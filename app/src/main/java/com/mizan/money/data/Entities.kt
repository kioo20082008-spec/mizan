package com.mizan.money.data

import androidx.room.*

enum class TxType { EXPENSE, INCOME }

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["smsHash"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val currency: String = "SAR",
    val merchant: String? = null,
    val category: String = "أخرى",
    val type: TxType = TxType.EXPENSE,
    val bankName: String? = null,
    val cardLast4: String? = null,
    val rawSms: String = "",
    val smsHash: String,
    val timestamp: Long,
    val isManual: Boolean = false,
    // Money moved between the user's own accounts (bank SMS wording like
    // "بين حساباتك") isn't real income or spending, so it's tracked but kept
    // out of the totals in FinancialAdvisor.
    val isSelfTransfer: Boolean = false,
    // Set once the user manually corrects this transaction (amount/category/
    // self-transfer/etc). A later rescan re-derives every SMS-sourced
    // transaction from the current parser so old parsing bugs get fixed
    // retroactively instead of leaving stale/duplicate data behind forever —
    // but it must never silently overwrite a fix the user already made.
    val isEdited: Boolean = false,
    // A large irregular/fixed bill (rent, once-a-month laundry, etc) posted on
    // a single day badly skews "average daily spending" — dividing it by days-
    // passed makes a normal month look like a spending spike. The user flags
    // these themselves; they still count in totals/budgets, just not this one
    // pace calculation.
    val excludeFromDailyAvg: Boolean = false
)

@Entity(tableName = "budgets", primaryKeys = ["monthKey", "category"])
data class BudgetEntity(
    val monthKey: String,
    val category: String,
    val limitAmount: Double,
    val rolloverEnabled: Boolean = false
)

const val TOTAL_BUDGET = "__TOTAL__"
const val ALL_MONTHS  = "ALL"
const val SELF_TRANSFER_CATEGORY = "تحويل بين حساباتي"
const val CASH_WITHDRAWAL_CATEGORY = "سحب نقدي"

// A savings goal is tracked as its own small ledger (currentAmount, updated
// directly when the user logs a contribution) rather than derived from
// linked transactions — that would need a schema change on the transactions
// table and risk double-counting against the budget/advisor totals, which
// already treat every transaction as real spend/income. Keeping goals
// self-contained is simpler and can't corrupt those existing calculations.
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    // Optional — set from "خلال كم شهر" at creation time, not a full date picker.
    val targetDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

enum class DebtType { LOAN, BNPL, CREDIT_CARD, OTHER }

// Same self-contained approach as GoalEntity: remainingAmount is decremented
// directly when the user logs a payment, independent of the transactions table.
@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: DebtType = DebtType.OTHER,
    val totalAmount: Double,
    val remainingAmount: Double,
    val installmentAmount: Double = 0.0,
    val nextDueDate: Long? = null,
    val lender: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

// A lightweight monthly reminder, not a full bill-tracking system: it just
// remembers "this merchant tends to charge ~amount around this day" so a
// heads-up notification can fire a few days ahead. Created/removed from a
// toggle on a transaction's own detail view (see TxDetailDialog), keyed by
// merchant name rather than linked to one specific transaction, since the
// point is "the next occurrence", not this one.
@Entity(tableName = "recurring_items")
data class RecurringItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val expectedAmount: Double,
    val expectedDayOfMonth: Int,
    val category: String,
    val reminderEnabled: Boolean = true,
    // Guards against notifying more than once for the same due date — reset
    // implicitly every month since this is compared against the current month key.
    val lastNotifiedMonthKey: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
