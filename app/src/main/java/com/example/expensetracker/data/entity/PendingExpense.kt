package com.example.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_expenses")
data class PendingExpense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Double?,
    val merchant: String,
    val paymentMethod: String,
    val date: String,
    val notificationText: String,
    val packageName: String,
    val detectedAt: String,
    val status: String = "PENDING",
    val isIncome: Boolean = false
)
