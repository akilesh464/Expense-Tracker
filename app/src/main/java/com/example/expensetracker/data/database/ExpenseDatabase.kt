package com.example.expensetracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.expensetracker.data.dao.ExpenseDao
import com.example.expensetracker.data.dao.PendingExpenseDao
import com.example.expensetracker.data.entity.Expense
import com.example.expensetracker.data.entity.PendingExpense

/**
 * Responsibility: Provides the main database entry point, maintaining a thread-safe singleton instance.
 */
@Database(entities = [Expense::class, PendingExpense::class], version = 3, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun pendingExpenseDao(): PendingExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pending_expenses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `amount` REAL, 
                        `merchant` TEXT NOT NULL, 
                        `paymentMethod` TEXT NOT NULL, 
                        `date` TEXT NOT NULL, 
                        `notificationText` TEXT NOT NULL, 
                        `packageName` TEXT NOT NULL, 
                        `detectedAt` TEXT NOT NULL, 
                        `status` TEXT NOT NULL
                    )
                """)
            }
        }

        fun getDatabase(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "expense_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
