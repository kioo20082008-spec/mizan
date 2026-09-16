package com.mizan.money.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>
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
    // Correcting one transaction's category is a strong signal for every other
    // transaction from the same merchant, past and future — see
    // TransactionRepository.updateWithMerchantRule().
    @Query("UPDATE transactions SET category = :category WHERE merchant = :merchant")
    suspend fun updateCategoryByMerchant(merchant: String, category: String)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>
    @Upsert
    suspend fun upsert(b: BudgetEntity)
    @Query("DELETE FROM budgets WHERE monthKey = :monthKey AND category = :category")
    suspend fun delete(monthKey: String, category: String)
}
