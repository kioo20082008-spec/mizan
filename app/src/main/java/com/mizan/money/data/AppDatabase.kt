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
        GoalEntity::class, DebtEntity::class, RecurringItemEntity::class,
        GoalContributionEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun goalContributionDao(): GoalContributionDao
    abstract fun debtDao(): DebtDao
    abstract fun recurringItemDao(): RecurringItemDao
    abstract fun backupDao(): BackupDao
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE budgets ADD COLUMN rolloverEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Marks an incoming transfer as reimbursing a shared expense so it is
        // excluded from income and subtracted from spending totals.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isReimbursement INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Installment-plan terms on a debt: how many months total and how many
        // the user has already paid (both default 0 = no plan).
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN termMonths INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE debts ADD COLUMN paidMonths INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Budget-planning concepts: which recurring items are fixed monthly
        // commitments (auto-set-aside), an explicit monthly savings target per
        // goal, and a dated contribution trail so monthly savings can be shown
        // separately from spending. The contribution table is new and
        // independent; the two ALTERs only add defaulted columns.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recurring_items ADD COLUMN isFixed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE goals ADD COLUMN monthlyAmount REAL NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS goal_contributions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        timestamp INTEGER NOT NULL
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { INSTANCE = it }
        }
    }
}
