package com.example.expensetracker.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.expensetracker.data.entity.PendingExpense
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPendingExpense(pendingExpense: PendingExpense): Long

    @Query("SELECT * FROM pending_expenses WHERE status = 'PENDING' ORDER BY id DESC")
    fun getPendingExpenses(): Flow<List<PendingExpense>>

    @Query("SELECT * FROM pending_expenses WHERE id = :id LIMIT 1")
    fun getPendingExpenseById(id: Int): PendingExpense?

    @Delete
    fun deletePendingExpense(pendingExpense: PendingExpense): Int

    @Query("SELECT * FROM pending_expenses WHERE notificationText = :notificationText LIMIT 1")
    fun getPendingExpenseByNotificationText(notificationText: String): PendingExpense?
}
