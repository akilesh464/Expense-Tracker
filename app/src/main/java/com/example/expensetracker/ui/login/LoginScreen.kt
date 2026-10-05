package com.example.expensetracker.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEBF3FC), // Soft top blue-white tint
                        Color(0xFFF6F8FB), // Soft modern neutral bottom
                        Color(0xFFFFFFFF)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Header
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = "App Logo",
                    modifier = Modifier.size(44.dp),
                    tint = Color(0xFF0061A4)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Expense Tracker",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1C1B1F),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Track Smart. Spend Better.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Premium Form Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp), clip = false),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Sign In",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )

                    // Name Field
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (nameError) nameError = false
                        },
                        label = { Text("Name") },
                        placeholder = { Text("e.g. John Doe") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (nameError) MaterialTheme.colorScheme.error else Color.Gray
                            )
                        },
                        isError = nameError,
                        supportingText = {
                            if (nameError) {
                                Text("Name is required", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0061A4),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            errorBorderColor = MaterialTheme.colorScheme.error
                        )
                    )

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            if (emailError) emailError = false
                        },
                        label = { Text("Email") },
                        placeholder = { Text("e.g. name@domain.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = if (emailError) MaterialTheme.colorScheme.error else Color.Gray
                            )
                        },
                        isError = emailError,
                        supportingText = {
                            if (emailError) {
                                Text("Enter a valid email", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0061A4),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            errorBorderColor = MaterialTheme.colorScheme.error
                        )
                    )

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (passwordError) passwordError = false
                        },
                        label = { Text("Password") },
                        placeholder = { Text("Min 6 characters") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (passwordError) MaterialTheme.colorScheme.error else Color.Gray
                            )
                        },
                        trailingIcon = {
                            val icon = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description = if (isPasswordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(imageVector = icon, contentDescription = description)
                            }
                        },
                        isError = passwordError,
                        supportingText = {
                            if (passwordError) {
                                Text("Password must be at least 6 characters", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0061A4),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            errorBorderColor = MaterialTheme.colorScheme.error
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Login Button
                    Button(
                        onClick = {
                            var hasError = false
                            if (name.isBlank()) {
                                nameError = true
                                hasError = true
                            }
                            val emailTrim = email.trim()
                            if (emailTrim.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrim).matches()) {
                                emailError = true
                                hasError = true
                            }
                            if (password.length < 6) {
                                passwordError = true
                                hasError = true
                            }

                            if (!hasError) {
                                val sharedPrefs = context.getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE)
                                sharedPrefs.edit().putString("username", name.trim()).apply()
                                onLoginSuccess()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(28.dp), clip = false),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0061A4),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Login",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
