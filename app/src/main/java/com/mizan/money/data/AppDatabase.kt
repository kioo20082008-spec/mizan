package com.mizan.money.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        TransactionEntity::class, BudgetEntity::class,
        GoalEntity::class, DebtEntity::class, RecurringItemEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun debtDao(): DebtDao
    abstract fun recurringItemDao(): RecurringItemDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isEdited INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN excludeFromDailyAvg INTEGER NOT NULL DEFAULT 0")
            }
        }
        // Goals/debts/recurring-items (savings goals, installment tracking, bill
        // reminders) — three brand-new, independent tables. None of them touch the
        // transactions/budgets tables, so this can't corrupt existing history.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS goals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        targetAmount REAL NOT NULL,
                        currentAmount REAL NOT NULL DEFAULT 0,
                        targetDate INTEGER,
                        createdAt INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS debts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        totalAmount REAL NOT NULL,
                        remainingAmount REAL NOT NULL,
                        installmentAmount REAL NOT NULL DEFAULT 0,
                        nextDueDate INTEGER,
                        lender TEXT,
                        createdAt INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS recurring_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        merchant TEXT NOT NULL,
                        expectedAmount REAL NOT NULL,
                        expectedDayOfMonth INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        reminderEnabled INTEGER NOT NULL DEFAULT 1,
                        lastNotifiedMonthKey TEXT,
                        createdAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        // No fallbackToDestructiveMigration: this holds a user's financial history,
        // so a future schema change must ship a real Migration rather than silently
        // wipe their data. exportSchema keeps the schema history to write one from.
        fun get(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                ctx.applicationContext, AppDatabase::class.java, "mizan.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
        }
    }
}
