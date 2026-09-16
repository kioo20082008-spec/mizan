package com.mizan.money.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [TransactionEntity::class, BudgetEntity::class],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
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
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN reimbursedAmount REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isReimbursement INTEGER NOT NULL DEFAULT 0")
            }
        }
        // Replaces the fixed-amount reimbursedAmount (added in v4, one build
        // ago) with a percentage — a fixed sum stops making sense the moment the
        // transaction's own amount is later corrected. The old column is left in
        // place unused rather than dropped: SQLite's DROP COLUMN support is new
        // enough that relying on it here isn't worth the risk to real user data.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN reimbursedPercent INTEGER NOT NULL DEFAULT 0")
            }
        }

        // No fallbackToDestructiveMigration: this holds a user's financial history,
        // so a future schema change must ship a real Migration rather than silently
        // wipe their data. exportSchema keeps the schema history to write one from.
        fun get(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                ctx.applicationContext, AppDatabase::class.java, "mizan.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
        }
    }
}
