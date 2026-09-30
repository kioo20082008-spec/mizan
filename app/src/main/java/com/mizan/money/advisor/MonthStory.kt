package com.mizan.money.advisor

import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import com.mizan.money.data.ExchangeRates
import com.mizan.money.data.SELF_TRANSFER_CATEGORY
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType

// The "story" of a cycle: a few highlights computed from everyday (discretionary)
// spending only, so rent and bills don't drown out the interesting numbers.
data class MonthStory(
    val topMerchant: String?,
    val topMerchantAmount: Double,
    val topMerchantCount: Int,
    val priciestDayStart: Long?,
    val priciestDayAmount: Double,
    val improvedCategory: String?,
    val improvedAmount: Double,
    val longestStreakDays: Int,
    // First day (epoch ms) of the longest streak, or null when there is none.
    val streakStartMs: Long? = null,
    // Zero-based day index of the streak's first day within the cycle.
    val streakStartIndex: Int = -1,
    val totalSpend: Double = 0.0,
    val dayCount: Int = 0,
    val improvedPrev: Double = 0.0,
    val improvedCur: Double = 0.0,
) {
    val averageDay: Double get() = if (dayCount > 0) totalSpend / dayCount else 0.0
}

object MonthStoryCalculator {
    private const val DAY_MS = 86_400_000L
    private val oneOff = setOf("إيجار", "فواتير", "تحويلات", SELF_TRANSFER_CATEGORY, CASH_WITHDRAWAL_CATEGORY)
    // Ignore tiny categories when hunting for "most improved".
    private const val MIN_PREV_FOR_IMPROVEMENT = 50.0

    private fun everyday(
        txs: List<TransactionEntity>, range: LongRange, rates: Map<String, Double>
    ): List<Pair<TransactionEntity, Double>> = txs.mapNotNull { t ->
        if (t.type != TxType.EXPENSE || t.isSelfTransfer || t.isReimbursement) return@mapNotNull null
        if (t.timestamp !in range || t.category in oneOff) return@mapNotNull null
        val sar = ExchangeRates.toSar(t.amount, t.currency, rates) ?: return@mapNotNull null
        t to sar
    }

    // `range` is the viewed cycle; `prevRange` the cycle to compare with (the
    // caller truncates it to the same pace while the current cycle is running);
    // `now` bounds the streak so days that haven't happened yet don't count.
    fun compute(
        txs: List<TransactionEntity>,
        range: LongRange,
        prevRange: LongRange,
        rates: Map<String, Double> = ExchangeRates.DEFAULT,
        now: Long = System.currentTimeMillis(),
    ): MonthStory {
        val cur = everyday(txs, range, rates)
        val prev = everyday(txs, prevRange, rates)

        val byMerchant = cur.filter { !it.first.merchant.isNullOrBlank() }
            .groupBy { it.first.merchant!!.trim().lowercase() }
        val top = byMerchant.maxByOrNull { (_, l) -> l.sumOf { it.second } }
        val topName = top?.value?.first()?.first?.merchant?.trim()

        val byDay = cur.groupBy { (it.first.timestamp - range.first) / DAY_MS }
        val priciest = byDay.maxByOrNull { (_, l) -> l.sumOf { it.second } }

        val curCat = cur.groupBy { it.first.category }.mapValues { (_, l) -> l.sumOf { it.second } }
        val prevCat = prev.groupBy { it.first.category }.mapValues { (_, l) -> l.sumOf { it.second } }
        val improved = prevCat.filter { it.value >= MIN_PREV_FOR_IMPROVEMENT }
            .map { (c, p) -> c to (p - (curCat[c] ?: 0.0)) }
            .filter { it.second > 0.005 }
            .maxByOrNull { it.second }

        val lastDay = ((minOf(now, range.last) - range.first) / DAY_MS).coerceAtLeast(0L)
        var best = 0
        var bestStart = -1L
        var run = 0
        for (d in 0L..lastDay) {
            if (d in byDay) run = 0 else {
                run++
                if (run > best) { best = run; bestStart = d - run + 1 }
            }
        }

        return MonthStory(
            topMerchant = topName,
            topMerchantAmount = top?.value?.sumOf { it.second } ?: 0.0,
            topMerchantCount = top?.value?.size ?: 0,
            priciestDayStart = priciest?.let { range.first + it.key * DAY_MS },
            priciestDayAmount = priciest?.value?.sumOf { it.second } ?: 0.0,
            improvedCategory = improved?.first,
            improvedAmount = improved?.second ?: 0.0,
            longestStreakDays = best,
            streakStartIndex = if (best > 0) bestStart.toInt() else -1,
            streakStartMs = if (best > 0) range.first + bestStart * DAY_MS else null,
            totalSpend = cur.sumOf { it.second },
            dayCount = (lastDay + 1).toInt(),
            improvedPrev = improved?.let { prevCat[it.first] } ?: 0.0,
            improvedCur = improved?.let { curCat[it.first] ?: 0.0 } ?: 0.0,
        )
    }
}
