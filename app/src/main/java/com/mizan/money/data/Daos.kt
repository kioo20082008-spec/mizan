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
