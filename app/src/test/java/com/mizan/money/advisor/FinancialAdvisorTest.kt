package com.mizan.money.advisor

import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val JAN_2024_START = 1_704_067_200_000L // 2024-01-01T00:00:00Z
private const val JAN_2024_END = 1_706_745_599_999L   // 2024-01-31T23:59:59.999Z
private val FEB_MARK = JAN_2024_START + 35L * 86_400_000L // safely inside February
private val MAR_MARK = JAN_2024_START + 65L * 86_400_000L // safely inside March

private fun tx(
    amount: Double,
    type: TxType = TxType.EXPENSE,
    category: String = "أخرى",
    currency: String = "SAR",
    merchant: String? = "Test",
    timestamp: Long = JAN_2024_START + 1_000L,
    isSelfTransfer: Boolean = false,
    excludeFromDailyAvg: Boolean = false
): TransactionEntity = TransactionEntity(
    amount = amount, currency = currency, merchant = merchant, category = category,
    type = type, rawSms = "", smsHash = "h-${System.nanoTime()}-${(0..999999).random()}",
    timestamp = timestamp, isSelfTransfer = isSelfTransfer, excludeFromDailyAvg = excludeFromDailyAvg
)

class FinancialAdvisorTest {

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
    fun `summarize excludes non-SAR transactions from totals`() {
        val sar = tx(100.0, currency = "SAR")
        val usd = tx(500.0, currency = "USD")
        val s = FinancialAdvisor.summarize(listOf(sar, usd), JAN_2024_START, JAN_2024_END)

        assertEquals(100.0, s.spent, 0.001)
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

        assertTrue(advice.any { it.level == Level.DANGER })
    }

    @Test
    fun `advise prompts to set a budget when none is configured`() {
        val txs = listOf(tx(100.0))
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = txs, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertTrue(advice.any { it.level == Level.INFO && it.title.contains("ميزانيتك") })
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

        assertTrue(advice.any { it.title.contains("سحوبات نقدية") })
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

        assertTrue(advice.any { it.title.contains("راتبك الشهري") })
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

        val salaryAdvice = advice.first { it.title.contains("راتبك الشهري") }
        assertFalse(salaryAdvice.title.contains("رصدنا"))
        assertTrue(salaryAdvice.body.contains(FinancialAdvisor.fmt(12000.0)))
        // 50/30/20 should plan off the manually entered figure too, not the
        // auto-detected/summed one.
        val plan = advice.first { it.title.contains("50 / 30 / 20") }
        assertTrue(plan.body.contains(FinancialAdvisor.fmt(12000.0)))
    }

    @Test
    fun `detectSalary does not treat two wildly different monthly deposits as a salary`() {
        val allTx = listOf(
            tx(8000.0, TxType.INCOME, timestamp = JAN_2024_START + 1_000L),
            tx(13000.0, TxType.INCOME, timestamp = FEB_MARK)
        )
        val s = FinancialAdvisor.summarize(allTx, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = allTx, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertFalse(advice.any { it.title.contains("راتبك الشهري") })
    }

    @Test
    fun `advise does not claim a salary from a single month of income`() {
        val txs = listOf(tx(9500.0, TxType.INCOME))
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = txs, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertFalse(advice.any { it.title.contains("راتبك الشهري") })
    }

    @Test
    fun `50-30-20 advice uses the detected salary even when this month has no income yet`() {
        val history = listOf(
            tx(8000.0, TxType.INCOME, timestamp = JAN_2024_START + 1_000L),
            tx(8000.0, TxType.INCOME, timestamp = FEB_MARK)
            // no income timestamped in March, simulating "before payday"
        )
        val marchStart = MAR_MARK - 5L * 86_400_000L
        val marchEnd = MAR_MARK + 25L * 86_400_000L
        val s = FinancialAdvisor.summarize(history, marchStart, marchEnd)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = history, monthStart = marchStart, monthEnd = marchEnd)

        assertEquals(0.0, s.income, 0.001)
        assertTrue(advice.any { it.title.contains("50 / 30 / 20") && it.body.contains(FinancialAdvisor.fmt(8000.0)) })
    }

    @Test
    fun `detectSubscriptions ignores non-SAR charges even at the same merchant`() {
        val allTx = listOf(
            tx(35.0, merchant = "Netflix", category = "اشتراكات", currency = "SAR", timestamp = JAN_2024_START + 1_000L),
            tx(35.0, merchant = "Netflix", category = "اشتراكات", currency = "SAR", timestamp = FEB_MARK),
            tx(999.0, merchant = "Netflix", category = "اشتراكات", currency = "USD", timestamp = MAR_MARK)
        )
        val s = FinancialAdvisor.summarize(allTx, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = allTx, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        val subsAdvice = advice.firstOrNull { it.title.contains("اشتراكات") }
        assertTrue(subsAdvice != null)
        assertTrue(subsAdvice!!.body.contains(FinancialAdvisor.fmt(35.0)))
        assertFalse(subsAdvice.body.contains(FinancialAdvisor.fmt(999.0)))
    }

    @Test
    fun `advise does not show a nonsensical one-day pace when viewing a past month`() {
        val txs = listOf(tx(850.0))
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)
        val farFuture = JAN_2024_END + 180L * 86_400_000L // 6 months after month end
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 1000.0, allTx = txs, monthStart = JAN_2024_START, monthEnd = JAN_2024_END, now = farFuture)

        val paceAdvice = advice.first { it.title.contains("اقتربت من الحد") }
        assertFalse(paceAdvice.body.contains("لـ 1 يوم"))
    }

    @Test
    fun `advise does not mistake a recurring self-transfer for a subscription`() {
        val savings = List(4) {
            tx(1000.0, merchant = "حسابي التوفير", category = "اشتراكات", isSelfTransfer = true, timestamp = JAN_2024_START + it * 1_000L)
        }
        val s = FinancialAdvisor.summarize(savings, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = savings, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertFalse(advice.any { it.title.contains("اشتراكات") })
    }

    @Test
    fun `detectSubscriptions ignores a recurring similar-amount food-delivery or installment merchant`() {
        // Regression test: HungerStation/Tabby/Tamara recur with near-identical
        // amounts too (delivery fees, fixed installments) but are food/shopping,
        // not subscriptions — only a merchant CategoryClassifier already put in
        // "اشتراكات" should ever be flagged.
        val allTx = listOf(
            tx(25.0, merchant = "HungerStation", category = "طعام وشراب", timestamp = JAN_2024_START + 1_000L),
            tx(25.0, merchant = "HungerStation", category = "طعام وشراب", timestamp = FEB_MARK),
            tx(25.0, merchant = "HungerStation", category = "طعام وشراب", timestamp = MAR_MARK),
            tx(200.0, merchant = "Tabby", category = "تسوق", timestamp = JAN_2024_START + 2_000L),
            tx(200.0, merchant = "Tabby", category = "تسوق", timestamp = FEB_MARK + 1_000L)
        )
        val s = FinancialAdvisor.summarize(allTx, JAN_2024_START, JAN_2024_END)
        val advice = FinancialAdvisor.advise(s, monthlyBudget = 0.0, allTx = allTx, monthStart = JAN_2024_START, monthEnd = JAN_2024_END)

        assertFalse(advice.any { it.title.contains("اشتراكات") })
    }

    @Test
    fun `summarize excludes flagged transactions from dailyAvg but still counts them in spent`() {
        val txs = listOf(
            tx(100.0, excludeFromDailyAvg = false),
            tx(3000.0, category = "فواتير", excludeFromDailyAvg = true) // e.g. rent
        )
        val s = FinancialAdvisor.summarize(txs, JAN_2024_START, JAN_2024_END)

        assertEquals(3100.0, s.spent, 0.001) // rent still counts toward real spending/budget
        val daysPassed = ((JAN_2024_END.coerceAtMost(System.currentTimeMillis()) - JAN_2024_START) / 86_400_000L).toInt() + 1
        assertEquals(100.0 / daysPassed.coerceAtLeast(1), s.dailyAvg, 0.01) // but not the daily pace
    }

    @Test
    fun `planningIncome prefers manual salary over detected salary over this month's income`() {
        val history = listOf(
            tx(9000.0, TxType.INCOME, timestamp = JAN_2024_START + 1_000L),
            tx(9000.0, TxType.INCOME, timestamp = FEB_MARK)
        )
        val s = FinancialAdvisor.summarize(history, JAN_2024_START, JAN_2024_END)

        assertEquals(12000.0, FinancialAdvisor.planningIncome(s, history, manualSalary = 12000.0)!!, 0.001)
        assertEquals(9000.0, FinancialAdvisor.planningIncome(s, history, manualSalary = 0.0)!!, 0.001)

        val noHistory = listOf(tx(500.0, TxType.INCOME))
        val s2 = FinancialAdvisor.summarize(noHistory, JAN_2024_START, JAN_2024_END)
        assertEquals(500.0, FinancialAdvisor.planningIncome(s2, noHistory, manualSalary = 0.0)!!, 0.001)
    }
}
