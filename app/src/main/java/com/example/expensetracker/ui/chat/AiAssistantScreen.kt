package com.example.expensetracker.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.database.ExpenseDatabase
import com.example.expensetracker.data.entity.Expense
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.service.AiService
import com.example.expensetracker.data.service.ChatMessage
import com.example.expensetracker.viewmodel.ExpenseViewModel
import com.example.expensetracker.viewmodel.ExpenseViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
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

    val expenses by viewModel.allExpenses.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Chat History state
    var chatHistory by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    text = "Hello! I am your AI Financial Assistant. Ask me anything about your recorded expenses or budgets!",
                    isUser = false
                )
            )
        )
    }

    var textInput by remember { mutableStateOf("") }
    var isReplying by remember { mutableStateOf(false) }

    val suggestionChips = listOf(
        "How much did I spend in total?",
        "What is my highest category?",
        "Give me general budget advice."
    )

    val sendMessage = { messageText: String ->
        if (messageText.isNotBlank()) {
            val userMsg = ChatMessage(text = messageText, isUser = true)
            chatHistory = chatHistory + userMsg
            textInput = ""
            isReplying = true
            focusManager.clearFocus()

            // Scroll to bottom immediately after user sends message
            scope.launch {
                listState.animateScrollToItem(chatHistory.size - 1)
            }

            scope.launch {
                val replyText = AiService.generateChatResponse(context, chatHistory, expenses)
                chatHistory = chatHistory + ChatMessage(text = replyText, isUser = false)
                isReplying = false

                // Scroll to bottom after AI replies
                listState.animateScrollToItem(chatHistory.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Financial Assistant", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (chatHistory.size > 1) {
                        IconButton(onClick = {
                            chatHistory = listOf(
                                ChatMessage(
                                    text = "Hello! I am your AI Financial Assistant. Ask me anything about your recorded expenses or budgets!",
                                    isUser = false
                                )
                            )
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat", tint = Color.Gray)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFFBFBFD)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Chat history stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(chatHistory) { msg ->
                    ChatBubble(message = msg)
                }

                if (isReplying) {
                    item {
                        TypingIndicatorBubble()
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Suggestion chips (only show when not waiting for AI reply)
            if (!isReplying && chatHistory.size <= 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    suggestionChips.forEach { chip ->
                        SuggestionChipItem(
                            text = chip,
                            onClick = { sendMessage(chip) }
                        )
                    }
                }
            }

            // Input Row area
            Surface(
                tonalElevation = 8.dp,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Ask about your expenses...") },
                        maxLines = 3,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage(textInput) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0061A4),
                            unfocusedBorderColor = Color(0xFFE1E4E8),
                            focusedContainerColor = Color(0xFFF1F4F8),
                            unfocusedContainerColor = Color(0xFFF1F4F8)
                        )
                    )
                    IconButton(
                        onClick = { sendMessage(textInput) },
                        enabled = textInput.isNotBlank() && !isReplying,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (textInput.isNotBlank() && !isReplying) Color(0xFF0061A4) else Color(0xFFE1E4E8))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.isUser) Color(0xFFD1E4FF) else Color(0xFFF1F4F8)
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (!message.isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE3F2FD))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFF0061A4),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Surface(
                color = bubbleColor,
                shape = shape,
                modifier = Modifier.wrapContentSize()
            ) {
                Text(
                    text = message.text,
                    fontSize = 15.sp,
                    color = Color(0xFF1C1B1F),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFE3F2FD))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = Color(0xFF0061A4),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        Surface(
            color = Color(0xFFF1F4F8),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF0061A4),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "AI is thinking...",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun SuggestionChipItem(
    text: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E4E8)),
        modifier = Modifier
            .wrapContentWidth()
            .height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF0061A4)
            )
        }
    }
}
