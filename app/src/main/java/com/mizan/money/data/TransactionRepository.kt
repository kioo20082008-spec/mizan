package com.mizan.money.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val txDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val debtDao: DebtDao,
    private val recurringDao: RecurringItemDao
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
    suspend fun setBudget(
        monthKey: String,
        category: String,
        amount: Double,
        rolloverEnabled: Boolean? = null
    ) {
        if (amount <= 0) {
            budgetDao.delete(monthKey, category)
            return
        }
        val existing = budgetDao.find(monthKey, category)
        val effectiveRollover = rolloverEnabled ?: existing?.rolloverEnabled ?: false
        budgetDao.upsert(BudgetEntity(monthKey, category, amount, effectiveRollover))
    }

    // ---- Goals ----
    fun goals(): Flow<List<GoalEntity>> = goalDao.observeAll()
    suspend fun addGoal(g: GoalEntity): Long = goalDao.insert(g)
    suspend fun updateGoal(g: GoalEntity) = goalDao.update(g)
    suspend fun deleteGoal(g: GoalEntity) = goalDao.delete(g)

    // ---- Debts / installments ----
    fun debts(): Flow<List<DebtEntity>> = debtDao.observeAll()
    suspend fun addDebt(d: DebtEntity): Long = debtDao.insert(d)
    suspend fun updateDebt(d: DebtEntity) = debtDao.update(d)
    suspend fun deleteDebt(d: DebtEntity) = debtDao.delete(d)

    // ---- Bill reminders ----
    fun recurringItems(): Flow<List<RecurringItemEntity>> = recurringDao.observeAll()
    suspend fun recurringItemsDueSoon(): List<RecurringItemEntity> = recurringDao.getEnabledOnce()
    suspend fun upsertRecurringItem(r: RecurringItemEntity): Long = recurringDao.insert(r)
    suspend fun updateRecurringItem(r: RecurringItemEntity) = recurringDao.update(r)
    suspend fun deleteRecurringItem(r: RecurringItemEntity) = recurringDao.delete(r)
}
