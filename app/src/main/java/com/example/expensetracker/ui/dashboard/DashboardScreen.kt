package com.example.expensetracker.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.expensetracker.viewmodel.ExpenseViewModel
import com.example.expensetracker.viewmodel.ExpenseViewModelFactory
import com.example.expensetracker.data.database.ExpenseDatabase
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.entity.Expense
import com.example.expensetracker.data.service.AiService
import com.example.expensetracker.data.network.NetworkUtils
import android.net.Uri
import java.io.File
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onProfileClick: () -> Unit,
    onTransactionsClick: () -> Unit,
    onScanReceipt: (android.net.Uri?, android.graphics.Bitmap?) -> Unit,
    onReviewPendingClick: () -> Unit,
    onAskAiClick: () -> Unit,
    onAnalyticsClick: () -> Unit
) {
    val context = LocalContext.current
    var isNotificationReaderEnabled by remember { mutableStateOf(isNotificationListenerServiceEnabled(context)) }
    var showPermissionSnackbar by remember { mutableStateOf(false) }
    var showAddExpenseSheet by remember { mutableStateOf(false) }
    var showScanOptionsSheet by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }

    // Transaction Management states
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var showViewExpenseSheet by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
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

    // Observe expenses live from ViewModel
    val expenses by viewModel.allExpenses.collectAsState()
    val pendingExpenses by viewModel.pendingExpenses.collectAsState()

    // SharedPreferences for Budget and User Info
    val sharedPrefs = remember { context.getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE) }
    var monthlyBudget by remember { mutableStateOf(sharedPrefs.getFloat("monthly_budget", 25000f).toDouble()) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    val username = remember { sharedPrefs.getString("username", "User") ?: "User" }

    // Calculations
    val totalIncome = 40000.0 + expenses.filter { it.isIncome }.sumOf { it.amount }
    val totalExpense = expenses.filter { !it.isIncome }.sumOf { it.amount }
    val remainingBalance = totalIncome - totalExpense

    val sheetState = rememberModalBottomSheetState()
    val viewSheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasPermission = isNotificationListenerServiceEnabled(context)
                if (showPermissionSnackbar) {
                    if (hasPermission) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Notification Reader Enabled")
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Notification access is required.")
                        }
                    }
                    showPermissionSnackbar = false
                }
                isNotificationReaderEnabled = hasPermission
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && tempPhotoUri != null) {
                onScanReceipt(tempPhotoUri, null)
            }
        }
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onScanReceipt(uri, null)
            }
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = { 
            BottomNavigationBar(
                onHomeClick = {},
                onTransactionsClick = onTransactionsClick,
                onProfileClick = onProfileClick,
                onAnalyticsClick = onAnalyticsClick,
                selectedTab = 0
            ) 
        },
        containerColor = Color(0xFFFBFBFD) // Premium soft background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Top App Bar
                item { 
                    TopSection(
                        username = username,
                        pendingCount = pendingExpenses.size,
                        onNotificationClick = onReviewPendingClick
                    ) 
                }

                // Balance Card (Live Calculations)
                item { BalanceCard(remainingBalance = remainingBalance, totalExpense = totalExpense, totalIncome = totalIncome) }

                // Budget Card (Configurable Calculations)
                item { 
                    BudgetCard(
                        totalExpense = totalExpense,
                        monthlyBudget = monthlyBudget,
                        onEditBudgetClick = { showBudgetDialog = true }
                    ) 
                }

                // Quick Actions
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionItem(
                                icon = Icons.Default.Add,
                                label = "Add Expense",
                                modifier = Modifier.weight(1f),
                                iconBgColor = Color(0xFFE3F2FD),
                                iconTintColor = Color(0xFF0D47A1),
                                onClick = { 
                                    expenseToEdit = null
                                    showAddExpenseSheet = true 
                                }
                            )
                            ActionItem(
                                icon = Icons.Default.PhotoCamera,
                                label = "Scan Receipt",
                                modifier = Modifier.weight(1f),
                                iconBgColor = Color(0xFFF3E5F5),
                                iconTintColor = Color(0xFF4A148C),
                                onClick = {
                                    showScanOptionsSheet = true
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionItem(
                                icon = Icons.Default.SmartToy,
                                label = "Ask AI Assistant",
                                modifier = Modifier.weight(1f),
                                iconBgColor = Color(0xFFE8F5E9),
                                iconTintColor = Color(0xFF1B5E20),
                                onClick = onAskAiClick
                            )
                            ActionItem(
                                icon = Icons.Default.Notifications,
                                label = "Notification Reader",
                                modifier = Modifier.weight(1f),
                                iconBgColor = Color(0xFFFFF3E0),
                                iconTintColor = Color(0xFFE65100),
                                hasSwitch = true,
                                switchState = isNotificationReaderEnabled,
                                onSwitchChange = { checked ->
                                    if (checked) {
                                        showNotificationPermissionDialog = true
                                    } else {
                                        val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                    }
                }

                if (pendingExpenses.isNotEmpty()) {
                    item {
                        PendingExpensesBanner(
                            count = pendingExpenses.size,
                            onReviewClick = onReviewPendingClick
                        )
                    }
                }

                // Recent Transactions (Live Database entries)
                item { 
                    RecentTransactionsSection(
                        expenses = expenses,
                        onTransactionClick = { expense ->
                            selectedExpense = expense
                            showViewExpenseSheet = true
                        },
                        onSeeAllClick = onTransactionsClick
                    ) 
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Sheets, Dialogs and Confirmations
            if (showAddExpenseSheet) {
                AddExpenseBottomSheet(
                    sheetState = sheetState,
                    viewModel = viewModel,
                    expenseToEdit = expenseToEdit,
                    onDismiss = { showAddExpenseSheet = false },
                    onSave = {
                        showAddExpenseSheet = false
                        scope.launch {
                            val message = if (expenseToEdit != null) "Expense updated successfully." else "Expense added successfully."
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                )
            }

            if (showViewExpenseSheet && selectedExpense != null) {
                ViewExpenseBottomSheet(
                    expense = selectedExpense!!,
                    sheetState = viewSheetState,
                    onDismiss = { showViewExpenseSheet = false },
                    onEditClick = {
                        showViewExpenseSheet = false
                        expenseToEdit = selectedExpense
                        showAddExpenseSheet = true
                    },
                    onDeleteClick = {
                        showViewExpenseSheet = false
                        showDeleteConfirmation = true
                    }
                )
            }

            if (showDeleteConfirmation && selectedExpense != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = { Text("Delete Expense?", fontWeight = FontWeight.Bold) },
                    text = { Text("This action cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val expenseToDelete = selectedExpense
                                showDeleteConfirmation = false
                                if (expenseToDelete != null) {
                                    viewModel.deleteExpense(expenseToDelete)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Expense deleted successfully.")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(20.dp)
                )
            }

            if (showScanOptionsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showScanOptionsSheet = false },
                    sheetState = rememberModalBottomSheetState(),
                    dragHandle = { BottomSheetDefaults.DragHandle() },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Scan Receipt",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F)
                        )

                        // Option 1: Take Photo
                        Surface(
                            onClick = {
                                showScanOptionsSheet = false
                                try {
                                    val file = File.createTempFile("receipt_capture_", ".jpg", context.cacheDir)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "com.example.expensetracker.provider",
                                        file
                                    )
                                    tempPhotoUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF1F4F8)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD1E4FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Take Photo",
                                        tint = Color(0xFF0061A4)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Take Photo",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1C1B1F)
                                    )
                                    Text(
                                        text = "Capture a photo of your receipt",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        // Option 2: Choose from Gallery
                        Surface(
                            onClick = {
                                showScanOptionsSheet = false
                                galleryLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF1F4F8)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD1E4FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Choose from Gallery",
                                        tint = Color(0xFF0061A4)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Choose from Gallery",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1C1B1F)
                                    )
                                    Text(
                                        text = "Select a receipt from your device storage",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showNotificationPermissionDialog) {
                NotificationPermissionDialog(
                    onDismiss = {
                        showNotificationPermissionDialog = false
                    },
                    onConfirm = {
                        showNotificationPermissionDialog = false
                        isNotificationReaderEnabled = true
                        showPermissionSnackbar = true
                        // Open Notification Access Settings
                        val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        context.startActivity(intent)
                    }
                )
            }

            if (showBudgetDialog) {
                var budgetInput by remember { mutableStateOf(monthlyBudget.toInt().toString()) }
                AlertDialog(
                    onDismissRequest = { showBudgetDialog = false },
                    title = { Text("Set Monthly Budget", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = budgetInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() }) {
                                    budgetInput = input
                                }
                            },
                            label = { Text("Budget Amount") },
                            prefix = { Text("₹", fontWeight = FontWeight.Bold) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val newBudget = budgetInput.toDoubleOrNull() ?: 25000.0
                                sharedPrefs.edit().putFloat("monthly_budget", newBudget.toFloat()).apply()
                                monthlyBudget = newBudget
                                showBudgetDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBudgetDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}

@Composable
fun NotificationPermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF0061A4),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Enable Notification Reader?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Automatically detect payment notifications from supported payment apps to track expenses in real-time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                
                HorizontalDivider(color = Color(0xFFF1F4F8))
                
                Text(
                    text = "How it works:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BulletItem("Detects payment notifications.")
                    BulletItem("Lets you confirm expenses before saving.")
                    BulletItem("Your notification content is never stored without your confirmation.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Allow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", color = Color.Gray, fontWeight = FontWeight.Medium)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun BulletItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF0061A4),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseBottomSheet(
    sheetState: SheetState,
    viewModel: ExpenseViewModel,
    expenseToEdit: Expense? = null,
    prefilledAmount: String = "",
    prefilledMerchant: String = "",
    prefilledCategory: String = "",
    prefilledDate: String = "",
    prefilledNotes: String = "",
    prefilledSource: String = "Manual",
    scanConfidence: Int? = null,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        var amount by remember { mutableStateOf(expenseToEdit?.amount?.toString() ?: prefilledAmount) }
        var merchant by remember { mutableStateOf(expenseToEdit?.merchant ?: prefilledMerchant) }
        var category by remember { mutableStateOf(expenseToEdit?.category ?: prefilledCategory) }
        var date by remember { mutableStateOf(expenseToEdit?.date ?: prefilledDate.ifBlank { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }) }
        var isIncome by remember { mutableStateOf(expenseToEdit?.isIncome ?: false) }
        
        var aiInputText by remember { mutableStateOf("") }
        var isAiParsing by remember { mutableStateOf(false) }

        // Dynamic AI Category Auto-Suggestion
        LaunchedEffect(merchant) {
            if (merchant.length >= 3) {
                delay(500) // debounce key inputs for 500ms
                val suggested = AiService.suggestCategory(context, merchant)
                if (suggested.isNotEmpty() && suggested != "Other") {
                    category = suggested
                }
            }
        }
        var paymentMethod by remember { mutableStateOf(expenseToEdit?.paymentMethod ?: "") }
        var notes by remember { mutableStateOf(expenseToEdit?.notes ?: prefilledNotes) }

        var amountError by remember { mutableStateOf<String?>(null) }
        var merchantError by remember { mutableStateOf<String?>(null) }

        var categoryExpanded by remember { mutableStateOf(false) }
        var paymentMethodExpanded by remember { mutableStateOf(false) }

        val categories = listOf("Food", "Transport", "Shopping", "Bills", "Entertainment", "Health", "Education", "Other")
        val paymentMethods = listOf("Cash", "UPI", "Credit Card", "Debit Card")

        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            delay(100)
            focusRequester.requestFocus()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Column {
                Text(
                    text = if (expenseToEdit != null) "✏️ Edit Transaction" else "➕ Add New Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (expenseToEdit != null) "Update transaction information." else "Record your transaction manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            // Transaction Type (Income / Expense Segmented Control)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF1F4F8)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (!isIncome) Color(0xFFD32F2F) else Color.Transparent)
                        .clickable { isIncome = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Expense",
                        color = if (!isIncome) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isIncome) Color(0xFF4CAF50) else Color.Transparent)
                        .clickable { isIncome = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Income",
                        color = if (isIncome) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }



            // Confidence warning banner
            if (scanConfidence != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F4F8))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Receipt Scan Results",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1C1B1F)
                            )
                            val confidenceLabel = when {
                                scanConfidence >= 90 -> "High ($scanConfidence%)"
                                scanConfidence >= 70 -> "Medium ($scanConfidence%)"
                                else -> "Low ($scanConfidence%)"
                            }
                            val confidenceColor = when {
                                scanConfidence >= 90 -> Color(0xFF4CAF50)
                                scanConfidence >= 70 -> Color(0xFFFFA000)
                                else -> Color(0xFFD32F2F)
                            }
                            Text(
                                text = confidenceLabel,
                                fontWeight = FontWeight.ExtraBold,
                                color = confidenceColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (scanConfidence < 90) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Some details could not be extracted accurately. Please verify before saving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }

            // Amount field (Primary)
            OutlinedTextField(
                value = amount,
                onValueChange = { 
                    amount = it
                    if (it.isNotBlank()) amountError = null
                },
                isError = amountError != null,
                supportingText = amountError?.let { { Text(text = it, color = MaterialTheme.colorScheme.error) } },
                label = { Text("Amount", fontWeight = FontWeight.SemiBold) },
                placeholder = { Text("0.00") },
                prefix = { Text("₹", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.LightGray
                )
            )

            // Merchant field
            OutlinedTextField(
                value = merchant,
                onValueChange = { 
                    merchant = it
                    if (it.isNotBlank()) merchantError = null
                },
                isError = merchantError != null,
                supportingText = merchantError?.let { { Text(text = it, color = MaterialTheme.colorScheme.error) } },
                label = { Text("Merchant") },
                placeholder = { Text("Example: Starbucks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.LightGray
                )
            )

            // Category Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    placeholder = { Text("Select category") },
                    trailingIcon = {
                        IconButton(onClick = { categoryExpanded = !categoryExpanded }) {
                            Icon(
                                imageVector = if (categoryExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.LightGray
                    )
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { categoryExpanded = true }
                )
                DropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    categories.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                category = selectionOption
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            // Payment Method Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = paymentMethod,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Method") },
                    placeholder = { Text("Select payment method") },
                    trailingIcon = {
                        IconButton(onClick = { paymentMethodExpanded = !paymentMethodExpanded }) {
                            Icon(
                                imageVector = if (paymentMethodExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.LightGray
                    )
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { paymentMethodExpanded = true }
                )
                DropdownMenu(
                    expanded = paymentMethodExpanded,
                    onDismissRequest = { paymentMethodExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    paymentMethods.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                paymentMethod = selectionOption
                                paymentMethodExpanded = false
                            }
                        )
                    }
                }
            }

            // Notes field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                placeholder = { Text("Add a note...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.LightGray
                )
            )

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Button(
                    onClick = {
                        var hasError = false
                        val parsedAmount = amount.toDoubleOrNull()
                        if (amount.isBlank()) {
                            amountError = "Amount cannot be empty"
                            hasError = true
                        } else if (parsedAmount == null) {
                            amountError = "Invalid amount format"
                            hasError = true
                        }
                        if (merchant.isBlank()) {
                            merchantError = "Merchant cannot be empty"
                            hasError = true
                        }
                        
                        if (!hasError) {
                            if (expenseToEdit == null) {
                                val expense = Expense(
                                    amount = parsedAmount ?: 0.0,
                                    merchant = merchant,
                                    category = category.ifBlank { "Other" },
                                    paymentMethod = paymentMethod.ifBlank { "Cash" },
                                    notes = notes,
                                    date = date,
                                    source = prefilledSource,
                                    isIncome = isIncome
                                )
                                viewModel.insertExpense(expense)
                            } else {
                                val updatedExpense = expenseToEdit.copy(
                                    amount = parsedAmount ?: 0.0,
                                    merchant = merchant,
                                    category = category.ifBlank { "Other" },
                                    paymentMethod = paymentMethod.ifBlank { "Cash" },
                                    notes = notes,
                                    isIncome = isIncome
                                    // Do NOT edit Date or Source!
                                )
                                viewModel.updateExpense(updatedExpense)
                            }
                            onSave()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Save Expense",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
fun TopSection(
    username: String,
    pendingCount: Int,
    onNotificationClick: () -> Unit
) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) {
            in 0..11 -> "Good Morning 👋"
            in 12..16 -> "Good Afternoon 👋"
            in 17..21 -> "Good Evening 👋"
            else -> "Good Night 👋"
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = username,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1C1B1F)
            )
        }
        BadgedBox(
            badge = {
                if (pendingCount > 0) {
                    Badge(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    ) {
                        Text(pendingCount.toString())
                    }
                }
            }
        ) {
            IconButton(
                onClick = onNotificationClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE5E7EB), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = Color(0xFF0061A4)
                )
            }
        }
    }
}

// Currency formatting utility
fun formatCurrency(amount: Double): String {
    return "₹" + String.format(java.util.Locale.US, "%,d", amount.toInt())
}

@Composable
fun BalanceCard(remainingBalance: Double, totalExpense: Double, totalIncome: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF0061A4), Color(0xFF003C66))
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Total Balance",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatCurrency(remainingBalance),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 36.sp
                )
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BalanceInfoItem(label = "Income", amount = formatCurrency(totalIncome), isIncome = true)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                    BalanceInfoItem(label = "Expense", amount = formatCurrency(totalExpense), isIncome = false)
                }
            }
        }
    }
}

@Composable
fun BalanceInfoItem(label: String, amount: String, isIncome: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = amount,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BudgetCard(
    totalExpense: Double,
    monthlyBudget: Double,
    onEditBudgetClick: () -> Unit
) {
    val budgetProgress = if (monthlyBudget > 0) (totalExpense.toFloat() / monthlyBudget.toFloat()) else 0f
    val budgetUsedPercent = if (monthlyBudget > 0) ((totalExpense / monthlyBudget) * 100).toInt() else 0
    val remainingBudget = monthlyBudget - totalExpense
    val isExceeded = totalExpense > monthlyBudget

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Monthly Budget",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Budget",
                        tint = Color(0xFF0061A4),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onEditBudgetClick() }
                    )
                }
                Text(
                    text = formatCurrency(monthlyBudget),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0061A4)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { budgetProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = if (isExceeded) Color(0xFFD32F2F) else Color(0xFF0061A4),
                trackColor = Color(0xFFE5E7EB),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${formatCurrency(totalExpense)} spent",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (remainingBudget >= 0) "${formatCurrency(remainingBudget)} remaining" else "${formatCurrency(kotlin.math.abs(remainingBudget))} over budget",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (remainingBudget >= 0) Color.Gray else Color(0xFFD32F2F)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isExceeded) "Budget Exceeded" else "On Track",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isExceeded) Color(0xFFD32F2F) else Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$budgetUsedPercent% used",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0061A4),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    hasSwitch: Boolean = false,
    switchState: Boolean = false,
    onSwitchChange: (Boolean) -> Unit = {},
    iconBgColor: Color = Color(0xFFD1E4FF),
    iconTintColor: Color = Color(0xFF0061A4)
) {
    Card(
        modifier = modifier.height(125.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = if (!hasSwitch) onClick else ({})
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F),
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (hasSwitch) {
                Spacer(modifier = Modifier.height(4.dp))
                Switch(
                    checked = switchState,
                    onCheckedChange = onSwitchChange,
                    modifier = Modifier.scale(0.7f)
                )
            }
        }
    }
}

// Category to Icon mapping helper
fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase()) {
        "food" -> Icons.Default.Restaurant
        "transport" -> Icons.Default.DirectionsCar
        "shopping" -> Icons.Default.ShoppingCart
        "bills" -> Icons.Default.Receipt
        "entertainment" -> Icons.Default.Movie
        "health" -> Icons.Default.LocalHospital
        "education" -> Icons.Default.School
        else -> Icons.Default.Category
    }
}

@Composable
fun RecentTransactionsSection(
    expenses: List<Expense>,
    onTransactionClick: (Expense) -> Unit,
    onSeeAllClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F)
            )
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF0061A4),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onSeeAllClick() }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                if (expenses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No expenses added yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val latestExpenses = expenses.take(5)
                    latestExpenses.forEachIndexed { index, expense ->
                        TransactionItem(
                            name = expense.merchant,
                            date = "${expense.date} • ${expense.category}",
                            amount = "${if (expense.isIncome) "+" else "-"}₹${expense.amount.toInt()}",
                            icon = getCategoryIcon(expense.category),
                            modifier = Modifier.clickable { onTransactionClick(expense) },
                            isIncome = expense.isIncome
                        )
                        if (index < latestExpenses.size - 1) {
                            HorizontalDivider(color = Color(0xFFF1F4F8), modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    name: String,
    date: String,
    amount: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isIncome: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1F4F8)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF0061A4),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) Color(0xFF4CAF50) else Color(0xFFD32F2F)
        )
    }
}

@Composable
fun BottomNavigationBar(
    onHomeClick: () -> Unit = {},
    onTransactionsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    selectedTab: Int = 0
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, Color(0xFFF1F4F8), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontWeight = FontWeight.Medium) },
            selected = selectedTab == 0,
            onClick = onHomeClick,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF0061A4),
                selectedTextColor = Color(0xFF0061A4),
                indicatorColor = Color(0xFFD1E4FF),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.History, contentDescription = "Transactions") },
            label = { Text("Transactions", fontWeight = FontWeight.Medium) },
            selected = selectedTab == 1,
            onClick = onTransactionsClick,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF0061A4),
                selectedTextColor = Color(0xFF0061A4),
                indicatorColor = Color(0xFFD1E4FF),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.BarChart, contentDescription = "Analytics") },
            label = { Text("Analytics", fontWeight = FontWeight.Medium) },
            selected = selectedTab == 2,
            onClick = onAnalyticsClick,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF0061A4),
                selectedTextColor = Color(0xFF0061A4),
                indicatorColor = Color(0xFFD1E4FF),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile", fontWeight = FontWeight.Medium) },
            selected = selectedTab == 3,
            onClick = onProfileClick,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF0061A4),
                selectedTextColor = Color(0xFF0061A4),
                indicatorColor = Color(0xFFD1E4FF),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewExpenseBottomSheet(
    expense: Expense,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Expense Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F)
            )

            HorizontalDivider(color = Color(0xFFF1F4F8))

            DetailItem(label = "Merchant", value = expense.merchant)
            DetailItem(label = "Amount", value = "₹${expense.amount.toInt()}")
            DetailItem(label = "Category", value = expense.category)
            DetailItem(label = "Payment Method", value = expense.paymentMethod)
            DetailItem(label = "Date", value = expense.date)
            DetailItem(label = "Source", value = expense.source)
            if (expense.notes.isNotBlank()) {
                DetailItem(label = "Notes", value = expense.notes)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                    border = BorderStroke(1.dp, Color(0xFFD32F2F))
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onEditClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                ) {
                    Text("Edit", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF1C1B1F),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PendingExpensesBanner(
    count: Int,
    onReviewClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReviewClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4EC)),
        border = BorderStroke(1.dp, Color(0xFFFFD8B3))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFE5CC)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Pending Notification",
                        tint = Color(0xFFE65C00),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "🔔 Pending Expenses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65C00)
                    )
                    Text(
                        text = if (count == 1) "1 expense detected" else "$count expenses detected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF803300)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onReviewClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65C00)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Review", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun isNotificationListenerServiceEnabled(context: android.content.Context): Boolean {
    val pkgName = context.packageName
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = android.content.ComponentName.unflattenFromString(name)
            if (cn != null && cn.packageName == pkgName) {
                return true
            }
        }
    }
    return false
}
