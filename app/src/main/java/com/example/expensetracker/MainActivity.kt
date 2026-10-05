package com.example.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.expensetracker.ui.dashboard.DashboardScreen
import com.example.expensetracker.ui.login.LoginScreen
import com.example.expensetracker.ui.profile.ProfileScreen
import com.example.expensetracker.ui.receipt.ReceiptPreviewScreen
import com.example.expensetracker.ui.splash.SplashScreen
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import com.example.expensetracker.ui.transactions.TransactionsScreen
import com.example.expensetracker.ui.pending.PendingExpensesScreen
import com.example.expensetracker.ui.chat.AiAssistantScreen
import com.example.expensetracker.ui.analytics.AnalyticsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseTrackerTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var previewUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(onTimeout = {
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }
        composable("login") {
            LoginScreen(onLoginSuccess = {
                navController.navigate("dashboard") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("dashboard") {
            DashboardScreen(
                onProfileClick = {
                    navController.navigate("profile")
                },
                onTransactionsClick = {
                    navController.navigate("transactions")
                },
                onScanReceipt = { uri, bitmap ->
                    previewUri = uri
                    previewBitmap = bitmap
                    navController.navigate("receipt_preview")
                },
                onReviewPendingClick = {
                    navController.navigate("pending_expenses")
                },
                onAskAiClick = {
                    navController.navigate("ai_chat")
                },
                onAnalyticsClick = {
                    navController.navigate("analytics")
                }
            )
        }
        composable("transactions") {
            TransactionsScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = false }
                    }
                },
                onProfileClick = {
                    navController.navigate("profile") {
                        popUpTo("dashboard")
                    }
                },
                onAnalyticsClick = {
                    navController.navigate("analytics")
                }
            )
        }
        composable("profile") {
            ProfileScreen(
                onBackClick = { navController.popBackStack() },
                onLogoutClick = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }
        composable("receipt_preview") {
            ReceiptPreviewScreen(
                imageUri = previewUri,
                bitmap = previewBitmap,
                onBackClick = { navController.popBackStack() },
                onChooseAnotherClick = {
                    navController.popBackStack()
                }
            )
        }
        composable("pending_expenses") {
            PendingExpensesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("ai_chat") {
            AiAssistantScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("analytics") {
            AnalyticsScreen(
                onHomeClick = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onTransactionsClick = {
                    navController.navigate("transactions") {
                        popUpTo("dashboard")
                    }
                },
                onProfileClick = {
                    navController.navigate("profile") {
                        popUpTo("dashboard")
                    }
                }
            )
        }
    }
}
