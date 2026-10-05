package com.example.expensetracker.ui.receipt

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.example.expensetracker.data.database.ExpenseDatabase
import com.example.expensetracker.data.entity.Expense
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.ui.dashboard.AddExpenseBottomSheet
import com.example.expensetracker.viewmodel.ExpenseViewModel
import com.example.expensetracker.viewmodel.ExpenseViewModelFactory
import com.example.expensetracker.data.service.AiService
import com.example.expensetracker.data.service.ReceiptData
import com.example.expensetracker.data.network.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ScannerState {
    PREVIEW,
    PROCESSING,
    VALIDATION_FAILED,
    OCR_FAILED,
    SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPreviewScreen(
    imageUri: Uri?,
    bitmap: Bitmap?,
    onBackClick: () -> Unit,
    onChooseAnotherClick: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { ExpenseDatabase.getDatabase(context) }
    val repository = remember { ExpenseRepository(database.expenseDao(), database.pendingExpenseDao()) }
    val activity = context as? androidx.activity.ComponentActivity
    val viewModel: ExpenseViewModel = remember(activity) {
        if (activity != null) {
            androidx.lifecycle.ViewModelProvider(
                activity,
                ExpenseViewModelFactory(repository)
            )[ExpenseViewModel::class.java]
        } else {
            ExpenseViewModel(repository)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    var scannerState by remember { mutableStateOf(ScannerState.PREVIEW) }
    var showAddExpenseSheet by remember { mutableStateOf(false) }

    // Parsed states
    var parsedMerchant by remember { mutableStateOf("") }
    var parsedAmount by remember { mutableStateOf("") }
    var parsedDate by remember { mutableStateOf("") }
    var parsedCategory by remember { mutableStateOf("") }
    var parsedNotes by remember { mutableStateOf("") }
    var scanConfidence by remember { mutableStateOf(0) }

    // Checklist states
    var checklistReadingText by remember { mutableStateOf(false) }
    var checklistDetectingMerchant by remember { mutableStateOf(false) }
    var checklistDetectingAmount by remember { mutableStateOf(false) }
    var checklistPreparingExpense by remember { mutableStateOf(false) }

    // Run OCR processing
    val onProcessClick = {
        scannerState = ScannerState.PROCESSING
        
        val inputImage = when {
            bitmap != null -> InputImage.fromBitmap(bitmap, 0)
            imageUri != null -> {
                try {
                    InputImage.fromFilePath(context, imageUri)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
        
        if (inputImage == null) {
            scannerState = ScannerState.OCR_FAILED
        } else {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text
                    
                    // Extract sorted vertical lines from bounding box coordinates
                    val textLines = mutableListOf<Pair<String, Int>>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val top = line.boundingBox?.top ?: 0
                            textLines.add(line.text to top)
                        }
                    }
                    val sortedLines = textLines.sortedBy { it.second }.map { it.first }
                    
                    if (text.isBlank() || !validateReceipt(text)) {
                        scannerState = ScannerState.VALIDATION_FAILED
                    } else {
                        // Start parsing on IO dispatcher
                        val isConnected = NetworkUtils.isInternetAvailable(context)
                        val geminiJob = scope.launch(Dispatchers.IO) {
                            if (isConnected) {
                                val geminiData = AiService.parseReceiptText(context, text)
                                if (geminiData != null) {
                                    parsedMerchant = geminiData.merchant
                                    parsedAmount = geminiData.amount?.toString() ?: ""
                                    parsedDate = geminiData.date ?: extractDate(text) ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                    parsedCategory = geminiData.category
                                    parsedNotes = geminiData.notes
                                    scanConfidence = 95
                                    return@launch
                                }
                            }
                            
                            // Fallback to local parsing logic (runs if offline or if Gemini fails)
                            val merchant = extractMerchant(sortedLines)
                            val amount = extractAmount(text, sortedLines)
                            val date = extractDate(text) ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            
                            val lowerText = text.lowercase()
                            val hasKeywords = listOf("total", "grand total", "gst", "invoice", "bill", "receipt", "amount", "subtotal", "value").any { lowerText.contains(it) }
                            
                            val confidence = calculateConfidence(
                                hasMerchant = merchant.isNotEmpty(),
                                hasAmount = amount != null,
                                hasDate = extractDate(text) != null,
                                hasKeywords = hasKeywords
                            )
                            
                            parsedMerchant = merchant
                            parsedAmount = amount?.toString() ?: ""
                            parsedDate = date
                            parsedCategory = AiService.suggestCategory(context, merchant) // Use semantic/local lookup map
                            parsedNotes = "Imported from Receipt"
                            scanConfidence = confidence
                        }

                        // Run simulated checklist checks sequentially
                        scope.launch {
                            checklistReadingText = false
                            checklistDetectingMerchant = false
                            checklistDetectingAmount = false
                            checklistPreparingExpense = false
                            
                            delay(300)
                            checklistReadingText = true
                            
                            delay(450)
                            checklistDetectingMerchant = true
                            
                            delay(450)
                            checklistDetectingAmount = true
                            
                            delay(450)
                            checklistPreparingExpense = true
                            
                            // Wait for the Gemini/Local job to finish completely before opening the sheet!
                            geminiJob.join()
                            
                            delay(300)
                            scannerState = ScannerState.SUCCESS
                            showAddExpenseSheet = true
                        }
                    }
                }
                .addOnFailureListener {
                    scannerState = ScannerState.OCR_FAILED
                }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (scannerState == ScannerState.PREVIEW) "Receipt Preview" else "Receipt Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFFBFBFD)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (scannerState) {
                ScannerState.PREVIEW -> {
                    // Preview content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .padding(bottom = 160.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF1F4F8)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Captured Receipt",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else if (imageUri != null) {
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = "Selected Receipt",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Receipt Ready",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1C1B1F)
                                )
                                val isConnected = NetworkUtils.isInternetAvailable(LocalContext.current)
                                Text(
                                    text = if (isConnected) {
                                        "Ready to analyze receipt with Gemini Generative AI."
                                    } else {
                                        "Ready to analyze receipt with smart local OCR scanner."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Bottom Buttons
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFFBFBFD).copy(alpha = 0f),
                                        Color(0xFFFBFBFD).copy(alpha = 0.95f),
                                        Color(0xFFFBFBFD)
                                    ),
                                    startY = 0f,
                                    endY = 40f
                                )
                            )
                            .padding(horizontal = 24.dp)
                            .padding(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onProcessClick() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                        ) {
                            Text(
                                text = "Scan Receipt",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = onChooseAnotherClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF0061A4)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0061A4))
                        ) {
                            Text(
                                text = "Choose Another Receipt",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                ScannerState.PROCESSING -> {
                    // Processing checklist layout
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD1E4FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = Color(0xFF0061A4),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        val isConnected = NetworkUtils.isInternetAvailable(LocalContext.current)
                        Text(
                            text = if (isConnected) "Analyzing with Gemini AI..." else "Processing with Local OCR...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(color = Color(0xFF0061A4))
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Checklist items
                        Card(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ChecklistItem("Reading text", checklistReadingText)
                                ChecklistItem("Detecting merchant", checklistDetectingMerchant)
                                ChecklistItem("Detecting total amount", checklistDetectingAmount)
                                ChecklistItem("Preparing expense", checklistPreparingExpense)
                            }
                        }
                    }
                }

                ScannerState.VALIDATION_FAILED -> {
                    // Validation Failed screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFEBEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Not a Valid Receipt",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "This doesn't appear to be a valid receipt. Please scan a receipt or enter the expense manually.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(36.dp))
                        
                        Button(
                            onClick = onChooseAnotherClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                        ) {
                            Text("Scan Again", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedButton(
                            onClick = {
                                parsedMerchant = ""
                                parsedAmount = ""
                                parsedDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                scanConfidence = 0
                                showAddExpenseSheet = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF0061A4)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0061A4))
                        ) {
                            Text("Continue with Manual Entry", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ScannerState.OCR_FAILED -> {
                    // OCR error screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFEBEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "OCR Scan Failed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Unable to extract receipt information.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(36.dp))
                        
                        Button(
                            onClick = onChooseAnotherClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                        ) {
                            Text("Try Again", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedButton(
                            onClick = {
                                parsedMerchant = ""
                                parsedAmount = ""
                                parsedDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                scanConfidence = 0
                                showAddExpenseSheet = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF0061A4)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0061A4))
                        ) {
                            Text("Enter Manually", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ScannerState.SUCCESS -> {
                    // Pre-filled success screen placeholder while bottom sheet is visible
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF0061A4))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Opening Expense Details...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Prefilled Bottom Sheet
            if (showAddExpenseSheet) {
                AddExpenseBottomSheet(
                    sheetState = sheetState,
                    viewModel = viewModel,
                    prefilledAmount = parsedAmount,
                    prefilledMerchant = parsedMerchant,
                    prefilledCategory = parsedCategory,
                    prefilledDate = parsedDate,
                    prefilledNotes = parsedNotes.ifEmpty { "Imported from Receipt" },
                    prefilledSource = "Receipt",
                    scanConfidence = if (scanConfidence > 0) scanConfidence else null,
                    onDismiss = {
                        showAddExpenseSheet = false
                        onBackClick() // navigate back to dashboard
                    },
                    onSave = {
                        showAddExpenseSheet = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Expense added successfully.")
                            delay(1000)
                            onBackClick() // navigate back to dashboard
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ChecklistItem(label: String, checked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) Color(0xFF4CAF50) else Color.Gray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) Color(0xFF1C1B1F) else Color.Gray,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// Validation helper
fun validateReceipt(text: String): Boolean {
    val lowerText = text.lowercase()
    
    // Check for presence of numeric values (at least one sequence of digits)
    val hasNumeric = Regex("\\d+").containsMatchIn(text)
    if (!hasNumeric) return false
    
    // Currency indicators (very lenient)
    val hasCurrency = lowerText.contains("₹") || lowerText.contains("rs") || 
                      lowerText.contains("inr") || lowerText.contains("$") || 
                      lowerText.contains("usd") || lowerText.contains("eur")
    
    // Lenient keywords checking to survive OCR typos and complex layouts
    val lenientKeywords = listOf(
        "tot", "tota", "totl", "tota1", "totai", "totaI", 
        "gst", "tax", "vat", "cgst", "sgst", "cst", "g.s.t",
        "sub", "s.t", "subt",
        "bil", "bil1", "bi1l", "bl", "no.", "no:",
        "inv", "invo", "invc",
        "rec", "rcpt", "rept",
        "amt", "amnt", "amout", "amount", "price", "prc",
        "qty", "item"
    )
    
    val hasKeyword = lenientKeywords.any { lowerText.contains(it) }
    
    return hasCurrency || hasKeyword
}

// Parsing helpers
// Parsing helpers
fun extractMerchant(sortedLines: List<String>): String {
    val systemLabels = listOf(
        "invoice", "receipt", "welcome", "cashier", "date", "time", "order", 
        "customer", "merchant", "terminal", "station", "trans", "card", "payment", "name:"
    )
    
    for (line in sortedLines) {
        val lower = line.lowercase()
        if (line.none { it.isLetter() }) continue
        if (systemLabels.any { lower.startsWith(it) || lower == it }) continue
        if (lower.contains("gstin") || lower.contains("cell:") || lower.contains("tel:") || lower.contains("phone:") || lower.contains("bill no")) continue
        
        var cleaned = line
        val gstIdx = cleaned.indexOf("GST", ignoreCase = true)
        if (gstIdx > 0) {
            cleaned = cleaned.substring(0, gstIdx).trim()
        }
        
        val lettersOnly = cleaned.filter { it.isLetter() }
        if (lettersOnly.length >= 3) {
            return cleaned.removeSuffix("-").removeSuffix(":").trim()
        }
    }
    return ""
}

fun extractAmount(text: String, sortedLines: List<String>): Double? {
    // OCR-resilient patterns to handle common misreadings
    val patterns = listOf(
        listOf("grand total", "g. total", "grandtotal", "g-total", "grand", "grnd", "gtot"),
        listOf("total value", "total amount", "net total", "totalamt", "total amt", "net amount", "total:"),
        listOf("total", "tot", "tota1", "totai", "totaI", "totl"),
        listOf("amount payable", "payable", "net payable", "payamt", "due", "amount", "amt", "amnt")
    )
    
    val exclusions = listOf("gst", "tax", "sub", "cgst", "sgst", "discount", "change", "savings", "vat", "round", "cash", "card", "tendered")
    
    val priceRegex = Regex("(?:₹|rs\\.?|inr)?\\s*(\\d+(?:\\.\\d{2})?)", RegexOption.IGNORE_CASE)
    
    for (priorityLabels in patterns) {
        // Scan bottom to top of sorted vertical lines
        for (line in sortedLines.reversed()) {
            val lower = line.lowercase()
            
            // Check exclusions for generic total/amount labels to ignore taxes and sub-breakdowns
            val isGeneric = priorityLabels.contains("total") || priorityLabels.contains("amount") || 
                            priorityLabels.contains("tot") || priorityLabels.contains("amt")
            val hasExclusion = if (isGeneric) {
                exclusions.any { lower.contains(it) && !lower.contains("grand") && !lower.contains("net") && !lower.contains("value") }
            } else {
                false
            }
            
            if (!hasExclusion && priorityLabels.any { lower.contains(it) }) {
                val matches = priceRegex.findAll(line).toList()
                if (matches.isNotEmpty()) {
                    val amountStr = matches.last().groupValues[1]
                    val amountVal = amountStr.toDoubleOrNull()
                    if (amountVal != null && amountVal > 0 && amountVal < 50000) {
                        return amountVal
                    }
                }
                
                // If not on same line, look at the next line in vertical order
                val idx = sortedLines.indexOf(line)
                if (idx >= 0 && idx < sortedLines.size - 1) {
                    val nextLine = sortedLines[idx + 1]
                    val standsAlone = priceRegex.find(nextLine)
                    if (standsAlone != null) {
                        val amountVal = standsAlone.groupValues[1].toDoubleOrNull()
                        if (amountVal != null && amountVal > 0 && amountVal < 50000) {
                            return amountVal
                        }
                    }
                }
            }
        }
    }
    
    // Fallback: If no keyword matches, let's collect all prices under 5,000 (standard personal expenses) 
    // and return the maximum of the last 3 prices to avoid Toll Free numbers (> 10000)
    val allPrices = priceRegex.findAll(text)
        .mapNotNull { it.groupValues[1].toDoubleOrNull() }
        .filter { it > 0 && it < 5000 } // Filter out large toll free numbers
        .toList()
        
    if (allPrices.isNotEmpty()) {
        val lastPrices = allPrices.takeLast(3)
        return lastPrices.maxOrNull()
    }
    
    return null
}

fun extractDate(text: String): String? {
    val ymdRegex = Regex("(\\d{4})[-/](\\d{2})[-/](\\d{2})")
    val dmyRegex = Regex("(\\d{2})[-/](\\d{2})[-/](\\d{4})")
    val dmyShortRegex = Regex("(\\d{2})[-/](\\d{2})[-/](\\d{2})")
    
    var match = ymdRegex.find(text)
    if (match != null) return match.value
    
    match = dmyRegex.find(text)
    if (match != null) {
        val parts = match.groupValues
        return "${parts[3]}-${parts[2]}-${parts[1]}"
    }
    
    match = dmyShortRegex.find(text)
    if (match != null) {
        val parts = match.groupValues
        return "20${parts[3]}-${parts[2]}-${parts[1]}"
    }
    
    return null
}

fun calculateConfidence(hasMerchant: Boolean, hasAmount: Boolean, hasDate: Boolean, hasKeywords: Boolean): Int {
    var score = 0
    if (hasMerchant) score += 30
    if (hasAmount) score += 40
    if (hasDate) score += 20
    if (hasKeywords) score += 10
    return score
}
