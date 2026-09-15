package com.mizan.money.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val txDao: TransactionDao,
    private val budgetDao: BudgetDao
) {
    fun allTransactions(): Flow<List<TransactionEntity>> = txDao.observeAll()
    fun budgets(): Flow<List<BudgetEntity>> = budgetDao.observeAll()
    suspend fun add(tx: TransactionEntity): Long = txDao.insert(tx)

    // A plain insert-and-ignore-conflicts would mean a parser/category bug fix
    // never reaches SMS already imported before the fix shipped — the stale,
    // wrongly-parsed (or wrongly duplicated) row just sits there forever, which
    // is exactly the "duplicate transaction" symptom a rescan is supposed to fix.
    // `scannedHashes` covers every SMS the scan looked at, transactional or not,
    // so a message that used to (wrongly) parse as a transaction and no longer
    // does — e.g. an OTP the parser now correctly rejects — gets its stale row
    // removed too, not just left orphaned because it's absent from `list`.
    suspend fun reconcile(list: List<TransactionEntity>, scannedHashes: Set<String>) {
        for (tx in list) {
            val existing = txDao.findByHash(tx.smsHash)
            if (existing == null) txDao.insert(tx)
            else if (!existing.isEdited) txDao.update(tx.copy(id = existing.id))
        }
        val parsedHashes = list.mapTo(HashSet()) { it.smsHash }
        for (hash in scannedHashes) {
            if (hash !in parsedHashes) txDao.deleteStaleByHash(hash)
        }
    }

    suspend fun update(tx: TransactionEntity) = txDao.update(tx.copy(isEdited = true))
    suspend fun delete(tx: TransactionEntity) = txDao.delete(tx)
    suspend fun setBudget(monthKey: String, category: String, amount: Double) {
        if (amount <= 0) budgetDao.delete(monthKey, category)
        else budgetDao.upsert(BudgetEntity(monthKey, category, amount))
    }
}
