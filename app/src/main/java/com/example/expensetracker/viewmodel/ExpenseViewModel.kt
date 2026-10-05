package com.example.expensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.entity.Expense
import com.example.expensetracker.data.entity.PendingExpense
import com.example.expensetracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Responsibility: Manages UI-related data and coordinates operations with the ExpenseRepository using Coroutines.
 */
class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    // Expose all expenses as a read-only StateFlow to the UI
    val allExpenses: StateFlow<List<Expense>> = repository.getAllExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Expose pending expenses as a read-only StateFlow to the UI
    val pendingExpenses: StateFlow<List<PendingExpense>> = repository.getPendingExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun insertExpense(expense: Expense) {
        viewModelScope.launch {
            repository.insertExpense(expense)
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            repository.updateExpense(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // Pending Expense operations
    fun insertPendingExpense(pendingExpense: PendingExpense) {
        viewModelScope.launch {
            repository.insertPendingExpense(pendingExpense)
        }
    }

    fun deletePendingExpense(pendingExpense: PendingExpense) {
        viewModelScope.launch {
            repository.deletePendingExpense(pendingExpense)
        }
    }
}

/**
 * Responsibility: Factory to create instances of ExpenseViewModel with its repository parameter.
 */
class ExpenseViewModelFactory(private val repository: ExpenseRepository) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
