package com.example.expensetracker.ui.pending

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.database.ExpenseDatabase
import com.example.expensetracker.data.entity.Expense
import com.example.expensetracker.data.entity.PendingExpense
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.viewmodel.ExpenseViewModel
import com.example.expensetracker.viewmodel.ExpenseViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingExpensesScreen(
    onBackClick: () -> Unit
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

    val pendingExpenses by viewModel.pendingExpenses.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var expenseToReview by remember { mutableStateOf<PendingExpense?>(null) }
    var expenseToIgnore by remember { mutableStateOf<PendingExpense?>(null) }
    
    // Bottom Sheet State for Review
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showReviewSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending Expenses", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color(0xFFFBFBFD)
    ) { innerPadding ->
        if (pendingExpenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F4F8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "No pending expenses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = "Clean as a whistle! New notifications will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(pendingExpenses, key = { it.id }) { item ->
                    PendingExpenseCard(
                        pendingExpense = item,
                        onReviewClick = {
                            expenseToReview = item
                            showReviewSheet = true
                        },
                        onIgnoreClick = {
                            expenseToIgnore = item
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }

        // Ignore Confirmation Dialog
        if (expenseToIgnore != null) {
            AlertDialog(
                onDismissRequest = { expenseToIgnore = null },
                title = { Text("Ignore this detected expense?", fontWeight = FontWeight.Bold) },
                text = { Text("This pending expense will be permanently deleted and cannot be confirmed.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = expenseToIgnore
                            expenseToIgnore = null
                            if (target != null) {
                                viewModel.deletePendingExpense(target)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Pending expense ignored.")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("Ignore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { expenseToIgnore = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Review Bottom Sheet
        if (showReviewSheet && expenseToReview != null) {
            ModalBottomSheet(
                onDismissRequest = { showReviewSheet = false },
                sheetState = sheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                ReviewBottomSheetContent(
                    pendingExpense = expenseToReview!!,
                    onDismiss = { showReviewSheet = false },
                    onConfirm = { editedMerchant, editedAmount, editedCategory, editedMethod, editedDate, editedNotes ->
                        showReviewSheet = false
                        // 1. Create normal Expense
                        val newExpense = Expense(
                            amount = editedAmount,
                            merchant = editedMerchant,
                            category = editedCategory,
                            paymentMethod = editedMethod,
                            notes = editedNotes,
                            date = editedDate,
                            source = "Notification",
                            isIncome = expenseToReview!!.isIncome
                        )
                        // 2. Save it
                        viewModel.insertExpense(newExpense)
                        // 3. Delete corresponding PendingExpense
                        viewModel.deletePendingExpense(expenseToReview!!)
                        // 4. Show success snackbar
                        scope.launch {
                            snackbarHostState.showSnackbar("Expense added successfully.")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PendingExpenseCard(
    pendingExpense: PendingExpense,
    onReviewClick: () -> Unit,
    onIgnoreClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = pendingExpense.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )
                    Text(
                        text = "Source: Notification",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Text(
                    text = pendingExpense.amount?.let { "₹${it.toInt()}" } ?: "Unknown Amount",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0061A4)
                )
            }

            HorizontalDivider(color = Color(0xFFF1F4F8))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Text(text = pendingExpense.paymentMethod, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Text(text = pendingExpense.date, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onIgnoreClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ignore")
                }
                Button(
                    onClick = onReviewClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Review", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewBottomSheetContent(
    pendingExpense: PendingExpense,
    onDismiss: () -> Unit,
    onConfirm: (merchant: String, amount: Double, category: String, method: String, date: String, notes: String) -> Unit
) {
    var merchant by remember { mutableStateOf(pendingExpense.merchant) }
    var amountText by remember { mutableStateOf(pendingExpense.amount?.toString() ?: "") }
    var category by remember { mutableStateOf("Other") }
    var paymentMethod by remember { mutableStateOf(pendingExpense.paymentMethod) }
    var date by remember { mutableStateOf(pendingExpense.date) }
    var notes by remember { mutableStateOf("Detected from payment notification") }

    var expandedCategory by remember { mutableStateOf(false) }
    val categories = listOf("Food", "Shopping", "Transport", "Entertainment", "Bills", "Other")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Review Expense",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F)
        )

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Merchant") },
            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount") },
            leadingIcon = { Icon(Icons.Default.Money, contentDescription = null) },
            prefix = { Text("₹") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        // Category dropdown
        Box(modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { expandedCategory = !expandedCategory }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    categories.forEach { selection ->
                        DropdownMenuItem(
                            text = { Text(selection) },
                            onClick = {
                                category = selection
                                expandedCategory = false
                            }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = paymentMethod,
            onValueChange = { paymentMethod = it },
            label = { Text("Payment Method") },
            leadingIcon = { Icon(Icons.Default.Payment, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("Date (YYYY-MM-DD)") },
            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            leadingIcon = { Icon(Icons.Default.Note, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
            ) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    val amountVal = amountText.toDoubleOrNull() ?: 0.0
                    onConfirm(merchant, amountVal, category, paymentMethod, date, notes)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
            ) {
                Text("Confirm Expense", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
