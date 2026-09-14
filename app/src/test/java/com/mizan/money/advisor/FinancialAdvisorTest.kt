package com.mizan.money.advisor

import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val JAN_2024_START = 1_704_067_200_000L // 2024-01-01T00:00:00Z
private const val JAN_2024_END = 1_706_745_599_999L   // 2024-01-31T23:59:59.999Z

private fun tx(
    amount: Double,
    type: TxType = TxType.EXPENSE,
    category: String = "أخرى",
    currency: String = "SAR",
    merchant: String? = "Test",
    timestamp: Long = JAN_2024_START + 1_000L
): TransactionEntity = TransactionEntity(
    amount = amount, currency = currency, merchant = merchant, category = category,
    type = type, rawSms = "", smsHash = "h-${System.nanoTime()}-${(0..999999).random()}",
    timestamp = timestamp
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
}
