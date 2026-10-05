package com.example.expensetracker.data.model

data class DetectedPayment(
    val amount: Double?,
    val merchant: String,
    val paymentMethod: String,
    val notificationText: String,
    val packageName: String,
    val detectedAt: Long = System.currentTimeMillis()
)
