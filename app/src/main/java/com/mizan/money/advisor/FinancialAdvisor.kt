package com.mizan.money.advisor

import com.mizan.money.R
import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class Level { INFO, GOOD, WARN, DANGER }

// Advice now carries string-resource IDs (and their formatting args) instead
// of pre-formatted Arabic strings — the UI resolves them through the active
// locale, so the same advice pipeline works for both Arabic and English.
data class Advice(
    val titleRes: Int,
    val titleArgs: List<Any> = emptyList(),
    val bodyRes: Int,
    val bodyArgs: List<Any> = emptyList(),
    val level: Level,
)

data class CategoryTotal(val category: String, val amount: Double, val share: Double)
data class MonthSummary(
    val spent: Double, val income: Double, val net: Double, val count: Int,
    val dailyAvg: Double, val categoryTotals: List<CategoryTotal>, val largest: TransactionEntity?
)

object FinancialAdvisor {
    fun fmt(v: Double): String = String.format(Locale.US, "%,.2f", v)

    fun summarize(txs: List<TransactionEntity>, monthStart: Long, monthEnd: Long): MonthSummary {
        val inMonth = txs.filter {
            it.timestamp in monthStart..monthEnd && it.currency == "SAR" && !it.isSelfTransfer
        }
        val expenses = inMonth.filter { it.type == TxType.EXPENSE }
        val incomes = inMonth.filter { it.type == TxType.INCOME }
        val spent = expenses.sumOf { it.amount }
        val income = incomes.sumOf { it.amount }
        val daysPassed = max(1, ((System.currentTimeMillis().coerceAtMost(monthEnd) - monthStart) / 86_400_000L).toInt() + 1)
        val byCat = expenses.groupBy { it.category }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                CategoryTotal(cat, sum, if (spent > 0) sum / spent else 0.0)
            }.sortedByDescending { it.amount }
        val dailyAvgBasis = expenses.filter { !it.excludeFromDailyAvg }.sumOf { it.amount }
        return MonthSummary(spent, income, income - spent, expenses.size, dailyAvgBasis / daysPassed, byCat, expenses.maxByOrNull { it.amount })
    }

    fun advise(
        summary: MonthSummary,
        monthlyBudget: Double,
        allTx: List<TransactionEntity>,
        monthStart: Long,
        monthEnd: Long,
        now: Long = System.currentTimeMillis(),
        manualSalary: Double = 0.0
    ): List<Advice> {
        val list = mutableListOf<Advice>()

        if (monthlyBudget <= 0.0) {
            list += Advice(
                titleRes = R.string.adv_waiting_income_title,
                bodyRes = R.string.adv_waiting_income_body,
                level = Level.INFO,
            )
        } else {
            val pct = summary.spent / monthlyBudget
            val remaining = monthlyBudget - summary.spent
            val isPastMonth = now > monthEnd
            val daysLeft = if (isPastMonth) 0 else max(1, ((monthEnd - now) / 86_400_000L).toInt())
            val safeDaily = if (isPastMonth) 0.0 else max(0.0, remaining / daysLeft)
            val pctInt = (pct * 100).toInt()
            when {
                pct >= 1.0 -> list += Advice(
                    titleRes = R.string.adv_over_budget_title,
                    bodyRes = R.string.adv_over_budget_body,
                    bodyArgs = listOf(fmt(summary.spent), fmt(monthlyBudget), pctInt, fmt(summary.spent - monthlyBudget)),
                    level = Level.DANGER,
                )
                pct >= 0.8 -> list += Advice(
                    titleRes = R.string.adv_approaching_title,
                    bodyRes = if (isPastMonth) R.string.adv_approaching_body_past else R.string.adv_approaching_body_future,
                    bodyArgs = if (isPastMonth) listOf(pctInt, fmt(remaining))
                               else listOf(pctInt, fmt(remaining), daysLeft, fmt(safeDaily)),
                    level = Level.WARN,
                )
                else -> list += Advice(
                    titleRes = R.string.adv_on_track_title,
                    bodyRes = if (isPastMonth) R.string.adv_on_track_body_past else R.string.adv_on_track_body_future,
                    bodyArgs = if (isPastMonth) listOf(pctInt)
                               else listOf(pctInt, fmt(remaining), fmt(safeDaily)),
                    level = Level.GOOD,
                )
            }
        }

        summary.categoryTotals.firstOrNull()?.let { top ->
            if (top.share >= 0.35 && summary.spent > 0) {
                list += Advice(
                    titleRes = R.string.adv_top_category_title_fmt,
                    titleArgs = listOf(top.category),
                    bodyRes = R.string.adv_top_category_body_fmt,
                    bodyArgs = listOf(top.category, fmt(top.amount), (top.share * 100).toInt(), fmt(top.amount * 0.2)),
                    level = Level.WARN,
                )
            }
        }

        summary.categoryTotals.firstOrNull { it.category == CASH_WITHDRAWAL_CATEGORY }?.let { cash ->
            if (cash.share >= 0.15 && summary.spent > 0) {
                list += Advice(
                    titleRes = R.string.adv_cash_title,
                    bodyRes = R.string.adv_cash_body_fmt,
                    bodyArgs = listOf(fmt(cash.amount), (cash.share * 100).toInt()),
                    level = Level.INFO,
                )
            }
        }

        if (summary.spent > 0) {
            list += Advice(
                titleRes = R.string.adv_daily_avg_title,
                bodyRes = R.string.adv_daily_avg_body_fmt,
                bodyArgs = listOf(fmt(summary.dailyAvg), fmt(summary.dailyAvg * 30)),
                level = Level.INFO,
            )
        }

        val subs = detectSubscriptions(allTx)
        if (subs.isNotEmpty()) {
            val total = subs.sumOf { it.second }
            val names = subs.joinToString("، ") { "${it.first} (${fmt(it.second)})" }
            list += Advice(
                titleRes = R.string.adv_subscriptions_title,
                bodyRes = R.string.adv_subscriptions_body_fmt,
                bodyArgs = listOf(subs.size, names, fmt(total), fmt(total * 12)),
                level = Level.WARN,
            )
        }

        summary.largest?.let { big ->
            if (summary.spent > 0 && big.amount / summary.spent >= 0.25) {
                list += Advice(
                    titleRes = R.string.adv_large_tx_title,
                    bodyRes = R.string.adv_large_tx_body_fmt,
                    bodyArgs = listOf(fmt(big.amount), big.merchant ?: "—", ((big.amount / summary.spent) * 100).toInt()),
                    level = Level.INFO,
                )
            }
        }

        if (summary.income > 0) {
            val rate = (summary.income - summary.spent) / summary.income
            val rateInt = (rate * 100).toInt()
            when {
                rate >= 0.2 -> list += Advice(
                    titleRes = R.string.adv_savings_excellent_title,
                    bodyRes = R.string.adv_savings_excellent_body_fmt,
                    bodyArgs = listOf(rateInt),
                    level = Level.GOOD,
                )
                rate >= 0 -> list += Advice(
                    titleRes = R.string.adv_savings_ok_title,
                    bodyRes = R.string.adv_savings_ok_body_fmt,
                    bodyArgs = listOf(rateInt),
                    level = Level.INFO,
                )
                else -> list += Advice(
                    titleRes = R.string.adv_savings_ok_title,
                    bodyRes = R.string.adv_savings_bad_body_fmt,
                    bodyArgs = listOf(fmt(summary.spent - summary.income)),
                    level = Level.DANGER,
                )
            }
        }

        val salary = manualSalary.takeIf { it > 0 } ?: detectSalary(allTx)
        if (salary != null) {
            val isManual = manualSalary > 0
            list += Advice(
                titleRes = if (isManual) R.string.adv_salary_manual_title else R.string.adv_salary_detected_title,
                bodyRes = if (isManual) R.string.adv_salary_manual_body_fmt else R.string.adv_salary_detected_body_fmt,
                bodyArgs = listOf(fmt(salary)),
                level = Level.INFO,
            )
        }

        val planningIncome = resolveIncome(summary, salary)
        if (planningIncome != null) {
            list += Advice(
                titleRes = R.string.adv_5030_20_title,
                bodyRes = R.string.adv_5030_20_body_fmt,
                bodyArgs = listOf(
                    fmt(planningIncome),
                    fmt(planningIncome * 0.5),
                    fmt(planningIncome * 0.3),
                    fmt(planningIncome * 0.2),
                ),
                level = Level.INFO,
            )
        }

        return list
    }

    fun planningIncome(summary: MonthSummary, allTx: List<TransactionEntity>, manualSalary: Double = 0.0): Double? =
        resolveIncome(summary, manualSalary.takeIf { it > 0 } ?: detectSalary(allTx))

    private fun resolveIncome(summary: MonthSummary, salary: Double?): Double? =
        summary.income.takeIf { it > 0 } ?: salary

    private fun detectSalary(allTx: List<TransactionEntity>): Double? {
        val incomes = allTx.filter { it.type == TxType.INCOME && it.currency == "SAR" && !it.isSelfTransfer }
        if (incomes.isEmpty()) return null
        fun monthKeyOf(ts: Long): Int {
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
        }
        val perMonth = incomes.groupBy { monthKeyOf(it.timestamp) }
            .mapValues { (_, list) -> list.maxOf { it.amount } }
        if (perMonth.size < 2) return null
        val amounts = perMonth.values.toList()
        val avg = amounts.average()
        val consistentMonths = amounts.count { abs(it - avg) / avg < 0.15 }
        return if (consistentMonths >= 2) avg else null
    }

    private val merchantSuffixes = Regex("""\.(com|net|org)\b|\b(inc|llc|ltd|co)\.?\b""", RegexOption.IGNORE_CASE)
    private fun normalizeMerchantName(raw: String): String =
        merchantSuffixes.replace(raw.lowercase(Locale.ROOT), "")
            .replace(Regex("""[^a-z0-9؀-ۿ]+"""), " ")
            .trim()

    private fun detectSubscriptions(allTx: List<TransactionEntity>): List<Pair<String, Double>> {
        val recent = allTx.filter {
            it.type == TxType.EXPENSE && it.merchant != null && it.currency == "SAR" &&
                !it.isSelfTransfer && it.category == "اشتراكات"
        }
        val grouped = recent.groupBy { normalizeMerchantName(it.merchant!!) }
        return grouped.mapNotNull { (merchant, list) ->
            if (list.size < 2) return@mapNotNull null
            val amounts = list.map { it.amount }
            val avg = amounts.average()
            val similar = amounts.count { abs(it - avg) / avg < 0.15 }
            if (similar >= 2) merchant.replaceFirstChar { it.uppercase() } to avg else null
        }.sortedByDescending { it.second }
    }
}
