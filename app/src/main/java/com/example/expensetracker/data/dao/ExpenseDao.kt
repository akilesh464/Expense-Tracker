package com.example.expensetracker.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.expensetracker.data.entity.Expense
import kotlinx.coroutines.flow.Flow

/**
 * Responsibility: Provides abstract methods to access and modify expense records in the SQLite database.
 */
@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertExpense(expense: Expense): Long

    @Update
    fun updateExpense(expense: Expense): Int

    @Delete
    fun deleteExpense(expense: Expense): Int

    @Query("SELECT * FROM expenses ORDER BY id DESC")
    fun getAllExpenses(): Flow<List<Expense>>
}
