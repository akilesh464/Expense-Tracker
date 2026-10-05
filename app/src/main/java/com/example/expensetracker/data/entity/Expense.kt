package com.example.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Responsibility: Represents the data schema for an Expense record in the local Room database.
 */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Double,
    val merchant: String,
    val category: String,
    val paymentMethod: String,
    val notes: String,
    val date: String,
    val source: String, // Supported values: "Manual", "Receipt", "Notification"
    val isIncome: Boolean = false
)
