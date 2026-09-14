package com.mizan.money.advisor

import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class Level { INFO, GOOD, WARN, DANGER }
data class Advice(val title: String, val body: String, val level: Level)
data class CategoryTotal(val category: String, val amount: Double, val share: Double)
data class MonthSummary(
    val spent: Double, val income: Double, val net: Double, val count: Int,
    val dailyAvg: Double, val categoryTotals: List<CategoryTotal>, val largest: TransactionEntity?
)

object FinancialAdvisor {
    fun fmt(v: Double): String = String.format(Locale.US, "%,.2f", v)
    fun summarize(txs: List<TransactionEntity>, monthStart: Long, monthEnd: Long): MonthSummary {
        // Totals are only meaningful within one currency; scope to SAR (the app's
        // primary currency) so a USD/EUR transaction doesn't get added in as-is.
        val inMonth = txs.filter { it.timestamp in monthStart..monthEnd && it.currency == "SAR" }
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
        return MonthSummary(spent, income, income - spent, expenses.size, spent / daysPassed, byCat, expenses.maxByOrNull { it.amount })
    }
    fun advise(summary: MonthSummary, monthlyBudget: Double, allTx: List<TransactionEntity>,
               monthStart: Long, monthEnd: Long, now: Long = System.currentTimeMillis()): List<Advice> {
        val list = mutableListOf<Advice>()
        if (monthlyBudget <= 0.0) {
            list += Advice("حدّد ميزانيتك الشهرية 🎯",
                "لم تحدد ميزانية بعد. اذهب لتبويب «الميزانية» واكتب المبلغ الذي تريد ألا تتجاوزه هذا الشهر.",
                Level.INFO)
        } else {
            val pct = summary.spent / monthlyBudget
            val remaining = monthlyBudget - summary.spent
            val daysLeft = max(1, ((monthEnd - now) / 86_400_000L).toInt())
            val safeDaily = max(0.0, remaining / daysLeft)
            when {
                pct >= 1.0 -> list += Advice("تجاوزت الميزانية ⚠️",
                    "صرفت ${fmt(summary.spent)} من أصل ${fmt(monthlyBudget)} ر.س (${(pct * 100).toInt()}%). تجاوزك ${fmt(summary.spent - monthlyBudget)} ر.س.",
                    Level.DANGER)
                pct >= 0.8 -> list += Advice("اقتربت من الحد 🟠",
                    "استهلكت ${(pct * 100).toInt()}% من ميزانيتك. المتبقي ${fmt(remaining)} ر.س لـ $daysLeft يوم، بمعدل ${fmt(safeDaily)} ر.س يومياً.",
                    Level.WARN)
                else -> list += Advice("أنت في المسار الصحيح ✅",
                    "صرفت ${(pct * 100).toInt()}% من ميزانيتك. المتبقي ${fmt(remaining)} ر.س، ويمكنك صرف ${fmt(safeDaily)} ر.س يومياً.",
                    Level.GOOD)
            }
        }
        summary.categoryTotals.firstOrNull()?.let { top ->
            if (top.share >= 0.35 && summary.spent > 0) {
                list += Advice("أكبر بند: ${top.category} 🔍",
                    "استهلكت «${top.category}» ${fmt(top.amount)} ر.س أي ${(top.share * 100).toInt()}% من مصاريفك. خفّضها 20% وستوفّر ~${fmt(top.amount * 0.2)} ر.س.",
                    Level.WARN)
            }
        }
        if (summary.spent > 0) {
            list += Advice("معدل صرفك اليومي 📊",
                "تصرف بمعدل ${fmt(summary.dailyAvg)} ر.س يومياً. لو استمريت فستنفق ~${fmt(summary.dailyAvg * 30)} ر.س شهرياً.",
                Level.INFO)
        }
        val subs = detectSubscriptions(allTx)
        if (subs.isNotEmpty()) {
            val total = subs.sumOf { it.second }
            list += Advice("اشتراكات متكررة 💳",
                "وجدت ${subs.size} اشتراك: ${subs.joinToString("، ") { "${it.first} (${fmt(it.second)})" }}. الإجمالي الشهري ${fmt(total)} ر.س = ${fmt(total * 12)} ر.س سنوياً.",
                Level.WARN)
        }
        summary.largest?.let { big ->
            if (summary.spent > 0 && big.amount / summary.spent >= 0.25) {
                list += Advice("عملية كبيرة رصدتها 👀",
                    "عملية ${fmt(big.amount)} ر.س لدى «${big.merchant ?: "غير معروف"}» = ${((big.amount / summary.spent) * 100).toInt()}% من الشهر.",
                    Level.INFO)
            }
        }
        if (summary.income > 0) {
            val rate = (summary.income - summary.spent) / summary.income
            val msg = when {
                rate >= 0.2 -> "ممتاز! نسبة ادخارك ${(rate * 100).toInt()}%."
                rate >= 0 -> "نسبة ادخارك ${(rate * 100).toInt()}%. حاول الوصول لـ 20%."
                else -> "تصرف أكثر مما تدخل بـ ${fmt(summary.spent - summary.income)} ر.س!"
            }
            list += Advice(
                if (rate >= 0.2) "ادخار ممتاز 🏆" else "راجع نسبة الادخار",
                msg,
                if (rate >= 0.2) Level.GOOD else if (rate >= 0) Level.INFO else Level.DANGER)
            list += Advice("قاعدة 50 / 30 / 20 💡",
                "من دخل ${fmt(summary.income)} ر.س: ${fmt(summary.income * 0.5)} للاحتياجات، ${fmt(summary.income * 0.3)} للرغبات، ${fmt(summary.income * 0.2)} للادخار.",
                Level.INFO)
        }
        return list
    }
    private fun detectSubscriptions(allTx: List<TransactionEntity>): List<Pair<String, Double>> {
        val recent = allTx.filter { it.type == TxType.EXPENSE && it.merchant != null }
        val grouped = recent.groupBy { it.merchant!!.lowercase().trim() }
        return grouped.mapNotNull { (merchant, list) ->
            if (list.size < 2) return@mapNotNull null
            val amounts = list.map { it.amount }
            val avg = amounts.average()
            val similar = amounts.count { abs(it - avg) / avg < 0.15 }
            if (similar >= 2) merchant.replaceFirstChar { it.uppercase() } to avg else null
        }.sortedByDescending { it.second }
    }
}
