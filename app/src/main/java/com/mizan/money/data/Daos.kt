package com.mizan.money.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>
    // One-shot read for code that must see the post-write state immediately
    // (e.g. the budget-threshold check), where the WhileSubscribed StateFlow
    // may still be holding the previous emission.
    @Query("SELECT * FROM transactions")
    suspend fun getAllOnce(): List<TransactionEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: TransactionEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<TransactionEntity>): List<Long>
    @Update
    suspend fun update(tx: TransactionEntity)
    @Delete
    suspend fun delete(tx: TransactionEntity)
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
    @Query("SELECT * FROM transactions WHERE smsHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): TransactionEntity?
    // Only ever removes an SMS-sourced, never-edited row: a manual entry has no
    // corresponding hash to match, and an edited one is a deliberate user fix
    // that a rescan must not silently discard.
    @Query("DELETE FROM transactions WHERE smsHash = :hash AND isManual = 0 AND isEdited = 0")
    suspend fun deleteStaleByHash(hash: String)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets")
    suspend fun getAllOnce(): List<BudgetEntity>
    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey AND category = :category LIMIT 1")
    suspend fun find(monthKey: String, category: String): BudgetEntity?

    @Upsert
    suspend fun upsert(b: BudgetEntity)
    @Query("DELETE FROM budgets WHERE monthKey = :monthKey AND category = :category")
    suspend fun delete(monthKey: String, category: String)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GoalEntity>>
    @Insert
    suspend fun insert(g: GoalEntity): Long
    @Update
    suspend fun update(g: GoalEntity)
    @Delete
    suspend fun delete(g: GoalEntity)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DebtEntity>>
    @Insert
    suspend fun insert(d: DebtEntity): Long
    @Update
    suspend fun update(d: DebtEntity)
    @Delete
    suspend fun delete(d: DebtEntity)
}

@Dao
interface RecurringItemDao {
    @Query("SELECT * FROM recurring_items ORDER BY expectedDayOfMonth ASC")
    fun observeAll(): Flow<List<RecurringItemEntity>>
    // Used by the background worker, which isn't allowed to collect a Flow.
    @Query("SELECT * FROM recurring_items WHERE reminderEnabled = 1")
    suspend fun getEnabledOnce(): List<RecurringItemEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: RecurringItemEntity): Long
    @Update
    suspend fun update(r: RecurringItemEntity)
    @Delete
    suspend fun delete(r: RecurringItemEntity)
}
