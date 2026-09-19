package com.mizan.money.cloud

import com.mizan.money.data.BudgetEntity
import com.mizan.money.data.DebtEntity
import com.mizan.money.data.DebtType
import com.mizan.money.data.GoalContributionEntity
import com.mizan.money.data.GoalEntity
import com.mizan.money.data.RecurringItemEntity
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType

// Pure entity <-> Firestore-map conversion, deliberately free of any Firebase
// types so it can be unit-tested on the JVM. Fields are stored flat under
// users/{uid}/<collection>/{docId} so a future web dashboard can query them
// directly without decoding the app's own JSON backup format.
object CloudBackupMapper {

    // Budgets have a composite primary key, so they use "<monthKey>|<category>"
    // as the document id. Categories are user-defined and could in theory
    // contain a slash (which Firestore rejects in a document id), so it is
    // sanitized — the real values still live in the monthKey/category fields.
    fun budgetDocId(b: BudgetEntity): String =
        (b.monthKey + "|" + b.category).replace('/', '_')

    fun transaction(t: TransactionEntity): Map<String, Any?> = mapOf(
        "id" to t.id,
        "amount" to t.amount,
        "currency" to t.currency,
        "merchant" to t.merchant,
        "category" to t.category,
        "type" to t.type.name,
        "bankName" to t.bankName,
        "cardLast4" to t.cardLast4,
        "rawSms" to t.rawSms,
        "smsHash" to t.smsHash,
        "timestamp" to t.timestamp,
        "isManual" to t.isManual,
        "isSelfTransfer" to t.isSelfTransfer,
        "isEdited" to t.isEdited,
        "excludeFromDailyAvg" to t.excludeFromDailyAvg,
        "isReimbursement" to t.isReimbursement,
    )

    fun transactionFrom(m: Map<String, Any?>): TransactionEntity = TransactionEntity(
        id = m.long("id") ?: 0L,
        amount = m.double("amount") ?: 0.0,
        currency = m.string("currency") ?: "SAR",
        merchant = m.string("merchant"),
        category = m.string("category") ?: "أخرى",
        type = m.enumOr("type", TxType.EXPENSE) { TxType.valueOf(it) },
        bankName = m.string("bankName"),
        cardLast4 = m.string("cardLast4"),
        rawSms = m.string("rawSms") ?: "",
        smsHash = m.string("smsHash") ?: "cloud-${m.long("id") ?: 0L}",
        timestamp = m.long("timestamp") ?: 0L,
        isManual = m.bool("isManual") ?: false,
        isSelfTransfer = m.bool("isSelfTransfer") ?: false,
        isEdited = m.bool("isEdited") ?: false,
        excludeFromDailyAvg = m.bool("excludeFromDailyAvg") ?: false,
        isReimbursement = m.bool("isReimbursement") ?: false,
    )

    fun budget(b: BudgetEntity): Map<String, Any?> = mapOf(
        "monthKey" to b.monthKey,
        "category" to b.category,
        "limitAmount" to b.limitAmount,
        "rolloverEnabled" to b.rolloverEnabled,
    )

    fun budgetFrom(m: Map<String, Any?>): BudgetEntity = BudgetEntity(
        monthKey = m.string("monthKey") ?: "",
        category = m.string("category") ?: "أخرى",
        limitAmount = m.double("limitAmount") ?: 0.0,
        rolloverEnabled = m.bool("rolloverEnabled") ?: false,
    )

    fun goal(g: GoalEntity): Map<String, Any?> = mapOf(
        "id" to g.id,
        "name" to g.name,
        "targetAmount" to g.targetAmount,
        "currentAmount" to g.currentAmount,
        "targetDate" to g.targetDate,
        "monthlyAmount" to g.monthlyAmount,
        "createdAt" to g.createdAt,
        "isArchived" to g.isArchived,
    )

    fun goalFrom(m: Map<String, Any?>): GoalEntity = GoalEntity(
        id = m.long("id") ?: 0L,
        name = m.string("name") ?: "",
        targetAmount = m.double("targetAmount") ?: 0.0,
        currentAmount = m.double("currentAmount") ?: 0.0,
        targetDate = m.long("targetDate"),
        monthlyAmount = m.double("monthlyAmount") ?: 0.0,
        createdAt = m.long("createdAt") ?: System.currentTimeMillis(),
        isArchived = m.bool("isArchived") ?: false,
    )

    fun contribution(c: GoalContributionEntity): Map<String, Any?> = mapOf(
        "id" to c.id,
        "goalId" to c.goalId,
        "amount" to c.amount,
        "timestamp" to c.timestamp,
    )

    fun contributionFrom(m: Map<String, Any?>): GoalContributionEntity = GoalContributionEntity(
        id = m.long("id") ?: 0L,
        goalId = m.long("goalId") ?: 0L,
        amount = m.double("amount") ?: 0.0,
        timestamp = m.long("timestamp") ?: 0L,
    )

    fun debt(d: DebtEntity): Map<String, Any?> = mapOf(
        "id" to d.id,
        "name" to d.name,
        "type" to d.type.name,
        "totalAmount" to d.totalAmount,
        "remainingAmount" to d.remainingAmount,
        "installmentAmount" to d.installmentAmount,
        "nextDueDate" to d.nextDueDate,
        "lender" to d.lender,
        "termMonths" to d.termMonths,
        "paidMonths" to d.paidMonths,
        "createdAt" to d.createdAt,
        "isArchived" to d.isArchived,
    )

    fun debtFrom(m: Map<String, Any?>): DebtEntity = DebtEntity(
        id = m.long("id") ?: 0L,
        name = m.string("name") ?: "",
        type = m.enumOr("type", DebtType.OTHER) { DebtType.valueOf(it) },
        totalAmount = m.double("totalAmount") ?: 0.0,
        remainingAmount = m.double("remainingAmount") ?: 0.0,
        installmentAmount = m.double("installmentAmount") ?: 0.0,
        nextDueDate = m.long("nextDueDate"),
        lender = m.string("lender"),
        termMonths = m.int("termMonths") ?: 0,
        paidMonths = m.int("paidMonths") ?: 0,
        createdAt = m.long("createdAt") ?: System.currentTimeMillis(),
        isArchived = m.bool("isArchived") ?: false,
    )

    fun recurringItem(r: RecurringItemEntity): Map<String, Any?> = mapOf(
        "id" to r.id,
        "merchant" to r.merchant,
        "expectedAmount" to r.expectedAmount,
        "expectedDayOfMonth" to r.expectedDayOfMonth,
        "category" to r.category,
        "reminderEnabled" to r.reminderEnabled,
        "isFixed" to r.isFixed,
        "lastNotifiedMonthKey" to r.lastNotifiedMonthKey,
        "createdAt" to r.createdAt,
    )

    fun recurringItemFrom(m: Map<String, Any?>): RecurringItemEntity = RecurringItemEntity(
        id = m.long("id") ?: 0L,
        merchant = m.string("merchant") ?: "",
        expectedAmount = m.double("expectedAmount") ?: 0.0,
        expectedDayOfMonth = m.int("expectedDayOfMonth") ?: 1,
        category = m.string("category") ?: "أخرى",
        reminderEnabled = m.bool("reminderEnabled") ?: true,
        isFixed = m.bool("isFixed") ?: false,
        lastNotifiedMonthKey = m.string("lastNotifiedMonthKey"),
        createdAt = m.long("createdAt") ?: System.currentTimeMillis(),
    )

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean

    private fun Map<String, Any?>.long(key: String): Long? = when (val v = this[key]) {
        is Long -> v
        is Int -> v.toLong()
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

    private fun Map<String, Any?>.int(key: String): Int? = long(key)?.toInt()

    private fun Map<String, Any?>.double(key: String): Double? = when (val v = this[key]) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    private inline fun <E : Enum<E>> Map<String, Any?>.enumOr(
        key: String,
        default: E,
        parse: (String) -> E,
    ): E = try {
        string(key)?.let(parse) ?: default
    } catch (e: Exception) {
        default
    }
}
