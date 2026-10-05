package com.example.expensetracker.ui.profile

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE) }
    val username = remember { sharedPrefs.getString("username", "User") ?: "User" }
    val email = remember { sharedPrefs.getString("email", "user@example.com") ?: "user@example.com" }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Profile Section
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD1E4FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.size(60.dp),
                            tint = Color(0xFF0061A4)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(text = email, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text(text = "+91 9876543210", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Member Since: July 2026", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Statistics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0061A4)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        StatItem(label = "Total Income", value = "₹40,000")
                        Spacer(modifier = Modifier.height(12.dp))
                        StatItem(label = "Total Expense", value = "₹18,400")
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))
                        StatItem(label = "Current Balance", value = "₹21,600", isMain = true)
                    }
                }
            }

            // Account Information Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoTile(icon = Icons.Default.AccountBalanceWallet, label = "Monthly Budget", value = "₹25,000")
                    InfoTile(icon = Icons.Default.Payment, label = "Preferred Payment", value = "UPI")
                    InfoTile(icon = Icons.Default.Notifications, label = "Notification Reader", value = "Enabled")
                }
            }

            // About Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AboutItem(label = "App Version", value = "1.0 (Prototype)")
                        AboutItem(label = "Project", value = "AI-Assisted Expense Tracker")
                        AboutItem(label = "Developer", value = "College Project Demo")
                    }
                }
            }

            // Developer Testing Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0061A4).copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🛠️ Developer Testing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0061A4)
                        )
                        Text(
                            text = "Simulate payment notifications on this device to test local NotificationListenerService capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val permission = android.Manifest.permission.POST_NOTIFICATIONS
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        (context as? android.app.Activity)?.requestPermissions(arrayOf(permission), 101)
                                        return@Button
                                    }
                                }
                                simulateNotification(context, 0)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Simulate Payment (Starbucks)", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val permission = android.Manifest.permission.POST_NOTIFICATIONS
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        (context as? android.app.Activity)?.requestPermissions(arrayOf(permission), 101)
                                        return@Button
                                    }
                                }
                                simulateNotification(context, 2)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Simulate Payment (Amazon)", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val permission = android.Manifest.permission.POST_NOTIFICATIONS
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        (context as? android.app.Activity)?.requestPermissions(arrayOf(permission), 101)
                                        return@Button
                                    }
                                }
                                simulateNotification(context, 3)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Simulate Payment (ABC Store)", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val permission = android.Manifest.permission.POST_NOTIFICATIONS
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        (context as? android.app.Activity)?.requestPermissions(arrayOf(permission), 101)
                                        return@Button
                                    }
                                }
                                simulateNotification(context, 4)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Simulate Payment (Rahul)", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val permission = android.Manifest.permission.POST_NOTIFICATIONS
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        (context as? android.app.Activity)?.requestPermissions(arrayOf(permission), 101)
                                        return@OutlinedButton
                                    }
                                }
                                simulateNotification(context, 1)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0061A4)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0061A4)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Simulate Non-Payment Notification", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Logout Button
            item {
                OutlinedButton(
                    onClick = onLogoutClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) {
                    Text("Logout", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, isMain: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            style = if (isMain) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = Color.White,
            style = if (isMain) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InfoTile(icon: ImageVector, label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF1F4F8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF0061A4), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AboutItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

fun simulateNotification(context: Context, type: Int) {
    val channelId = "payment_simulation_channel"
    val channelName = "Payment Simulation Channel"
    
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
    }
    
    val title: String
    val text: String
    val notificationId: Int
    
    when (type) {
        0 -> {
            title = "Payment Successful"
            text = "Paid ₹250 to Starbucks via UPI"
            notificationId = 1001
        }
        1 -> {
            title = "Order Update"
            text = "Your order has been shipped."
            notificationId = 1002
        }
        2 -> {
            title = "Payment Successful"
            text = "₹499 paid to Amazon using UPI."
            notificationId = 1003
        }
        3 -> {
            title = "Transaction Alert"
            text = "INR 850 debited from your account at ABC Store."
            notificationId = 1004
        }
        4 -> {
            title = "UPI Payment"
            text = "You sent ₹120 to Rahul."
            notificationId = 1005
        }
        else -> {
            title = "Notification"
            text = "Hello World"
            notificationId = 1000
        }
    }
    
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        
    notificationManager.notify(notificationId, builder.build())
}
