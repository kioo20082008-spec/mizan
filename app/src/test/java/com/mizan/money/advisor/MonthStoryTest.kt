package com.mizan.money.advisor

import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthStoryTest {
    private val day = 86_400_000L
    private val start = 1_700_000_000_000L
    private val range = start until (start + 10 * day)
    private val prevRange = (start - 10 * day) until start

    private fun tx(amount: Double, dayIdx: Int, merchant: String, category: String = "مطاعم", type: TxType = TxType.EXPENSE) =
        TransactionEntity(
            amount = amount, merchant = merchant, category = category, type = type,
            smsHash = "h-$merchant-$dayIdx-$amount", timestamp = start + dayIdx * day + 1_000L
        )

    @Test fun picksTopMerchantPriciestDayAndStreak() {
        val txs = listOf(
            tx(100.0, 1, "A"), tx(50.0, 1, "B"), tx(30.0, 2, "A"),
            tx(20.0, 8, "C"),
        )
        val s = MonthStoryCalculator.compute(txs, range, prevRange, now = start + 20 * day)
        assertEquals("A", s.topMerchant)
        assertEquals(130.0, s.topMerchantAmount, 0.001)
        assertEquals(2, s.topMerchantCount)
        assertEquals(start + 1 * day, s.priciestDayStart)
        assertEquals(150.0, s.priciestDayAmount, 0.001)
        // days 3..7 are empty -> 5-day streak
        assertEquals(5, s.longestStreakDays)
    }

    @Test fun oneOffCategoriesAreIgnored() {
        val txs = listOf(tx(3000.0, 1, "Landlord", "إيجار"), tx(10.0, 2, "Cafe"))
        val s = MonthStoryCalculator.compute(txs, range, prevRange, now = start + 20 * day)
        assertEquals("Cafe", s.topMerchant)
    }

    @Test fun mostImprovedCategoryComparesWithPrevious() {
        val txs = listOf(
            tx(300.0, -3, "X", "مطاعم"), tx(100.0, 2, "X", "مطاعم"),
            tx(200.0, -2, "Y", "تسوق"), tx(250.0, 3, "Y", "تسوق"),
        )
        val s = MonthStoryCalculator.compute(txs, range, prevRange, now = start + 20 * day)
        assertEquals("مطاعم", s.improvedCategory)
        assertEquals(200.0, s.improvedAmount, 0.001)
    }

    @Test fun emptyMonthHasNoHighlightsButFullStreak() {
        val s = MonthStoryCalculator.compute(emptyList(), range, prevRange, now = start + 20 * day)
        assertNull(s.topMerchant)
        assertNull(s.priciestDayStart)
        assertNull(s.improvedCategory)
        assertEquals(10, s.longestStreakDays)
    }
}
