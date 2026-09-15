package com.mizan.money.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val txDao: TransactionDao,
    private val budgetDao: BudgetDao
) {
    fun allTransactions(): Flow<List<TransactionEntity>> = txDao.observeAll()
    fun budgets(): Flow<List<BudgetEntity>> = budgetDao.observeAll()
    suspend fun add(tx: TransactionEntity): Long = txDao.insert(tx)
    suspend fun addAll(list: List<TransactionEntity>) = txDao.insertAll(list)
    suspend fun update(tx: TransactionEntity) = txDao.update(tx)
    suspend fun delete(tx: TransactionEntity) = txDao.delete(tx)
    suspend fun setBudget(monthKey: String, category: String, amount: Double) {
        if (amount <= 0) budgetDao.delete(monthKey, category)
        else budgetDao.upsert(BudgetEntity(monthKey, category, amount))
    }
}
