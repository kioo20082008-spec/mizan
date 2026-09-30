package com.mizan.money.advisor

import com.mizan.money.data.SinkingFundEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SinkingFundsTest {
    private val day = 86_400_000L
    private val created = 1_700_000_000_000L
    private fun fund(y: Double = 1200.0) = SinkingFundEntity(name = "تأمين", yearlyAmount = y, createdAt = created)

    @Test fun monthlyShareIsYearlyOverTwelve() = assertEquals(100.0, SinkingFunds.monthlyShare(fund()), 0.001)

    @Test fun firstMonthAlreadyReservesOneShare() =
        assertEquals(100.0, SinkingFunds.reservedSoFar(fund(), created + day), 0.001)

    @Test fun reservationGrowsThenCapsAtYearly() {
        assertEquals(300.0, SinkingFunds.reservedSoFar(fund(), created + 65 * day), 0.001)
        assertEquals(1200.0, SinkingFunds.reservedSoFar(fund(), created + 900 * day), 0.001)
    }

    @Test fun planDeductsFundsFromFreeIncome() {
        val plan = FinancialAdvisor.plan(
            allTx = emptyList(), income = 5000.0, categories = emptyList(),
            fixedItems = emptyList(), debts = emptyList(), goals = emptyList(),
            funds = listOf(fund())
        )
        assertEquals(100.0, plan.savingsTotal, 0.001)
        assertEquals(4900.0, plan.free, 0.001)
    }
}
