package com.mizan.money.advisor

import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class Level { INFO, GOOD, WARN, DANGER }
data class Advice(val title: String, val body: String, val level: Level)
data class CategoryTotal(val category: String, val amount: Double, val share: Double)
data class BurnRate(
    val dailyAvg: Double,
    val projected: Double,
    val daysInMonth: Int,
    val daysPassed: Int,
    val overUnder: Double // projected - budget: positive means heading for overspend
)
data class MonthSummary(
    val spent: Double,          // net of reimbursedPercent — real out-of-pocket cost, for stats/budgets
    val grossSpent: Double,     // before reimbursedPercent — what actually left the account, for balance
    val income: Double,         // real income only (excludes reimbursements)
    val reimbursements: Double, // money that came back for a shared expense (isReimbursement = true)
    val net: Double,            // income - spent — the accounting net, unrelated to cash on hand
    val balance: Double,        // income + reimbursements - grossSpent — actual cash available
    val count: Int,
    val dailyAvg: Double, val categoryTotals: List<CategoryTotal>, val largest: TransactionEntity?
)

// The actual out-of-pocket cost after subtracting the percentage a housemate/
// friend paid back for a shared expense — coerced to 0 in case reimbursedPercent
// somehow exceeds 100.
private fun TransactionEntity.netSpend(): Double {
    val pct = reimbursedPercent.coerceIn(0, 100)
    return (amount * (100 - pct) / 100.0).coerceAtLeast(0.0)
}

object FinancialAdvisor {
    fun fmt(v: Double): String = String.format(Locale.US, "%,.2f", v)
    fun summarize(txs: List<TransactionEntity>, monthStart: Long, monthEnd: Long): MonthSummary {
        // Totals are only meaningful within one currency; scope to SAR (the app's
        // primary currency) so a USD/EUR transaction doesn't get added in as-is.
        // Self-transfers (money moved between the user's own accounts) are excluded
        // too, since they're neither real income nor real spending.
        val inMonth = txs.filter {
            it.timestamp in monthStart..monthEnd && it.currency == "SAR" && !it.isSelfTransfer
        }
        val expenses = inMonth.filter { it.type == TxType.EXPENSE }
        // A reimbursement (money coming back for a shared expense) isn't real
        // income — it's already netted out of the expense side via netSpend(),
        // so counting it here too would both shrink spending AND inflate income
        // for the same shared purchase. It's still real cash though, so it
        // counts separately toward `balance`.
        val realIncome = inMonth.filter { it.type == TxType.INCOME && !it.isReimbursement }
        val reimbursements = inMonth.filter { it.type == TxType.INCOME && it.isReimbursement }
        val spent = expenses.sumOf { it.netSpend() }
        val grossSpent = expenses.sumOf { it.amount }
        val income = realIncome.sumOf { it.amount }
        val reimb = reimbursements.sumOf { it.amount }
        val daysPassed = max(1, ((System.currentTimeMillis().coerceAtMost(monthEnd) - monthStart) / 86_400_000L).toInt() + 1)
        val byCat = expenses.groupBy { it.category }
            .map { (cat, list) ->
                val sum = list.sumOf { it.netSpend() }
                CategoryTotal(cat, sum, if (spent > 0) sum / spent else 0.0)
            }.sortedByDescending { it.amount }
        // A single big irregular bill (rent, etc) posted on one day would
        // otherwise dominate "average daily spend" — the user flags which
        // transactions to leave out of this one figure; totals/budgets/category
        // breakdowns above still include them, since that money is still spent.
        val dailyAvgBasis = expenses.filter { !it.excludeFromDailyAvg }.sumOf { it.netSpend() }
        return MonthSummary(
            spent = spent, grossSpent = grossSpent, income = income, reimbursements = reimb,
            net = income - spent, balance = income + reimb - grossSpent,
            count = expenses.size, dailyAvg = dailyAvgBasis / daysPassed,
            categoryTotals = byCat, largest = expenses.maxByOrNull { it.netSpend() }
        )
    }
    fun advise(summary: MonthSummary, monthlyBudget: Double, allTx: List<TransactionEntity>,
               monthStart: Long, monthEnd: Long, now: Long = System.currentTimeMillis(),
               manualSalary: Double = 0.0): List<Advice> {
        val list = mutableListOf<Advice>()
        if (monthlyBudget <= 0.0) {
            list += Advice("بانتظار دخل هذا الشهر 🎯",
                "لم يصلك دخل بعد هذا الشهر، ولا راتب محفوظ نقدّر عليه. بمجرد وصول أول إيداع سنقدر نحسب استهلاكك من رصيدك الفعلي.",
                Level.INFO)
        } else {
            val pct = summary.spent / monthlyBudget
            val remaining = monthlyBudget - summary.spent
            // A "per day" pace is meaningless once the month being viewed has
            // already ended — guard it instead of dividing the whole remaining
            // budget by a clamped 1 day and presenting that as today's pace.
            val isPastMonth = now > monthEnd
            val daysLeft = if (isPastMonth) 0 else max(1, ((monthEnd - now) / 86_400_000L).toInt())
            val safeDaily = if (isPastMonth) 0.0 else max(0.0, remaining / daysLeft)
            when {
                pct >= 1.0 -> list += Advice("تجاوزت الميزانية ⚠️",
                    "صرفت ${fmt(summary.spent)} من أصل ${fmt(monthlyBudget)} ر.س (${(pct * 100).toInt()}%). تجاوزك ${fmt(summary.spent - monthlyBudget)} ر.س.",
                    Level.DANGER)
                pct >= 0.8 -> list += Advice("اقتربت من الحد 🟠",
                    if (isPastMonth) "استهلكت ${(pct * 100).toInt()}% من ميزانية ذلك الشهر، بمتبقي ${fmt(remaining)} ر.س."
                    else "استهلكت ${(pct * 100).toInt()}% من ميزانيتك. المتبقي ${fmt(remaining)} ر.س لـ $daysLeft يوم، بمعدل ${fmt(safeDaily)} ر.س يومياً.",
                    Level.WARN)
                else -> list += Advice("أنت في المسار الصحيح ✅",
                    if (isPastMonth) "صرفت ${(pct * 100).toInt()}% من ميزانية ذلك الشهر."
                    else "صرفت ${(pct * 100).toInt()}% من ميزانيتك. المتبقي ${fmt(remaining)} ر.س، ويمكنك صرف ${fmt(safeDaily)} ر.س يومياً.",
                    Level.GOOD)
            }
        }
        // Trajectory card — placed right after budget advice since it's the
        // "will I be okay by month-end?" companion to "am I okay right now?".
        burnRate(summary, monthlyBudget, monthStart, monthEnd, now)?.let { burn ->
            val delta = burn.overUnder
            when {
                delta > monthlyBudget * 0.05 -> list += Advice(
                    "بهذه الوتيرة ستتجاوز دخلك ⚠️",
                    "بمعدل صرفك الحالي (${fmt(burn.dailyAvg)} ر.س يومياً)، متوقع تنهي الشهر بـ ${fmt(burn.projected)} ر.س — أي ${fmt(delta)} ر.س أكثر من دخلك.",
                    Level.DANGER
                )
                delta > -monthlyBudget * 0.05 -> list += Advice(
                    "ستنتهي الشهر عند حد دخلك",
                    "بمعدل صرفك الحالي، متوقع تنهي الشهر بـ ${fmt(burn.projected)} ر.س، أي عند حدود دخلك بالضبط.",
                    Level.WARN
                )
                else -> list += Advice(
                    "على هذه الوتيرة ستوفّر ${fmt(-delta)} ر.س",
                    "بمعدل صرفك الحالي (${fmt(burn.dailyAvg)} ر.س يومياً)، متوقع تنهي الشهر بـ ${fmt(burn.projected)} ر.س، وستبقى ${fmt(-delta)} ر.س من دخلك.",
                    Level.GOOD
                )
            }
        }
        summary.categoryTotals.firstOrNull()?.let { top ->
            if (top.share >= 0.35 && summary.spent > 0) {
                list += Advice("أكبر بند: ${top.category} 🔍",
                    "استهلكت «${top.category}» ${fmt(top.amount)} ر.س أي ${(top.share * 100).toInt()}% من مصاريفك. خفّضها 20% وستوفّر ~${fmt(top.amount * 0.2)} ر.س.",
                    Level.WARN)
            }
        }
        summary.categoryTotals.firstOrNull { it.category == CASH_WITHDRAWAL_CATEGORY }?.let { cash ->
            if (cash.share >= 0.15 && summary.spent > 0) {
                list += Advice("سحوبات نقدية ملحوظة 💵",
                    "سحبت ${fmt(cash.amount)} ر.س نقداً، أي ${(cash.share * 100).toInt()}% من مصاريفك. المصروفات النقدية لا يمكن تتبع تفاصيلها تلقائياً من رسائل البنك — حاول تدوين أين تُصرف.",
                    Level.INFO)
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
        }
        // A user-entered salary always wins over the inferred one — detection
        // needs 2+ months of consistent deposits and can be slow to pick up a
        // new/changed salary, while the user just knows the number.
        val salary = manualSalary.takeIf { it > 0 } ?: detectSalary(allTx)
        if (salary != null) {
            if (manualSalary > 0) {
                list += Advice("راتبك الشهري 💼",
                    "حسب ما أدخلته في الإعدادات، راتبك الشهري ${fmt(salary)} ر.س.",
                    Level.INFO)
            } else {
                list += Advice("رصدنا راتبك الشهري 💼",
                    "بناءً على تكرار الإيداعات خلال الأشهر الماضية، دخلك الثابت الشهري تقريباً ${fmt(salary)} ر.س.",
                    Level.INFO)
            }
        }
        // 50/30/20 is a planning rule for regular income, so it deliberately
        // prefers the stable salary figure over "whatever posted this month"
        // (unlike planningIncome/resolveIncome, which track the real remaining
        // balance and so correctly prefer actual income) — a one-off bonus or
        // gift landing this month isn't something that makes sense to carve
        // into needs/wants/savings percentages the way a regular paycheck is.
        val ruleBasis = salary ?: summary.income.takeIf { it > 0 }
        if (ruleBasis != null) {
            list += Advice("قاعدة 50 / 30 / 20 💡",
                "من دخل ${fmt(ruleBasis)} ر.س: ${fmt(ruleBasis * 0.5)} للاحتياجات، ${fmt(ruleBasis * 0.3)} للرغبات، ${fmt(ruleBasis * 0.2)} للادخار.",
                Level.INFO)
        }
        return list
    }
    // The basis used for "how much of my money have I used up" (budget-consumption
    // card, over-budget advice): whatever actually posted as income this month, so
    // it always agrees with the real remaining balance (income - spent) shown on
    // the dashboard. Salary (manual or detected) is only a pre-payday stand-in —
    // once real income lands this month, even a salary figure the user typed in
    // is no longer a better estimate than what's actually in the account.
    fun planningIncome(summary: MonthSummary, allTx: List<TransactionEntity>, manualSalary: Double = 0.0): Double? =
        resolveIncome(summary, manualSalary.takeIf { it > 0 } ?: detectSalary(allTx))
    private fun resolveIncome(summary: MonthSummary, salary: Double?): Double? =
        summary.income.takeIf { it > 0 } ?: salary
    // Trajectory: from the month-to-date daily average (which already excludes
    // one-off bills the user flagged via excludeFromDailyAvg), project where
    // this month is heading. Returns null before enough days have passed for
    // the average to be meaningful, or when there's no budget/income to
    // compare against (a projection with no target is just a number).
    fun burnRate(
        summary: MonthSummary,
        budget: Double,
        monthStart: Long,
        monthEnd: Long,
        now: Long = System.currentTimeMillis()
    ): BurnRate? {
        if (budget <= 0.0) return null
        if (now > monthEnd) return null
        val daysInMonth = ((monthEnd - monthStart) / 86_400_000L).toInt() + 1
        val daysPassed = max(1, ((now.coerceAtMost(monthEnd) - monthStart) / 86_400_000L).toInt() + 1)
        if (daysPassed < 3) return null
        val projected = summary.dailyAvg * daysInMonth
        return BurnRate(
            dailyAvg = summary.dailyAvg,
            projected = projected,
            daysInMonth = daysInMonth,
            daysPassed = daysPassed,
            overUnder = projected - budget
        )
    }
    // Salary is inferred, not tagged per-SMS: bank wording for a payroll deposit
    // varies too much to match reliably, but a recurring similar-sized deposit
    // once a month is a strong signal on its own.
    private fun detectSalary(allTx: List<TransactionEntity>): Double? {
        val incomes = allTx.filter { it.type == TxType.INCOME && it.currency == "SAR" && !it.isSelfTransfer }
        if (incomes.isEmpty()) return null
        fun monthKeyOf(ts: Long): Int {
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
        }
        // The largest deposit per calendar month, since salary is typically a
        // person's biggest recurring credit — this filters out smaller one-off
        // refunds landing in the same month.
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
        // SAR-only (matches summarize/detectSalary), excludes self-transfers, and —
        // importantly — only looks at transactions CategoryClassifier already put
        // in "اشتراكات" (Netflix, Spotify, etc). Recurring-similar-amount alone is
        // too weak a signal on its own: frequent food-delivery orders (HungerStation,
        // Keeta) and fixed-installment BNPL charges (Tabby, Tamara) both recur with
        // near-identical amounts too, but neither is a subscription.
        val recent = allTx.filter {
            it.type == TxType.EXPENSE && it.merchant != null && it.currency == "SAR" &&
                !it.isSelfTransfer && it.category == "اشتراكات"
        }
        // A bank's own merchant-name formatting varies charge to charge for the
        // same subscription ("Netflix", "NETFLIX.COM", "Netflix Inc") — without
        // normalizing, each variant groups separately and never reaches the 2+
        // occurrences needed below, so the subscription goes undetected.
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
