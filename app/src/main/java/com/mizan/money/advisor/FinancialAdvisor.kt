package com.mizan.money.advisor

import com.mizan.money.R
import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import com.mizan.money.data.DebtEntity
import com.mizan.money.data.ExchangeRates
import com.mizan.money.data.GoalEntity
import com.mizan.money.data.RecurringItemEntity
import com.mizan.money.data.SELF_TRANSFER_CATEGORY
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
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

// A monthly obligation that eats into the budget before discretionary spend:
// a fixed recurring item (rent, subscription), a debt installment, or a goal's
// planned monthly saving. Kept as pure data so BudgetScreen only renders it.
enum class CommitmentKind { FIXED, DEBT, SAVINGS }
data class Commitment(
    val label: String,
    val amount: Double,
    val kind: CommitmentKind,
    val category: String? = null,
)

// A suggested per-category limit derived from the user's own spending history,
// scaled down to whatever income is left after commitments.
data class BudgetSuggestion(val category: String, val amount: Double)

data class BudgetPlan(
    val income: Double,
    val fixed: List<Commitment>,
    val debts: List<Commitment>,
    val savings: List<Commitment>,
    val suggestions: List<BudgetSuggestion>,
) {
    val fixedTotal: Double get() = fixed.sumOf { it.amount }
    val debtTotal: Double get() = debts.sumOf { it.amount }
    val savingsTotal: Double get() = savings.sumOf { it.amount }
    val committedTotal: Double get() = fixedTotal + debtTotal + savingsTotal
    // What's left of income to actually budget across categories.
    val free: Double get() = (income - committedTotal).coerceAtLeast(0.0)
}

object FinancialAdvisor {
    fun fmt(v: Double): String = String.format(Locale.US, "%,.2f", v)

    fun summarize(
        txs: List<TransactionEntity>,
        monthStart: Long,
        monthEnd: Long,
        rates: Map<String, Double> = ExchangeRates.DEFAULT
    ): MonthSummary {
        // Foreign-currency rows are included via their SAR equivalent; only a
        // currency with no usable rate is skipped (rather than counting its raw
        // number as SAR, which would badly understate e.g. a USD purchase).
        fun sar(tx: TransactionEntity): Double = ExchangeRates.toSar(tx.amount, tx.currency, rates) ?: 0.0
        val inMonth = txs.filter {
            it.timestamp in monthStart..monthEnd && !it.isSelfTransfer &&
                ExchangeRates.toSar(it.amount, it.currency, rates) != null
        }
        // A reimbursement is money returned for a shared expense: it is not
        // income, it reduces spending. It only offsets the expense side, so it
        // is split out of both expenses and incomes.
        val expenses = inMonth.filter { it.type == TxType.EXPENSE && !it.isReimbursement }
        val incomes = inMonth.filter { it.type == TxType.INCOME && !it.isReimbursement }
        val offsets = inMonth.filter { it.isReimbursement && it.type == TxType.INCOME }
        val spent = expenses.sumOf { sar(it) } - offsets.sumOf { sar(it) }
        val income = incomes.sumOf { sar(it) }
        val daysPassed = max(1, ((System.currentTimeMillis().coerceAtMost(monthEnd) - monthStart) / 86_400_000L).toInt() + 1)
        // Reimbursements are subtracted from the category they were filed under
        // so the donut and per-category budgets show the net (own) share.
        val offsetsByCat = offsets.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { sar(it) } }
        val byCat = expenses.groupBy { it.category }
            .mapNotNull { (cat, list) ->
                val sum = list.sumOf { sar(it) } - (offsetsByCat[cat] ?: 0.0)
                if (sum <= 0.005) null
                else CategoryTotal(cat, sum, if (spent > 0) sum / spent else 0.0)
            }.sortedByDescending { it.amount }
        val dailyAvgBasis = (
            expenses.filter { !it.excludeFromDailyAvg }.sumOf { sar(it) } -
                offsets.filter { !it.excludeFromDailyAvg }.sumOf { sar(it) }
            ).coerceAtLeast(0.0)
        return MonthSummary(spent, income, income - spent, expenses.size, dailyAvgBasis / daysPassed, byCat, expenses.maxByOrNull { sar(it) })
    }

    fun advise(
        summary: MonthSummary,
        monthlyBudget: Double,
        allTx: List<TransactionEntity>,
        monthStart: Long,
        monthEnd: Long,
        now: Long = System.currentTimeMillis(),
        manualSalary: Double = 0.0,
        rates: Map<String, Double> = ExchangeRates.DEFAULT,
        prevSummary: MonthSummary? = null,
    ): List<Advice> {
        val list = mutableListOf<Advice>()
        val daysInMonth = max(1, ((monthEnd - monthStart) / 86_400_000L).toInt() + 1)
        val isPastMonth = now > monthEnd
        val daysPassed = if (isPastMonth) daysInMonth
            else max(1, ((now.coerceAtLeast(monthStart) - monthStart) / 86_400_000L).toInt() + 1)

        if (monthlyBudget <= 0.0) {
            list += Advice(
                titleRes = R.string.adv_waiting_income_title,
                bodyRes = R.string.adv_waiting_income_body,
                level = Level.INFO,
            )
        } else {
            val pct = summary.spent / monthlyBudget
            val remaining = monthlyBudget - summary.spent
            val daysLeft = if (isPastMonth) 0 else max(1, ((monthEnd - now) / 86_400_000L).toInt())
            val safeDaily = if (isPastMonth) 0.0 else max(0.0, remaining / daysLeft)
            val projected = summary.spent / daysPassed * daysInMonth
            val pctInt = (pct * 100).toInt()
            when {
                pct >= 1.0 -> list += Advice(
                    titleRes = R.string.adv_over_budget_title,
                    bodyRes = R.string.adv_over_budget_body,
                    bodyArgs = listOf(fmt(summary.spent), fmt(monthlyBudget), pctInt, fmt(summary.spent - monthlyBudget)),
                    level = Level.DANGER,
                )
                !isPastMonth && daysPassed >= 5 && projected > monthlyBudget * 1.05 -> list += Advice(
                    titleRes = R.string.adv_projected_title,
                    bodyRes = R.string.adv_projected_body_fmt,
                    bodyArgs = listOf(fmt(projected), fmt(projected - monthlyBudget)),
                    level = Level.WARN,
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

        val prevTotals = prevSummary?.categoryTotals?.associate { it.category to it.amount }.orEmpty()
        val pace = daysPassed.toDouble() / daysInMonth
        val rising = if (prevTotals.isEmpty()) null else summary.categoryTotals
            .asSequence()
            .filter { it.category != CASH_WITHDRAWAL_CATEGORY && it.category != SELF_TRANSFER_CATEGORY }
            .mapNotNull { cur ->
                val prev = prevTotals[cur.category] ?: return@mapNotNull null
                val expected = prev * pace
                if (expected < 50.0 || cur.amount < 100.0) return@mapNotNull null
                val up = (cur.amount - expected) / expected
                if (up < 0.3) return@mapNotNull null
                Triple(cur.category, (up * 100).toInt(), cur.amount)
            }
            .maxByOrNull { it.second }
        if (rising != null) {
            list += Advice(
                titleRes = R.string.adv_category_up_title_fmt,
                titleArgs = listOf(rising.first),
                bodyRes = R.string.adv_category_up_body_fmt,
                bodyArgs = listOf(rising.first, fmt(rising.third), rising.second),
                level = Level.WARN,
            )
        } else {
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

        val subs = detectSubscriptions(allTx, rates)
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

        val salary = manualSalary.takeIf { it > 0 } ?: detectSalary(allTx, rates)
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

    fun planningIncome(
        summary: MonthSummary,
        allTx: List<TransactionEntity>,
        manualSalary: Double = 0.0,
        rates: Map<String, Double> = ExchangeRates.DEFAULT
    ): Double? =
        resolveIncome(summary, manualSalary.takeIf { it > 0 } ?: detectSalary(allTx, rates))

    private fun resolveIncome(summary: MonthSummary, salary: Double?): Double? =
        summary.income.takeIf { it > 0 } ?: salary

    // Builds the whole monthly plan: fixed commitments, debt installments and
    // planned goal savings first, then per-category limits suggested from the
    // trailing average spend so they fit whatever income is left. Pure, so it is
    // unit-testable without a ViewModel/DB.
    fun plan(
        allTx: List<TransactionEntity>,
        income: Double,
        categories: List<String>,
        fixedItems: List<RecurringItemEntity>,
        debts: List<DebtEntity>,
        goals: List<GoalEntity>,
        rates: Map<String, Double> = ExchangeRates.DEFAULT,
        now: Long = System.currentTimeMillis(),
        monthsBack: Int = 3,
    ): BudgetPlan {
        val fixed = fixedItems.filter { it.isFixed }.map {
            Commitment(it.merchant, it.expectedAmount, CommitmentKind.FIXED, it.category)
        }
        val debtList = debts.filter { !it.isArchived && it.remainingAmount > 0.0 && it.installmentAmount > 0.0 }
            .map { Commitment(it.name, it.installmentAmount, CommitmentKind.DEBT) }
        val savings = goals.filter { !it.isArchived }.mapNotNull { g ->
            goalMonthlySaving(g, now).takeIf { it > 0.0 }?.let { Commitment(g.name, it, CommitmentKind.SAVINGS) }
        }
        val committed = fixed.sumOf { it.amount } + debtList.sumOf { it.amount } + savings.sumOf { it.amount }

        val avg = averageMonthlyByCategory(allTx, rates, now, monthsBack)
        val basis = categories
            .filter { it != SELF_TRANSFER_CATEGORY && it != CASH_WITHDRAWAL_CATEGORY }
            .mapNotNull { c -> avg[c]?.takeIf { it > 0.005 }?.let { c to it } }
        val totalBasis = basis.sumOf { it.second }
        // Never inflate past the user's actual habit: scale down to the free
        // income only when habits exceed it (income unknown => keep raw avg).
        val scale = if (income > 0.0 && totalBasis > 0.0) {
            minOf(1.0, ((income - committed).coerceAtLeast(0.0)) / totalBasis)
        } else 1.0
        val suggestions = basis.map { (c, a) -> BudgetSuggestion(c, a * scale) }.sortedByDescending { it.amount }

        return BudgetPlan(income, fixed, debtList, savings, suggestions)
    }

    // A goal's monthly saving: the explicit field when set, otherwise the
    // remaining amount spread over the months left until its target date.
    fun goalMonthlySaving(goal: GoalEntity, now: Long = System.currentTimeMillis()): Double {
        if (goal.monthlyAmount > 0.0) return goal.monthlyAmount
        val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
        val target = goal.targetDate ?: return 0.0
        if (remaining <= 0.0) return 0.0
        val months = ceil((target - now).toDouble() / (30L * 86_400_000L)).toInt().coerceAtLeast(1)
        return remaining / months
    }

    private fun averageMonthlyByCategory(
        allTx: List<TransactionEntity>,
        rates: Map<String, Double>,
        now: Long,
        monthsBack: Int,
    ): Map<String, Double> {
        val months = monthsBack.coerceAtLeast(1)
        val cutoff = now - months.toLong() * 30L * 86_400_000L
        return allTx
            .filter {
                it.type == TxType.EXPENSE && !it.isSelfTransfer && !it.isReimbursement &&
                    it.timestamp >= cutoff && ExchangeRates.toSar(it.amount, it.currency, rates) != null
            }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { ExchangeRates.toSar(it.amount, it.currency, rates) ?: 0.0 } / months }
    }

    private fun detectSalary(allTx: List<TransactionEntity>, rates: Map<String, Double>): Double? {
        val incomes = allTx.filter {
            it.type == TxType.INCOME && !it.isSelfTransfer && !it.isReimbursement &&
                ExchangeRates.toSar(it.amount, it.currency, rates) != null
        }
        if (incomes.isEmpty()) return null
        fun monthKeyOf(ts: Long): Int {
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
        }
        val perMonth = incomes.groupBy { monthKeyOf(it.timestamp) }
            .mapValues { (_, list) -> list.maxOf { ExchangeRates.toSar(it.amount, it.currency, rates) ?: 0.0 } }
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

    private fun detectSubscriptions(allTx: List<TransactionEntity>, rates: Map<String, Double>): List<Pair<String, Double>> {
        val recent = allTx.filter {
            it.type == TxType.EXPENSE && it.merchant != null &&
                !it.isSelfTransfer && !it.isReimbursement && it.category == "اشتراكات" &&
                ExchangeRates.toSar(it.amount, it.currency, rates) != null
        }
        val grouped = recent.groupBy { normalizeMerchantName(it.merchant!!) }
        return grouped.mapNotNull { (merchant, list) ->
            if (list.size < 2) return@mapNotNull null
            val amounts = list.map { ExchangeRates.toSar(it.amount, it.currency, rates)!! }
            val avg = amounts.average()
            val similar = amounts.count { abs(it - avg) / avg < 0.15 }
            if (similar >= 2) merchant.replaceFirstChar { it.uppercase() } to avg else null
        }.sortedByDescending { it.second }
    }
}
