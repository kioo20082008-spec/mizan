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
    val isManual: Boolean = false
)

@Entity(tableName = "budgets", primaryKeys = ["monthKey", "category"])
data class BudgetEntity(
    val monthKey: String,
    val category: String,
    val limitAmount: Double
)

const val TOTAL_BUDGET = "__TOTAL__"
const val ALL_MONTHS  = "ALL"
