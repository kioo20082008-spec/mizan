package com.mizan.money.advisor

import com.mizan.money.R
import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import com.mizan.money.data.DebtEntity
import com.mizan.money.data.GoalEntity
import com.mizan.money.data.RecurringItemEntity
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val JAN_2024_START = 1_704_067_200_000L
private const val JAN_2024_END = 1_706_745_599_999L
private val FEB_MARK = JAN_2024_START + 35L * 86_400_000L
private val MAR_MARK = JAN_2024_START + 65L * 86_400_000L

private fun tx(
    amount: Double,
    type: TxType = TxType.EXPENSE,
    category: String = "أخرى",
    currency: String = "SAR",
    merchant: String? = "Test",
    timestamp: Long = JAN_2024_START + 1_000L,
    isSelfTransfer: Boolean = false,
    excludeFromDailyAvg: Boolean = false,
    isReimbursement: Boolean = false
): TransactionEntity = TransactionEntity(
    amount = amount, currency = currency, merchant = merchant, category = category,
    type = type, rawSms = "", smsHash = "h-${System.nanoTime()}-${(0..999999).random()}",
    timestamp = timestamp, isSelfTransfer = isSelfTransfer, excludeFromDailyAvg = excludeFromDailyAvg,
    isReimbursement = isReimbursement
)

class FinancialAdvisorTest {

    @Test
    fun `reimbursements reduce spending and category totals without counting as income`() {
        val txs = listOf(
            tx(100.0, TxType.EXPENSE, category = "طعام وشراب"),
            tx(50.0, TxType.INCOME, category = "طعام وشراب", isReimbursement = true)
        )
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)

        assertEquals(50.0, s.spent, 0.001)
        assertEquals(0.0, s.income, 0.001)
        assertEquals(50.0, s.categoryTotals.first { it.category == "طعام وشراب" }.amount, 0.001)
    }

    @Test
    fun `summarize sums SAR expenses and incomes within the month range`() {
        val txs = listOf(
            tx(100.0, TxType.EXPENSE, category = "طعام وشراب"),
            tx(50.0, TxType.EXPENSE, category = "مواصلات"),
            tx(2000.0, TxType.INCOME)
        )
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)

        assertEquals(150.0, s.spent, 0.001)
        assertEquals(2000.0, s.income, 0.001)
        assertEquals(1850.0, s.net, 0.001)
        assertEquals(2, s.count)
    }

    @Test
    fun `summarize excludes transactions outside the month range`() {
        val outside = tx(999.0, timestamp = JAN_2024_END + 86_400_000L)
        val inside = tx(100.0)
        val s = FinancialAdvisor.summarize(listOf(inside, outside), JAN_2024_START, JAN_2024_END)

        assertEquals(100.0, s.spent, 0.001)
    }

    @Test
    fun `summarize converts non-SAR transactions to SAR instead of dropping them`() {
        val sar = tx(100.0, currency = "SAR")
        val usd = tx(500.0, currency = "USD")
        val s = FinancialAdvisor.summarize(listOf(sar, usd), JAN_2024_START, JAN_2024_END)

        // 500 USD at the default 3.75 rate must move the budget, not vanish.
        assertEquals(100.0 + 500.0 * 3.75, s.spent, 0.001)
    }

    @Test
    fun `summarize sorts category totals descending by amount`() {
        val txs = listOf(
            tx(30.0, category = "ترفيه"),
            tx(120.0, category = "طعام وشراب"),
            tx(80.0, category = "مواصلات")
        )
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)

        assertEquals(listOf("طعام وشراب", "مواصلات", "ترفيه"), s.categoryTotals.map { it.category })
    }

    @Test
    fun `advise flags exceeding the monthly budget as DANGER`() {
        val txs = listOf(tx(1200.0))
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 1000.0, allTx = txs, monthStart = JAN_2024_START, monthEnd = JAN_2024_END, now = JAN_2024_START + 5_000L)

        assertTrue(advice.any { it.level == Level.DANGER && it.titleRes == R.string.adv_over_budget_title })
    }

    @Test
    fun `advise prompts to set a budget when none is configured`() {
        val txs = listOf(tx(100.0))
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = txs, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertTrue(advice.any { it.level == Level.INFO && it.titleRes == R.string.adv_waiting_income_title })
    }

    @Test
    fun `summarize excludes self-transfers from spent and income totals`() {
        val realExpense = tx(100.0, TxType.EXPENSE)
        val selfTransferOut = tx(5000.0, TxType.EXPENSE, isSelfTransfer = true)
        val selfTransferIn = tx(5000.0, TxType.INCOME, isSelfTransfer = true)
        val s = FinancialAdvisor.summarize(listOf(realExpense, selfTransferOut, selfTransferIn), JAN_2024_START, JAN_2024_END)

        assertEquals(100.0, s.spent, 0.001)
        assertEquals(0.0, s.income, 0.001)
    }

    @Test
    fun `advise flags a large cash withdrawal share of spending`() {
        val txs = listOf(
            tx(200.0, category = "طعام وشراب"),
            tx(300.0, category = CASH_WITHDRAWAL_CATEGORY)
        )
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = txs, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertTrue(advice.any { it.titleRes == R.string.adv_cash_title })
    }

    @Test
    fun `advise detects a recurring monthly salary across multiple months`() {
        val allTx = listOf(
            tx(9500.0, TxType.INCOME, timestamp = JAN_2024_START + 1_000L),
            tx(9600.0, TxType.INCOME, timestamp = FEB_MARK),
            tx(9400.0, TxType.INCOME, timestamp = MAR_MARK)
        )
        val s = FinancialAdvisor.summarize(allTx, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = allTx, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertTrue(advice.any { it.titleRes == R.string.adv_salary_detected_title })
    }

    @Test
    fun `a manually entered salary overrides the auto-detected one and uses different wording`() {
        val allTx = listOf(
            tx(9500.0, TxType.INCOME, timestamp = JAN_2024_START + 1_000L),
            tx(9600.0, TxType.INCOME, timestamp = FEB_MARK)
        )
        val s = FinancialAdvisor.summarize(allTx, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(
            s, monthlyBudget = 0.0, allTx = allTx, monthStart = JAN_2024_START, monthEnd = JAN_2024_END,
            manualSalary = 12000.0
        )

        val salaryAdvice = advice.first { it.titleRes == R.string.adv_salary_manual_title }
        assertTrue(salaryAdvice.bodyArgs.contains(FinancialAdvisor.fmt(12000.0)))
        // The 50/30/20 plan is driven by this month's *actual* income (9500)
        // once it has posted; the manual salary only overrides the
        // auto-detected figure when no income has landed yet. This mirrors the
        // dedicated `planningIncome prefers this month's real income` test.
        val plan = advice.first { it.titleRes == R.string.adv_5030_20_title }
        assertTrue(plan.bodyArgs.contains(FinancialAdvisor.fmt(9500.0)))
    }

    @Test
    fun `detectSalary does not treat two wildly different monthly deposits as a salary`() {
        val allTx = listOf(
            tx(8000.0, TxType.INCOME, timestamp = JAN_2024_START + 1_000L),
            tx(13000.0, TxType.INCOME, timestamp = FEB_MARK)
        )
        val s = FinancialAdvisor.summarize(allTx, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = allTx, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertFalse(advice.any { it.titleRes == R.string.adv_salary_manual_title || it.titleRes == R.string.adv_salary_detected_title })
    }

    @Test
    fun `advise does not claim a salary from a single month of income`() {
        val txs = listOf(tx(9500.0, TxType.INCOME))
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = txs, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertFalse(advice.any { it.titleRes == R.string.adv_salary_manual_title || it.titleRes == R.string.adv_salary_detected_title })
    }

    @Test
    fun `50-30-20 advice uses the detected salary even when this month has no income yet`() {
        val history = listOf(
            tx(8000.0, TxType.INCOME, timestamp = JAN_2024_START + 1_000L),
            tx(8000.0, TxType.INCOME, timestamp = FEB_MARK)
        )
        val marchStart = MAR_MARK - 5L * 86_400_000L
        val marchEnd = MAR_MARK + 25L * 86_400_000L
        val s = FinancialAdvisor.summarize(history, marchStart, marchEnd)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = history, monthStart = marchStart, monthEnd = marchEnd)

        assertEquals(0.0, s.income, 0.001)
        assertTrue(advice.any { it.titleRes == R.string.adv_5030_20_title && it.bodyArgs.contains(FinancialAdvisor.fmt(8000.0)) })
    }

    @Test
    fun `detectSubscriptions converts a foreign-currency charge to SAR`() {
        val allTx = listOf(
            tx(35.0, merchant = "Netflix", category = "اشتراكات", currency = "SAR", timestamp = JAN_2024_START + 1_000L),
            tx(35.0, merchant = "Netflix", category = "اشتراكات", currency = "SAR", timestamp = FEB_MARK),
            // 9.33 USD * 3.75 = 34.99 SAR — same subscription, not a separate huge one.
            tx(9.33, merchant = "Netflix", category = "اشتراكات", currency = "USD", timestamp = MAR_MARK)
        )
        val s = FinancialAdvisor.summarize(allTx, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = allTx, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        val subsAdvice = advice.firstOrNull { it.titleRes == R.string.adv_subscriptions_title }
        assertTrue(subsAdvice != null)
        assertTrue(subsAdvice!!.bodyArgs.contains(FinancialAdvisor.fmt(35.0)))
    }

    @Test
    fun `advise does not show a nonsensical one-day pace when viewing a past month`() {
        val txs = listOf(tx(850.0))
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)
        val farFuture = JAN_2024_END + 180L * 86_400_000L
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 1000.0, allTx = txs, monthStart = JAN_2024_START, monthEnd = JAN_2024_END, now = farFuture)

        val paceAdvice = advice.first { it.titleRes == R.string.adv_approaching_title }
        // In a past month, the "future pace" body should never be used — only
        // the compact past-month variant.
        assertEquals(R.string.adv_approaching_body_past, paceAdvice.bodyRes)
    }

    @Test
    fun `advise does not mistake a recurring self-transfer for a subscription`() {
        val savings = List(4) {
            tx(1000.0, merchant = "حسابي التوفير", category = "اشتراكات", isSelfTransfer = true, timestamp = JAN_2024_START + it * 1_000L)
        }
        val s = FinancialAdvisor.summarize(savings, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = savings, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertFalse(advice.any { it.titleRes == R.string.adv_subscriptions_title })
    }

    @Test
    fun `detectSubscriptions ignores a recurring similar-amount food-delivery or installment merchant`() {
        val allTx = listOf(
            tx(25.0, merchant = "HungerStation", category = "طعام وشراب", timestamp = JAN_2024_START + 1_000L),
            tx(25.0, merchant = "HungerStation", category = "طعام وشراب", timestamp = FEB_MARK),
            tx(25.0, merchant = "HungerStation", category = "طعام وشراب", timestamp = MAR_MARK),
            tx(200.0, merchant = "Tabby", category = "تسوق", timestamp = JAN_2024_START + 2_000L),
            tx(200.0, merchant = "Tabby", category = "تسوق", timestamp = FEB_MARK + 1_000L)
        )
        val s = FinancialAdvisor.summarize(allTx, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = allTx, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertFalse(advice.any { it.titleRes == R.string.adv_subscriptions_title })
    }

    @Test
    fun `summarize excludes flagged transactions from dailyAvg but still counts them in spent`() {
        val txs = listOf(
            tx(100.0, excludeFromDailyAvg = false),
            tx(3000.0, category = "فواتير", excludeFromDailyAvg = true)
        )
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)

        assertEquals(3100.0, s.spent, 0.001)
        val daysPassed = ((JAN_2024_END.coerceAtMost(System.currentTimeMillis()) - JAN_2024_START) / 86_400_000L).toInt() + 1
        assertEquals(100.0 / daysPassed.coerceAtLeast(1), s.dailyAvg, 0.01)
    }

    @Test
    fun `planningIncome prefers this month's real income over salary once it has posted`() {
        val history = listOf(
            tx(9000.0, TxType.INCOME, timestamp = JAN_2024_START + 1_000L),
            tx(9000.0, TxType.INCOME, timestamp = FEB_MARK)
        )
        val s = FinancialAdvisor.summarize(history, JAN_2024_START, JAN_2024_END)
        assertEquals(9000.0, FinancialAdvisor.planningIncome(s, history, manualSalary = 12000.0)!!, 0.001)

        val onlyARefundPosted = listOf(tx(500.0, TxType.INCOME))
        val s2 = FinancialAdvisor.summarize(onlyARefundPosted, JAN_2024_START, JAN_2024_END)
        assertEquals(500.0, FinancialAdvisor.planningIncome(s2, onlyARefundPosted, manualSalary = 0.0)!!, 0.001)
    }

    @Test
    fun `planningIncome falls back to manual then detected salary before any income posts`() {
        val priorMonthsOnly = listOf(
            tx(9000.0, TxType.INCOME, timestamp = FEB_MARK),
            tx(9000.0, TxType.INCOME, timestamp = MAR_MARK)
        )
        val s = FinancialAdvisor.summarize(priorMonthsOnly, JAN_2024_START, JAN_2024_END)

        assertEquals(12000.0, FinancialAdvisor.planningIncome(s, priorMonthsOnly, manualSalary = 12000.0)!!, 0.001)
        assertEquals(9000.0, FinancialAdvisor.planningIncome(s, priorMonthsOnly, manualSalary = 0.0)!!, 0.001)
        assertEquals(null, FinancialAdvisor.planningIncome(s, emptyList(), manualSalary = 0.0))
    }

    @Test
    fun `plan splits fixed debt and savings commitments and leaves the rest free`() {
        val now = 1_000_000_000_000L
        val txs = listOf(
            tx(600.0, TxType.EXPENSE, category = "طعام وشراب", timestamp = now - 10L * 86_400_000L),
            tx(400.0, TxType.EXPENSE, category = "مواصلات", timestamp = now - 9L * 86_400_000L),
        )
        val fixedItems = listOf(
            RecurringItemEntity(merchant = "إيجار", expectedAmount = 2000.0, expectedDayOfMonth = 1, category = "فواتير", isFixed = true),
            RecurringItemEntity(merchant = "نتفلكس", expectedAmount = 50.0, expectedDayOfMonth = 5, category = "اشتراكات", isFixed = false),
        )
        val debts = listOf(DebtEntity(name = "قرض", totalAmount = 3000.0, remainingAmount = 1500.0, installmentAmount = 300.0))
        val goals = listOf(GoalEntity(name = "طوارئ", targetAmount = 1200.0, currentAmount = 0.0, monthlyAmount = 100.0))

        val plan = FinancialAdvisor.plan(
            allTx = txs, income = 3000.0, categories = listOf("طعام وشراب", "مواصلات"),
            fixedItems = fixedItems, debts = debts, goals = goals, now = now, monthsBack = 1
        )

        assertEquals(1, plan.fixed.size)
        assertEquals(2000.0, plan.fixedTotal, 0.001)
        assertEquals(300.0, plan.debtTotal, 0.001)
        assertEquals(100.0, plan.savingsTotal, 0.001)
        assertEquals(2400.0, plan.committedTotal, 0.001)
        assertEquals(600.0, plan.free, 0.001)
        // History: food 600 + transport 400 = 1000, scaled to the 600 free.
        assertEquals("طعام وشراب", plan.suggestions[0].category)
        assertEquals(360.0, plan.suggestions[0].amount, 0.001)
        assertEquals(240.0, plan.suggestions[1].amount, 0.001)
    }

    @Test
    fun `plan with no income keeps raw historical suggestions`() {
        val now = 1_000_000_000_000L
        val txs = listOf(tx(300.0, TxType.EXPENSE, category = "تسوق", timestamp = now - 5L * 86_400_000L))
        val plan = FinancialAdvisor.plan(
            allTx = txs, income = 0.0, categories = listOf("تسوق"),
            fixedItems = emptyList(), debts = emptyList(), goals = emptyList(), now = now, monthsBack = 1
        )
        assertEquals(0.0, plan.fixedTotal, 0.001)
        assertEquals(0.0, plan.free, 0.001)
        assertEquals(300.0, plan.suggestions.first().amount, 0.001)
    }

    @Test
    fun `goalMonthlySaving prefers the explicit amount then spreads the target over months left`() {
        val now = 1_000_000_000_000L
        val explicit = GoalEntity(name = "سيارة", targetAmount = 1200.0, currentAmount = 0.0, monthlyAmount = 250.0)
        assertEquals(250.0, FinancialAdvisor.goalMonthlySaving(explicit, now), 0.001)

        val byDeadline = GoalEntity(
            name = "سفر", targetAmount = 1200.0, currentAmount = 0.0,
            targetDate = now + 6L * 30L * 86_400_000L
        )
        assertEquals(200.0, FinancialAdvisor.goalMonthlySaving(byDeadline, now), 0.001)

        val noDeadline = GoalEntity(name = "أخرى", targetAmount = 1200.0, currentAmount = 0.0)
        assertEquals(0.0, FinancialAdvisor.goalMonthlySaving(noDeadline, now), 0.001)
    }
}
