package com.example.expensetracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.example.expensetracker.data.service.AiService
import com.example.expensetracker.data.service.SpendingStats
import com.example.expensetracker.ui.dashboard.BottomNavigationBar
import com.example.expensetracker.viewmodel.ExpenseViewModel
import com.example.expensetracker.viewmodel.ExpenseViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onHomeClick: () -> Unit,
    onTransactionsClick: () -> Unit,
    onProfileClick: () -> Unit
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
    val scope = rememberCoroutineScope()

    // Calculate Spending Statistics (Filter out Income)
    val expensesOnly = expenses.filter { !it.isIncome }
    val totalSpent = expensesOnly.sumOf { it.amount }

    val categoryTotals = expensesOnly.groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }

    val topCategoryEntry = categoryTotals.maxByOrNull { it.value }
    val topCategory = topCategoryEntry?.key ?: "N/A"
    val topCategoryAmount = topCategoryEntry?.value ?: 0.0

    val topMerchant = expensesOnly.groupBy { it.merchant }
        .mapValues { entry -> entry.value.size }
        .maxByOrNull { it.value }?.key ?: "N/A"

    val dayOfWeekTotals = expensesOnly.groupBy { expense ->
        try {
            val dateObj = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(expense.date)
            if (dateObj != null) {
                java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(dateObj)
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }.mapValues { entry -> entry.value.sumOf { it.amount } }
    val highestDay = dayOfWeekTotals.maxByOrNull { it.value }?.key ?: "N/A"

    val averageExpense = if (expensesOnly.isNotEmpty()) totalSpent / expensesOnly.size else 0.0

    val stats = remember(expenses) {
        SpendingStats(
            totalSpent = totalSpent,
            topCategory = topCategory,
            topCategoryAmount = topCategoryAmount,
            topMerchant = topMerchant,
            highestDay = highestDay,
            averageExpense = averageExpense
        )
    }

    // AI Insight State
    var aiInsights by remember { mutableStateOf("") }
    var isLoadingInsights by remember { mutableStateOf(false) }

    LaunchedEffect(expenses) {
        if (expenses.isNotEmpty()) {
            isLoadingInsights = true
            aiInsights = AiService.generateInsights(context, stats)
            isLoadingInsights = false
        } else {
            aiInsights = "Add some expense records to see personalized AI savings tips!"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Analytics", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            BottomNavigationBar(
                onHomeClick = onHomeClick,
                onTransactionsClick = onTransactionsClick,
                onProfileClick = onProfileClick,
                selectedTab = 2
            )
        },
        containerColor = Color(0xFFFBFBFD)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // AI Insights Panel Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F7FD)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFD1E4FF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD1E4FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF0061A4),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "AI Spending Insights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0061A4),
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isLoadingInsights) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF0061A4),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "AI is analyzing your spending patterns...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            Text(
                                text = aiInsights,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1C1B1F),
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // Quick Stats Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatBox(
                        title = "Avg Transaction",
                        value = "₹${averageExpense.toInt()}",
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "Top Merchant",
                        value = topMerchant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Category Breakdown Title
            item {
                Text(
                    text = "Category Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )
            }

            // List of Categories
            if (expenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transactions found to break down.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(categoryTotals.entries.sortedByDescending { it.value }) { entry ->
                    val progress = if (totalSpent > 0.0) entry.value / totalSpent else 0.0
                    CategoryBreakdownRow(
                        category = entry.key,
                        amount = entry.value,
                        percentage = (progress * 100).toInt(),
                        progress = progress.toFloat()
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F)
            )
        }
    }
}

@Composable
fun CategoryBreakdownRow(
    category: String,
    amount: Double,
    percentage: Int,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(getCategoryBgColor(category)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(category),
                    contentDescription = null,
                    tint = getCategoryColor(category),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )
                    Text(
                        text = "₹${amount.toInt()}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = getCategoryColor(category),
                        trackColor = Color(0xFFF1F4F8)
                    )
                    Text(
                        text = "$percentage%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

private fun getCategoryColor(category: String): Color {
    return when (category.lowercase()) {
        "food" -> Color(0xFF4CAF50)          // Green
        "transport" -> Color(0xFF00BCD4)     // Cyan
        "shopping" -> Color(0xFFFF9800)      // Orange
        "bills" -> Color(0xFFE91E63)          // Pink/Crimson
        "entertainment" -> Color(0xFF9C27B0)  // Purple
        "health" -> Color(0xFFF44336)         // Red
        "education" -> Color(0xFF3F51B5)      // Indigo
        else -> Color(0xFF0061A4)             // Default Theme Blue
    }
}

private fun getCategoryBgColor(category: String): Color {
    return when (category.lowercase()) {
        "food" -> Color(0xFFE8F5E9)
        "transport" -> Color(0xFFE0F7FA)
        "shopping" -> Color(0xFFFFF3E0)
        "bills" -> Color(0xFFFCE4EC)
        "entertainment" -> Color(0xFFF3E5F5)
        "health" -> Color(0xFFFFEBEE)
        "education" -> Color(0xFFE8EAF6)
        else -> Color(0xFFE3F2FD)
    }
}

private fun getCategoryIcon(category: String): ImageVector {
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
