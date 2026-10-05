package com.example.expensetracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.expensetracker.data.database.ExpenseDatabase
import com.example.expensetracker.data.entity.PendingExpense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyNotificationListenerService : NotificationListenerService() {

    private val paymentPackages = listOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",                       // PhonePe
        "net.one97.paytm",                       // Paytm
        "com.bhimupi.bhim",                      // BHIM UPI
        "in.org.npci.upiapp"                     // NPCI UPI
    )

    private val smsPackages = listOf(
        "com.google.android.apps.messaging",     // Google Messages
        "com.android.mms",                       // Android Standard MMS
        "com.samsung.android.messaging",         // Samsung Messages
        "com.hmdglobal.messages",                // Nokia Messages
        "com.huawei.message"                     // Huawei Messages
    )

    private val paymentKeywords = listOf(
        "payment", "paid", "debited", "debit", "transaction",
        "transferred", "sent", "upi", "successful", "payment successful"
    )

    private val amountRegex = Regex("(?:₹|rs\\.?|inr|amount:?\\s*(?:₹|rs\\.?|inr)?)\\s*([\\d,]+(?:\\.\\d{2})?)", RegexOption.IGNORE_CASE)
    
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence("android.title")?.toString() ?: ""
        val text = extras?.getCharSequence("android.text")?.toString() ?: ""
        val textLines = extras?.getCharSequenceArray("android.textLines")

        Log.d("NotificationListener", "Incoming notification: pkg=$packageName, title=$title, text=$text, hasLines=${textLines != null}")

        val textsToProcess = mutableListOf<String>()
        if (textLines != null && textLines.isNotEmpty()) {
            for (line in textLines) {
                val lineStr = line?.toString() ?: ""
                if (lineStr.isNotBlank()) {
                    textsToProcess.add(lineStr)
                }
            }
        }
        if (text.isNotBlank() && !textsToProcess.contains(text)) {
            textsToProcess.add(text)
        }

        for (textItem in textsToProcess) {
            val combinedText = "$title $textItem"
            if (isPaymentNotification(packageName, combinedText)) {
                val amount = extractAmount(textItem) ?: extractAmount(title)
                
                // Skip if no valid amount could be parsed
                if (amount == null) continue

                val merchant = extractMerchant(title, textItem)
                val paymentMethod = extractPaymentMethod(combinedText)

                // Log details of the detected payment
                Log.i("NotificationListener", """
                    PAYMENT DETECTED
                    Merchant: $merchant
                    Amount: ₹$amount
                    Payment Method: $paymentMethod
                    Source: Notification
                """.trimIndent())

                // Create and save a PendingExpense record asynchronously
                serviceScope.launch {
                    try {
                        val db = ExpenseDatabase.getDatabase(applicationContext)
                        val pendingDao = db.pendingExpenseDao()
                        
                        val lookupText = textItem.ifBlank { title }
                        // Duplicate protection: check if a pending expense with identical text already exists
                        val existing = pendingDao.getPendingExpenseByNotificationText(lookupText)
                        if (existing == null) {
                            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                            val isIncome = isIncomeNotification(combinedText)
                            val pendingExpense = PendingExpense(
                                amount = amount,
                                merchant = merchant,
                                paymentMethod = paymentMethod,
                                date = currentDate,
                                notificationText = lookupText,
                                packageName = packageName,
                                detectedAt = currentTime,
                                status = "PENDING",
                                isIncome = isIncome
                            )
                            pendingDao.insertPendingExpense(pendingExpense)
                            Log.i("NotificationListener", "Saved PendingExpense successfully for: $merchant ($amount)")
                        } else {
                            Log.i("NotificationListener", "Duplicate notification ignored: $lookupText")
                        }
                    } catch (e: Exception) {
                        Log.e("NotificationListener", "Error saving PendingExpense", e)
                    }
                }
            }
        }
    }

    private fun isPaymentNotification(packageName: String, text: String): Boolean {
        val isPaymentApp = paymentPackages.contains(packageName)
        val isSmsApp = smsPackages.contains(packageName) || 
                       packageName.lowercase().contains("message") || 
                       packageName.lowercase().contains("mms") ||
                       packageName.lowercase().contains("sms") ||
                       packageName == "com.truecaller"
        val lowerText = text.lowercase()
        
        // Strict bank/transaction text pattern overrides app package verification entirely
        val hasBankPattern = (lowerText.contains("debited") || lowerText.contains("credited") || lowerText.contains("spent") || lowerText.contains("withdrawn") || lowerText.contains("sent")) &&
                             (lowerText.contains("a/c") || lowerText.contains("acct") || lowerText.contains("account") || lowerText.contains("bal"))
        
        if (hasBankPattern) {
            return true
        }

        if (isPaymentApp) {
            return paymentKeywords.any { lowerText.contains(it) }
        }
        
        if (isSmsApp) {
            // For SMS apps, check if it's a bank/transaction alert rather than a personal chat
            val hasBankKeyword = lowerText.contains("a/c") || lowerText.contains("acct") || 
                                 lowerText.contains("account") || lowerText.contains("bank") || 
                                 lowerText.contains("card") || lowerText.contains("bal inr") || 
                                 lowerText.contains("balance")
            val hasTxnKeyword = lowerText.contains("debited") || lowerText.contains("credited") || 
                                lowerText.contains("spent") || lowerText.contains("withdrawn") ||
                                lowerText.contains("sent")
            return hasBankKeyword && hasTxnKeyword
        }
        
        val hasKeyword = paymentKeywords.any { lowerText.contains(it) }
        return hasKeyword
    }

    private fun extractAmount(text: String): Double? {
        val match = amountRegex.find(text)
        val amountStr = match?.groupValues?.get(1)?.replace(",", "")
        return amountStr?.toDoubleOrNull()
    }

    private fun extractMerchant(title: String, text: String): String {
        val patterns = listOf(
            Regex("^([a-zA-Z0-9\\s&'-]+?)\\s+(?:paid you|sent you|transferred to you|credited to you|has paid you)", RegexOption.IGNORE_CASE),
            Regex("(?:paid to|payment to|sent to|transferred to|debited at)\\s+([a-zA-Z0-9\\s&'-]+)", RegexOption.IGNORE_CASE),
            Regex("(?:paid|sent|transferred)\\s+.*?\\s+to\\s+([a-zA-Z0-9\\s&'-]+)", RegexOption.IGNORE_CASE),
            Regex("(?:to|at)\\s+([a-zA-Z0-9\\s&'-]+)", RegexOption.IGNORE_CASE)
        )

        // Try parsing from text first, then fallback to title
        val sources = listOf(text, title)
        for (source in sources) {
            for (pattern in patterns) {
                val match = pattern.find(source)
                if (match != null) {
                    var merchant = match.groupValues[1].trim()
                    
                    // Skip if captured merchant is actually a notification action verb (like "Tap to view")
                    val actionVerbs = listOf("view", "open", "check", "see", "tap", "pay", "authenticate", "approve", "details")
                    if (actionVerbs.contains(merchant.lowercase())) {
                        continue
                    }

                    // Clean up trailing helper words (like "using", "via", "on", "from")
                    val stopWords = listOf("using", "via", "on", "from", "through", "thru", "bal", "balance", "limit", "ref", "txn", "upi", "card")
                    for (stopWord in stopWords) {
                        val idx = merchant.lowercase().indexOf(" $stopWord ")
                        if (idx != -1) {
                            merchant = merchant.substring(0, idx).trim()
                        }
                        val idxEnd = merchant.lowercase().lastIndexOf(" $stopWord")
                        if (idxEnd != -1 && idxEnd == merchant.length - stopWord.length - 1) {
                            merchant = merchant.substring(0, idxEnd).trim()
                        }
                    }
                    
                    // Remove trailing punctuation
                    merchant = merchant.removeSuffix(".").removeSuffix(",").trim()
                    
                    if (merchant.isNotEmpty()) {
                        return merchant
                    }
                }
            }
        }

        // Fallback to title if it's a specific merchant name (not the app name or full sentence)
        val cleanTitle = title.trim()
        val appNames = listOf("google pay", "gpay", "phonepe", "paytm", "bhim", "upi", "expense tracker", "payment")
        if (cleanTitle.isNotEmpty() && !appNames.contains(cleanTitle.lowercase()) && cleanTitle.length < 30) {
            return cleanTitle
        }

        return "Unknown Merchant"
    }

    private fun extractPaymentMethod(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("upi") || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm") -> "UPI"
            lower.contains("credit card") || lower.contains("creditcard") || lower.contains("cc") -> "Credit Card"
            lower.contains("debit card") || lower.contains("debitcard") || lower.contains("dc") -> "Debit Card"
            lower.contains("cash") -> "Cash"
            else -> "Unknown"
        }
    }

    private fun isIncomeNotification(text: String): Boolean {
        val lowerText = text.lowercase()
        // Debit keyword overrides income keyword
        if (lowerText.contains("debited") || lowerText.contains("spent") || lowerText.contains("withdrawn")) {
            return false
        }
        return lowerText.contains("paid you") || 
               lowerText.contains("sent you") || 
               lowerText.contains("credited") || 
               lowerText.contains("received")
    }
}
