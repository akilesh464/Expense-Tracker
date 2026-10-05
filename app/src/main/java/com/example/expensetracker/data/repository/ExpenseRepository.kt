package com.example.expensetracker.data.repository

import com.example.expensetracker.data.dao.ExpenseDao
import com.example.expensetracker.data.dao.PendingExpenseDao
import com.example.expensetracker.data.entity.Expense
import com.example.expensetracker.data.entity.PendingExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Responsibility: Abstracts access to the Room database, providing a clean API for the ViewModel layer.
 */
class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val pendingExpenseDao: PendingExpenseDao
) {

    fun getAllExpenses(): Flow<List<Expense>> {
        return expenseDao.getAllExpenses()
    }

    suspend fun insertExpense(expense: Expense): Long = withContext(Dispatchers.IO) {
        expenseDao.insertExpense(expense)
    }

    suspend fun updateExpense(expense: Expense): Int = withContext(Dispatchers.IO) {
        expenseDao.updateExpense(expense)
    }

    suspend fun deleteExpense(expense: Expense): Int = withContext(Dispatchers.IO) {
        expenseDao.deleteExpense(expense)
    }

    // Pending Expense operations
    fun getPendingExpenses(): Flow<List<PendingExpense>> {
        return pendingExpenseDao.getPendingExpenses()
    }

    suspend fun insertPendingExpense(pendingExpense: PendingExpense): Long = withContext(Dispatchers.IO) {
        pendingExpenseDao.insertPendingExpense(pendingExpense)
    }

    suspend fun deletePendingExpense(pendingExpense: PendingExpense): Int = withContext(Dispatchers.IO) {
        pendingExpenseDao.deletePendingExpense(pendingExpense)
    }
}
