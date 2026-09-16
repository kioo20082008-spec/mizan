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
    //
    // Every column below this point was added by a Migration (ALTER TABLE ...
    // DEFAULT x) after the table already existed on real installs, so each one
    // needs @ColumnInfo(defaultValue=...) matching that SQL default exactly —
    // Room's runtime schema check compares the *declared* default against the
    // physical column's, not just the Kotlin property default, and fails
    // (crashing the app on launch, on every existing install) if they disagree.
    @ColumnInfo(defaultValue = "0") val isEdited: Boolean = false,
    // A large irregular/fixed bill (rent, once-a-month laundry, etc) posted on
    // a single day badly skews "average daily spending" — dividing it by days-
    // passed makes a normal month look like a spending spike. The user flags
    // these themselves; they still count in totals/budgets, just not this one
    // pace calculation.
    @ColumnInfo(defaultValue = "0") val excludeFromDailyAvg: Boolean = false,
    // Superseded by reimbursedPercent below (a fixed sum stops making sense
    // once the transaction's own amount is later corrected). Kept as a real,
    // if unread, field: the column is still physically on the table from the
    // v3->v4 migration, and SQLite's DROP COLUMN support isn't reliable enough
    // across the Android versions this app supports to remove it there —
    // Room's schema check requires every physical column to be declared.
    @ColumnInfo(defaultValue = "0") val reimbursedAmount: Double = 0.0,
    // For an expense: what percentage of it a housemate/friend paid back (a
    // shared grocery run, a split bill), 0-100. A percentage survives an edit
    // to the amount itself better than a fixed sum would (bought for 200,
    // split 50/50 — still 50% reimbursed if the amount later gets corrected).
    @ColumnInfo(defaultValue = "0") val reimbursedPercent: Int = 0,
    // For an income: this is money coming back for an earlier shared expense,
    // not real income — excluded from income totals so it doesn't inflate the
    // budget ceiling or get divided up by the 50/30/20 rule like a paycheck would.
    @ColumnInfo(defaultValue = "0") val isReimbursement: Boolean = false
)

@Entity(tableName = "budgets", primaryKeys = ["monthKey", "category"])
data class BudgetEntity(
    val monthKey: String,
    val category: String,
    val limitAmount: Double
)

const val TOTAL_BUDGET = "__TOTAL__"
const val ALL_MONTHS  = "ALL"
const val SELF_TRANSFER_CATEGORY = "تحويل بين حساباتي"
const val CASH_WITHDRAWAL_CATEGORY = "سحب نقدي"
