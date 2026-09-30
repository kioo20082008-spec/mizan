package com.mizan.money.advisor

import com.mizan.money.data.SinkingFundEntity
import kotlin.math.floor

// Yearly expenses (car insurance, zakat, travel...) split over 12 months so the
// big bill never arrives as a surprise. Pure logic, no Android dependencies.
object SinkingFunds {
    private const val MONTH_MS = 30L * 86_400_000L

    fun monthlyShare(f: SinkingFundEntity): Double =
        if (f.yearlyAmount > 0.0) f.yearlyAmount / 12.0 else 0.0

    // Full months since the fund was created, counting the current one, so the
    // very first month already reserves a share. Capped at a full year.
    fun monthsReserved(f: SinkingFundEntity, now: Long = System.currentTimeMillis()): Int {
        val elapsed = floor((now - f.createdAt).coerceAtLeast(0L).toDouble() / MONTH_MS).toInt()
        return (elapsed + 1).coerceIn(1, 12)
    }

    fun reservedSoFar(f: SinkingFundEntity, now: Long = System.currentTimeMillis()): Double =
        (monthlyShare(f) * monthsReserved(f, now)).coerceAtMost(f.yearlyAmount)

    fun totalMonthly(funds: List<SinkingFundEntity>): Double =
        funds.filter { !it.isArchived }.sumOf { monthlyShare(it) }

    fun commitments(funds: List<SinkingFundEntity>): List<Commitment> =
        funds.filter { !it.isArchived && it.yearlyAmount > 0.0 }
            .map { Commitment(it.name, monthlyShare(it), CommitmentKind.SAVINGS) }
}
