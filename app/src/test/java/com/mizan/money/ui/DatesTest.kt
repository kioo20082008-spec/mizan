package com.mizan.money.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

// Dates.monthRange is the backbone of every monthly total (dashboard, budget,
// advisor, reports), and the startDay arithmetic is easy to break around short
// months — exactly the "adding a month to day 30 rolls into March" trap the
// implementation comments call out. These tests pin the invariants.
class DatesTest {

    @Test
    fun `default start day gives plain calendar-month boundaries`() {
        val range = Dates.monthRange(0, 1)
        val start = Calendar.getInstance().apply { timeInMillis = range.first }
        assertEquals(1, start.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, start.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, start.get(Calendar.MINUTE))
    }

    @Test
    fun `consecutive month ranges are contiguous for every start day`() {
        // The key regression guard: range(o).end + 1 must equal range(o+1).start,
        // for startDay=28 too, where naive month addition can skip a month.
        for (startDay in listOf(1, 15, 28)) {
            for (offset in -3..3) {
                val current = Dates.monthRange(offset, startDay)
                val next = Dates.monthRange(offset + 1, startDay)
                assertEquals(
                    "startDay=$startDay offset=$offset",
                    current.last + 1,
                    next.first,
                )
            }
        }
    }

    @Test
    fun `monthKey is unique and well formatted across a year`() {
        val keys = (-6..6).map { Dates.monthKey(it, 1) }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { Regex("""\d{4}-\d{2}""").matches(it) })
    }

    @Test
    fun `range always contains the current instant for the current offset`() {
        val now = System.currentTimeMillis()
        val range = Dates.monthRange(0, 1)
        assertTrue(now in range)
    }

    @Test
    fun `dayOfMonth returns the calendar day of a timestamp`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 29)
        }
        assertEquals(29, Dates.dayOfMonth(cal.timeInMillis))
    }
}
