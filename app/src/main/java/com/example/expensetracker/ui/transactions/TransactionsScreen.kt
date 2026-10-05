package com.example.expensetracker.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.database.ExpenseDatabase
import com.example.expensetracker.data.entity.Expense
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.ui.dashboard.AddExpenseBottomSheet
import com.example.expensetracker.ui.dashboard.BottomNavigationBar
import com.example.expensetracker.ui.dashboard.ViewExpenseBottomSheet
import com.example.expensetracker.ui.dashboard.getCategoryIcon
import com.example.expensetracker.viewmodel.ExpenseViewModel
import com.example.expensetracker.viewmodel.ExpenseViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAnalyticsClick: () -> Unit
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

    val expenses by viewModel.allExpenses.collectAsState()

    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var showViewExpenseSheet by remember { mutableStateOf(false) }
    var showAddExpenseSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val viewSheetState = rememberModalBottomSheetState()
    val editSheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("All Transactions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            BottomNavigationBar(
                onHomeClick = onHomeClick,
                onTransactionsClick = {},
                onProfileClick = onProfileClick,
                onAnalyticsClick = onAnalyticsClick,
                selectedTab = 1
            )
        },
        containerColor = Color(0xFFFBFBFD)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            if (expenses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    itemsIndexed(expenses) { index, expense ->
                        TransactionCard(
                            expense = expense,
                            onClick = {
                                selectedExpense = expense
                                showViewExpenseSheet = true
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            // Sheets and Dialogs
            if (showViewExpenseSheet && selectedExpense != null) {
                ViewExpenseBottomSheet(
                    expense = selectedExpense!!,
                    sheetState = viewSheetState,
                    onDismiss = { showViewExpenseSheet = false },
                    onEditClick = {
                        showViewExpenseSheet = false
                        showAddExpenseSheet = true
                    },
                    onDeleteClick = {
                        showViewExpenseSheet = false
                        showDeleteConfirmation = true
                    }
                )
            }

            if (showAddExpenseSheet && selectedExpense != null) {
                AddExpenseBottomSheet(
                    sheetState = editSheetState,
                    viewModel = viewModel,
                    expenseToEdit = selectedExpense,
                    onDismiss = { showAddExpenseSheet = false },
                    onSave = {
                        showAddExpenseSheet = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Expense updated successfully.")
                        }
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
        }
    }
}

@Composable
fun TransactionCard(
    expense: Expense,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F4F8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(expense.category),
                    contentDescription = null,
                    tint = Color(0xFF0061A4),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = expense.merchant,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )
                    if (expense.paymentMethod.isNotBlank()) {
                        Surface(
                            color = Color(0xFFF1F4F8),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = expense.paymentMethod,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = expense.date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = expense.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
            Text(
                text = "${if (expense.isIncome) "+" else "-"}₹${expense.amount.toInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (expense.isIncome) Color(0xFF4CAF50) else Color(0xFFD32F2F)
            )
        }
    }
}
