package com.example.expensetracker.data.service

import android.content.Context
import android.util.Log
import com.example.expensetracker.BuildConfig
import com.example.expensetracker.data.network.NetworkUtils
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.expensetracker.data.entity.Expense

data class ReceiptData(
    val merchant: String,
    val amount: Double?,
    val date: String?,
    val category: String,
    val notes: String
)

object AiService {
    private const val TAG = "AiService"

    // Lazy initialization of GenerativeModel with deterministic generation configuration
    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-3.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.0f
            }
        )
    }

    // Local rule-based fallback mappings for categorizations
    private val localCategoryMap = mapOf(
        "starbucks" to "Food",
        "mcdonald" to "Food",
        "kfc" to "Food",
        "swiggy" to "Food",
        "zomato" to "Food",
        "restaurant" to "Food",
        "cafe" to "Food",
        "hotel" to "Food",
        "canteen" to "Food",
        "bakery" to "Food",
        
        "amazon" to "Shopping",
        "flipkart" to "Shopping",
        "myntra" to "Shopping",
        "walmart" to "Shopping",
        "store" to "Shopping",
        "supermarket" to "Shopping",
        "mart" to "Shopping",
        "bazaar" to "Shopping",
        "clothing" to "Shopping",
        
        "uber" to "Transport",
        "ola" to "Transport",
        "rapido" to "Transport",
        "metro" to "Transport",
        "train" to "Transport",
        "railway" to "Transport",
        "bus" to "Transport",
        "fuel" to "Transport",
        "petrol" to "Transport",
        "toll" to "Transport",
        
        "netflix" to "Entertainment",
        "spotify" to "Entertainment",
        "hotstar" to "Entertainment",
        "prime video" to "Entertainment",
        "theater" to "Entertainment",
        "cinema" to "Entertainment",
        "gaming" to "Entertainment",
        
        "electricity" to "Bills",
        "water bill" to "Bills",
        "gas" to "Bills",
        "recharge" to "Bills",
        "bsnl" to "Bills",
        "jio" to "Bills",
        "airtel" to "Bills",
        "insurance" to "Bills"
    )

    /**
     * Semantic category suggester using Gemini with local rule-based fallback.
     */
    suspend fun suggestCategory(context: Context, merchant: String): String = withContext(Dispatchers.IO) {
        if (merchant.isBlank()) return@withContext "Other"

        val lowerMerchant = merchant.lowercase()
        
        // 1. First check local rule-based matching
        for ((key, cat) in localCategoryMap) {
            if (lowerMerchant.contains(key)) {
                Log.d(TAG, "Local rule matched category: $merchant -> $cat")
                return@withContext cat
            }
        }

        // 2. Check network status
        if (!NetworkUtils.isInternetAvailable(context)) {
            Log.d(TAG, "Offline. Defaulting to Other for merchant: $merchant")
            return@withContext "Other"
        }

        // 3. Fallback to Gemini API call
        try {
            val prompt = """
                Merchant Name: $merchant
                Assign one of these exact categories: Food, Shopping, Transport, Entertainment, Bills, Other.
                Return ONLY the category name.
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val result = response.text?.trim() ?: "Other"
            val validCategories = listOf("Food", "Shopping", "Transport", "Entertainment", "Bills", "Other")
            
            if (validCategories.contains(result)) {
                Log.d(TAG, "Gemini suggested category: $merchant -> $result")
                result
            } else {
                "Other"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini suggestion failed", e)
            "Other"
        }
    }

    /**
     * Semantic receipt parser using Gemini.
     */
    suspend fun parseReceiptText(context: Context, ocrText: String): ReceiptData? = withContext(Dispatchers.IO) {
        if (ocrText.isBlank()) return@withContext null

        if (!NetworkUtils.isInternetAvailable(context)) {
            Log.d(TAG, "Internet unavailable. Receipt Gemini parser skipped.")
            return@withContext null
        }

        try {
            val prompt = """
                You are an expert receipt parser. Analyze the following unstructured text extracted from a receipt and output a JSON object containing the structured data.
                
                EXTRACTION RULES:
                1. MERCHANT NAME: Look for the main store/brand title at the very top of the receipt (e.g. 'Aswins Home Special', 'Saravana Stores', 'ABC Super Market'). Do NOT extract generic labels like 'Name:', 'Bill No.', or customer details. Ensure the name is clean and trimmed.
                2. AMOUNT: Look strictly for the final total value at the bottom of the receipt associated with labels like 'Grand Total', 'Total Amount', 'Total', 'Amount Payable', 'Total Value', 'Net Amount', 'Net Pay', or 'Total Val'. Do NOT extract subtotals, tax details, item prices, bill numbers, phone numbers, or code identifiers.
                3. DATE: Find the transaction date and format it as 'YYYY-MM-DD'. If not found, return null.
                4. CATEGORY: Classify the expense into exactly one of: Food, Shopping, Transport, Entertainment, Bills, Other.
                5. NOTES: Create a short text summary of the items purchased or key GST details.
                
                JSON schema:
                {
                  "merchant": "Name of the store/merchant",
                  "amount": 125.50, // total amount paid as a numeric Double. Use null if not found.
                  "date": "YYYY-MM-DD", // transaction date. Use null if not found.
                  "category": "One of: Food, Shopping, Transport, Entertainment, Bills, Other",
                  "notes": "Any extra details, itemized list summary, or GST details"
                }
                
                Return ONLY the raw JSON block. Do not wrap in backticks or markdown formatting. No conversational text.
                
                Receipt text:
                $ocrText
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text?.trim() ?: return@withContext null
            Log.d(TAG, "Gemini raw response: $jsonText")

            val merchant = parseJsonValue(jsonText, "merchant") ?: "Unknown"
            val amount = parseJsonValue(jsonText, "amount")?.toDoubleOrNull()
            val date = parseJsonValue(jsonText, "date")
            val category = parseJsonValue(jsonText, "category") ?: "Other"
            val notes = parseJsonValue(jsonText, "notes") ?: "Imported from Receipt"

            ReceiptData(
                merchant = merchant,
                amount = amount,
                date = date,
                category = if (listOf("Food", "Shopping", "Transport", "Entertainment", "Bills", "Other").contains(category)) category else "Other",
                notes = notes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemini receipt parsing failed", e)
            null
        }
    }

    /**
     * Parses free-form text input into structured expense fields.
     */
    suspend fun parseNaturalLanguageExpense(context: Context, inputText: String): ReceiptData? = withContext(Dispatchers.IO) {
        if (inputText.isBlank()) return@withContext null

        if (!NetworkUtils.isInternetAvailable(context)) {
            Log.d(TAG, "Internet unavailable. Natural language parser skipped.")
            return@withContext null
        }

        try {
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val prompt = """
                You are an expert natural language processing engine. Your task is to analyze the user's input text describing an expense and parse it into a structured JSON object.
                
                CURRENT DATE CONTEXT: Today is $currentDate
                
                EXTRACTION RULES:
                1. MERCHANT: Extract the store, merchant, service, or brand name (e.g. 'Starbucks', 'Uber'). If not specified, set as "Unknown".
                2. AMOUNT: Extract the numerical total amount paid. If currency words are present (like 'rupees', 'rs', 'INR', '₹'), convert the amount to a Double value.
                3. DATE: Determine the date. Resolve relative terms (like 'today' -> '$currentDate', 'yesterday' -> one day before '$currentDate', or day names). Format as 'YYYY-MM-DD'. If not specified, default to '$currentDate'.
                4. CATEGORY: Map to exactly one of: Food, Shopping, Transport, Entertainment, Bills, Other.
                5. NOTES: Extract any extra descriptions or item names.
                
                JSON schema:
                {
                  "merchant": "Name of store",
                  "amount": 250.00, // numeric Double value
                  "date": "YYYY-MM-DD",
                  "category": "One of: Food, Shopping, Transport, Entertainment, Bills, Other",
                  "notes": "Any other context or items"
                }
                
                Return ONLY the raw JSON block. Do not wrap in backticks or markdown formatting. No conversational text.
                
                Input text:
                $inputText
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text?.trim() ?: return@withContext null
            Log.d(TAG, "Gemini NL raw response: $jsonText")

            val merchant = parseJsonValue(jsonText, "merchant") ?: "Unknown"
            val amount = parseJsonValue(jsonText, "amount")?.toDoubleOrNull()
            val date = parseJsonValue(jsonText, "date") ?: currentDate
            val category = parseJsonValue(jsonText, "category") ?: "Other"
            val notes = parseJsonValue(jsonText, "notes") ?: "Parsed via AI"

            ReceiptData(
                merchant = merchant,
                amount = amount,
                date = date,
                category = if (listOf("Food", "Shopping", "Transport", "Entertainment", "Bills", "Other").contains(category)) category else "Other",
                notes = notes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Natural language parsing failed", e)
            throw e
        }
    }

    private fun parseJsonValue(json: String, key: String): String? {
        val pattern = Regex("\"?$key\"?\\s*:\\s*(?:\"([^\"]*)\"|([\\d\\.]+)|null)", RegexOption.IGNORE_CASE)
        val match = pattern.find(json)
        return match?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } 
            ?: match?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
    }

    /**
     * Generates a conversational AI assistant response utilizing past chat history and user's expenses context database.
     */
    suspend fun generateChatResponse(
        context: Context,
        history: List<ChatMessage>,
        expenses: List<Expense>
    ): String = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isInternetAvailable(context)) {
            return@withContext "No internet connection. I need internet to access my AI engine."
        }

        try {
            // Format expenses list into structured context
            val expensesContext = if (expenses.isEmpty()) {
                "No expenses recorded yet."
            } else {
                expenses.joinToString("\n") { expense ->
                    "- ${expense.date}: ₹${expense.amount} at ${expense.merchant} (Category: ${expense.category}, Notes: ${expense.notes})"
                }
            }

            // Build prompts containing role, expenses context, and chat history
            val builder = StringBuilder()
            builder.append("""
                You are a helpful, smart, and friendly AI Financial Assistant. You have access to the user's recent expense transaction records listing below. 
                
                USER'S EXPENSES LIST:
                ${if (expenses.size > 200) expenses.takeLast(200).joinToString("\n") { "- ${it.date}: ₹${it.amount} at ${it.merchant} (${it.category})" } else expensesContext}
                
                ROLE & RULES:
                1. Answer questions about their expenses, budget, trends, categories, and merchants accurately based strictly on their data.
                2. Keep responses brief, conversational, and easy to read. Use bullet points or bold text if helpful.
                3. Do NOT make up transaction records. If they ask about a merchant or date not in the list, state that it isn't listed.
                4. If the question is off-topic (unrelated to finance or tracking), politely ask the user to focus on expense tracking.
                5. Do not give official financial investment advice.
                
                CHAT HISTORY:
            """.trimIndent())
            builder.append("\n\n")

            // Append history
            history.forEach { msg ->
                val speaker = if (msg.isUser) "User" else "Assistant"
                builder.append("$speaker: ${msg.text}\n")
            }

            // Append final prompt trigger
            builder.append("Assistant: ")

            val response = generativeModel.generateContent(builder.toString())
            response.text?.trim() ?: "I'm sorry, I couldn't generate a response."
        } catch (e: Exception) {
            Log.e(TAG, "Chat response generation failed", e)
            "Error: ${e.localizedMessage}"
        }
    }

    /**
     * Generates custom, personalized behavioral spending insights using programmatic statistics.
     */
    suspend fun generateInsights(
        context: Context,
        stats: SpendingStats
    ): String = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isInternetAvailable(context)) {
            return@withContext "No internet connection. AI insights requires an active internet connection."
        }

        try {
            val prompt = """
                You are a smart, warm, and friendly AI Financial advisor.
                Analyze the user's spending statistics below and provide exactly 3 bullet points of highly specific, personalized, and actionable financial insights/tips.
                
                USER'S SPENDING STATISTICS:
                - Total Spent: ₹${stats.totalSpent}
                - Top Category: ${stats.topCategory} (₹${stats.topCategoryAmount} spent)
                - Top Merchant: ${stats.topMerchant}
                - Most Expensive Day of Week: ${stats.highestDay}
                - Average Expense: ₹${stats.averageExpense}
                
                INSIGHT RULES:
                1. Make them sound warm and supportive, not robotic.
                2. Base your insights strictly on the statistics provided.
                3. Focus on highlighting saving opportunities (e.g., if Food is high, suggest meal prep tips; if Saturday is high, suggest weekend budget limits).
                4. Keep the output as a clean, plain-text bulleted list. Each point should be under 20 words. Do not use markdown titles.
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            response.text?.trim() ?: "No insights could be generated at this time."
        } catch (e: Exception) {
            Log.e(TAG, "Insight generation failed", e)
            "Error generating insights: ${e.localizedMessage}"
        }
    }
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class SpendingStats(
    val totalSpent: Double,
    val topCategory: String,
    val topCategoryAmount: Double,
    val topMerchant: String,
    val highestDay: String,
    val averageExpense: Double
)
