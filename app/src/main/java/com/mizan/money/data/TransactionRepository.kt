package com.mizan.money.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val txDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val goalContributionDao: GoalContributionDao,
    private val debtDao: DebtDao,
    private val recurringDao: RecurringItemDao
) {
    fun allTransactions(): Flow<List<TransactionEntity>> = txDao.observeAll()
    suspend fun allTransactionsOnce(): List<TransactionEntity> = txDao.getAllOnce()
    fun budgets(): Flow<List<BudgetEntity>> = budgetDao.observeAll()
    suspend fun budgetsOnce(): List<BudgetEntity> = budgetDao.getAllOnce()
    // The same SMS gets a different smsHash when captured live (SMSC timestamp)
    // than when read back from the inbox (device-received date), so hash alone
    // can't stop it being stored twice. Same body + timestamps within a few
    // minutes is the same message.
    private fun sameSms(a: TransactionEntity, b: TransactionEntity) =
        !a.isManual && !b.isManual && a.rawSms.isNotEmpty() && b.rawSms.isNotEmpty() &&
            (a.rawSms == b.rawSms || a.rawSms.startsWith(b.rawSms) || b.rawSms.startsWith(a.rawSms)) &&
            kotlin.math.abs(a.amount - b.amount) < 0.005 &&
            kotlin.math.abs(a.timestamp - b.timestamp) <= 10 * 60_000L

    suspend fun add(tx: TransactionEntity): Long {
        if (!tx.isManual && tx.rawSms.isNotEmpty() && txDao.getAllOnce().any { sameSms(it, tx) }) return -1L
        return txDao.insert(tx)
    }

    // One-off cleanup of SMS rows stored twice (hash mismatch between live and
    // inbox capture, or a live copy built from only the first SMS part). Keeps a
    // user-edited copy if there is one, else the one with the fuller SMS text.
    suspend fun removeDuplicateSmsRows() {
        val sms = txDao.getAllOnce().filter { !it.isManual && it.rawSms.isNotEmpty() }
            .sortedBy { it.timestamp }
        val gone = HashSet<Long>()
        for (i in sms.indices) {
            val x = sms[i]
            if (x.id in gone) continue
            for (j in i + 1 until sms.size) {
                val y = sms[j]
                if (y.timestamp - x.timestamp > 10 * 60_000L) break
                if (y.id in gone || x.id in gone || !sameSms(x, y)) continue
                val keepX = when {
                    x.isEdited != y.isEdited -> x.isEdited
                    x.rawSms.length != y.rawSms.length -> x.rawSms.length > y.rawSms.length
                    else -> x.id <= y.id
                }
                val drop = if (keepX) y else x
                txDao.delete(drop); gone.add(drop.id)
            }
        }
    }

    // A plain insert-and-ignore-conflicts would mean a parser/category bug fix
    // never reaches SMS already imported before the fix shipped — the stale,
    // wrongly-parsed (or wrongly duplicated) row just sits there forever, which
    // is exactly the "duplicate transaction" symptom a rescan is supposed to fix.
    // `scannedHashes` covers every SMS the scan looked at, transactional or not,
    // so a message that used to (wrongly) parse as a transaction and no longer
    // does — e.g. an OTP the parser now correctly rejects — gets its stale row
    // removed too, not just left orphaned because it's absent from `list`.
    suspend fun reconcile(list: List<TransactionEntity>, scannedHashes: Set<String>) {
        removeDuplicateSmsRows()
        val stored = txDao.getAllOnce()
        for (tx in list) {
            val existing = txDao.findByHash(tx.smsHash) ?: stored.firstOrNull { sameSms(it, tx) }
            if (existing == null) txDao.insert(tx)
            else if (!existing.isEdited) txDao.update(tx.copy(id = existing.id))
        }
        val parsedHashes = list.mapTo(HashSet()) { it.smsHash }
        for (hash in scannedHashes) {
            if (hash !in parsedHashes) txDao.deleteStaleByHash(hash)
        }
    }

    suspend fun update(tx: TransactionEntity) = txDao.update(tx.copy(isEdited = true))
    // Auto-recategorization only changes the category — crucially it must NOT
    // set isEdited, or a later rescan would stop refreshing this row from the
    // (possibly improved) parser output as if the user had hand-edited it.
    suspend fun setCategory(tx: TransactionEntity, category: String) = txDao.update(tx.copy(category = category))
    // Propagates a category correction to every other transaction from the same
    // merchant (see TransactionDao.updateCategoryByMerchant).
    suspend fun applyCategoryToMerchant(merchant: String, category: String, excludeId: Long) =
        txDao.updateCategoryByMerchant(merchant, category, excludeId)
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
    fun goalContributions(): Flow<List<GoalContributionEntity>> = goalContributionDao.observeAll()
    suspend fun addGoalContribution(c: GoalContributionEntity): Long = goalContributionDao.insert(c)
    suspend fun deleteGoalContributions(goalId: Long) = goalContributionDao.deleteForGoal(goalId)

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
