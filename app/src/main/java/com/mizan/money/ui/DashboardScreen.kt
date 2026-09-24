package com.mizan.money.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.advisor.MonthSummary
import com.mizan.money.data.RecurringItemEntity
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TOTAL_BUDGET
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

// ============ DASHBOARD ============
@Composable
fun DashboardScreen(
    vm: MainViewModel,
    offset: Int,
    onOffsetChange: (Int) -> Unit,
    onNavigateToTransactions: (String?) -> Unit
) {
    val txs by vm.transactions.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val rates by vm.exchangeRates.collectAsState()
    val recurringItems by vm.recurringItems.collectAsState()
    val categories by vm.categories.collectAsState()
    val upcomingBills = remember(recurringItems) { upcomingBillsWithinDays(recurringItems, 5) }
    var selectedTx by remember { mutableStateOf<TransactionEntity?>(null) }

    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val summary = remember(txs, offset, startDay, rates) { FinancialAdvisor.summarize(txs, range.first, range.last, rates) }
    val monthKey = remember(offset, startDay) { Dates.monthKey(offset, startDay) }
    val manualBudget = remember(budgets, monthKey) {
        budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }?.limitAmount?.takeIf { it > 0 }
    }
    // Same budget the home-screen widget uses, so the app and the widget always
    // show the same "left to spend" number.
    val planningIncome = remember(summary, txs, manualSalary, manualBudget, rates) {
        manualBudget ?: (FinancialAdvisor.planningIncome(summary, txs, manualSalary, rates) ?: 0.0)
    }
    val monthTxs = remember(txs, range) { txs.filter { it.timestamp in range } }
    val goPrev: () -> Unit = { onOffsetChange(offset - 1) }
    val goNext: () -> Unit = { if (offset < 0) onOffsetChange(offset + 1) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { MonthHeader(offset, startDay, range, onPrev = goPrev, onNext = goNext) }

        item {
            HeroCard(
                s = summary,
                offset = offset,
                range = range,
                budget = planningIncome,
                isManualBudget = manualBudget != null,
                onSwipePrev = goPrev,
                onSwipeNext = goNext
            )
        }

        if (upcomingBills.isNotEmpty()) {
            item {
                Text(stringResource(R.string.dash_upcoming_bills), style = H2, modifier = Modifier.padding(horizontal = 4.dp))
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(upcomingBills) { bill -> UpcomingBillChip(bill) }
                }
            }
        }

        if (summary.categoryTotals.isNotEmpty()) {
            item {
                Text(stringResource(R.string.dash_top_spending), style = H2, modifier = Modifier.padding(horizontal = 4.dp))
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(summary.categoryTotals.take(6)) { cat ->
                        CategoryChip(cat, onClick = { onNavigateToTransactions(cat.category) })
                    }
                }
            }
        }

        if (monthTxs.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.dash_recent), style = H2, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.dash_view_all),
                        style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold),
                        modifier = Modifier.clip(RoundedCornerShape(RadiusSm))
                            .clickable { onNavigateToTransactions(null) }
                            .padding(horizontal = 10.dp, vertical = 12.dp)
                    )
                }
            }
            item {
                val recent = monthTxs.take(3)
                Column {
                    recent.forEachIndexed { i, tx ->
                        TransactionCard(tx, position = rowPos(i, recent.size), onClick = { selectedTx = tx })
                    }
                }
            }
        } else if (txs.isNotEmpty()) {
            item { EmptyState(stringResource(R.string.dash_empty_month)) }
        } else {
            item { EmptyState(stringResource(R.string.dash_empty_ever)) }
        }
    }

    selectedTx?.let { current ->
        TxDetailDialog(
            tx = current,
            categories = categories,
            recurringItems = recurringItems,
            onDismiss = { selectedTx = null },
            onDelete = { vm.delete(current); selectedTx = null },
            onSave = { updated, billReminder ->
                vm.update(updated); vm.setBillReminder(updated, billReminder); selectedTx = null
            }
        )
    }
}

private fun daysLeftIn(range: LongRange): Int =
    ceil((range.last + 1 - System.currentTimeMillis()).toDouble() / 86_400_000.0).toInt().coerceAtLeast(1)

@Composable
private fun MonthHeader(offset: Int, startDay: Int, range: LongRange, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.dash_month_prev),
                tint = Ink, modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(monthName(offset, startDay), style = H2)
            if (offset == 0) {
                val days = daysLeftIn(range)
                Text(
                    if (days <= 1) stringResource(R.string.widget_days_last)
                    else stringResource(R.string.widget_left_short_fmt, days),
                    style = Eyebrow.copy(color = InkSoft)
                )
            }
        }
        IconButton(onClick = onNext, enabled = offset < 0, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                stringResource(R.string.dash_month_next),
                tint = if (offset < 0) Ink else Ink.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// The one number that matters: what's left to spend this cycle. A plain white
// One UI card — the number carries the design. Swipe sideways to change month.
@Composable
private fun HeroCard(
    s: MonthSummary,
    offset: Int,
    range: LongRange,
    budget: Double,
    isManualBudget: Boolean,
    onSwipePrev: () -> Unit,
    onSwipeNext: () -> Unit
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currency = currencyLabel("SAR")
    val hasBudget = budget > 0
    val remaining = budget - s.spent
    val over = hasBudget && remaining < 0
    val pct = if (hasBudget) (s.spent / budget).toFloat() else 0f
    val anim by animateFloatAsState(pct.coerceIn(0f, 1f), tween(800), label = "hero")
    var drag by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusXl))
            .background(White)
            .pointerInput(rtl, offset) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragCancel = { drag = 0f },
                    onDragEnd = {
                        if (abs(drag) > 60.dp.toPx()) {
                            // In RTL the newer month sits to the left, so a
                            // rightward drag brings it in (and vice versa in LTR).
                            val towardNewer = if (rtl) drag > 0 else drag < 0
                            if (towardNewer) onSwipeNext() else onSwipePrev()
                        }
                        drag = 0f
                    },
                    onHorizontalDrag = { _, dx -> drag += dx }
                )
            }
            .padding(24.dp)
    ) {
        val labelRes = when {
            !hasBudget && s.net < 0 -> R.string.dash_label_negative_net
            !hasBudget && offset < 0 -> R.string.dash_label_past_month
            !hasBudget -> R.string.dash_label_available
            over -> R.string.dash_label_over_budget
            isManualBudget -> R.string.dash_label_remaining
            else -> R.string.dash_label_remaining_income
        }
        val amount = if (hasBudget) remaining else s.net
        val danger = amount < 0

        Text(stringResource(labelRes), style = Body.copy(color = InkSoft))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                (if (amount < 0) "-" else "") + FinancialAdvisor.fmt(abs(amount)),
                style = Display.copy(color = if (danger) Danger else Ink),
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Text(
                currency,
                style = Body.copy(color = InkSoft, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        if (hasBudget && !over && offset == 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    R.string.dash_daily_allowance_fmt,
                    FinancialAdvisor.fmt(remaining / daysLeftIn(range)),
                    currency
                ),
                style = Body.copy(color = Ink)
            )
        }

        Spacer(Modifier.height(20.dp))
        if (hasBudget) {
            Box(
                Modifier.fillMaxWidth().height(8.dp)
                    .clip(RoundedCornerShape(Pill))
                    .background(PaperOuter)
            ) {
                Box(
                    Modifier.fillMaxWidth(anim).fillMaxHeight()
                        .clip(RoundedCornerShape(Pill))
                        .background(if (over) Danger else if (pct >= 0.85f) Amber else Indigo)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dash_used_of_fmt, FinancialAdvisor.fmt(s.spent), FinancialAdvisor.fmt(budget), currency),
                    style = Body.copy(color = InkSoft, fontSize = 13.sp),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(pct * 100).roundToInt()}%",
                    style = Body.copy(color = if (over) Danger else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeroStat(Icons.Default.ArrowDownward, Success, stringResource(R.string.dash_total_income), FinancialAdvisor.fmt(s.income), Modifier.weight(1f))
                HeroStat(Icons.Default.ArrowUpward, Danger, stringResource(R.string.dash_total_spend), FinancialAdvisor.fmt(s.spent), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(icon: ImageVector, tint: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, tint.copy(alpha = 0.12f), size = 38.dp, iconSize = 18.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = Eyebrow.copy(color = InkSoft))
            Text(value, style = Body.copy(color = Ink, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun CategoryChip(cat: com.mizan.money.advisor.CategoryTotal, onClick: () -> Unit) {
    Column(
        Modifier.width(136.dp)
            .clip(RoundedCornerShape(RadiusMd))
            .background(White)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        IconBadge(catIcon(cat.category), catColor(cat.category), catColorSoft(cat.category), size = 40.dp, iconSize = 18.dp)
        Spacer(Modifier.height(10.dp))
        Text(categoryDisplay(cat.category), style = H2.copy(fontSize = 13.sp), maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(FinancialAdvisor.fmt(cat.amount) + " " + currencyLabel("SAR"), style = NumBold.copy(fontSize = 12.sp))
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(Pill)).background(PaperOuter)
        ) {
            Box(
                Modifier.fillMaxWidth(cat.share.toFloat()).fillMaxHeight()
                    .clip(RoundedCornerShape(Pill)).background(catColor(cat.category))
            )
        }
    }
}

// ============ UPCOMING BILLS ============
private data class UpcomingBill(val item: RecurringItemEntity, val daysUntil: Int)

private fun upcomingBillsWithinDays(items: List<RecurringItemEntity>, window: Int): List<UpcomingBill> {
    val today = Calendar.getInstance()
    val todayDay = today.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
    return items.filter { it.reminderEnabled }.mapNotNull { item ->
        val daysUntil = if (item.expectedDayOfMonth >= todayDay) {
            item.expectedDayOfMonth - todayDay
        } else {
            (daysInMonth - todayDay) + item.expectedDayOfMonth
        }
        if (daysUntil <= window) UpcomingBill(item, daysUntil) else null
    }.sortedBy { it.daysUntil }
}

@Composable
private fun UpcomingBillChip(bill: UpcomingBill) {
    Column(
        Modifier.width(136.dp)
            .clip(RoundedCornerShape(RadiusMd))
            .background(White)
            .padding(14.dp)
    ) {
        IconBadge(catIcon(bill.item.category), Amber, Amber.copy(alpha = 0.12f), size = 40.dp, iconSize = 18.dp)
        Spacer(Modifier.height(10.dp))
        Text(bill.item.merchant, style = H2.copy(fontSize = 13.sp), maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text("~${FinancialAdvisor.fmt(bill.item.expectedAmount)} ${currencyLabel("SAR")}", style = NumBold.copy(fontSize = 12.sp))
        Spacer(Modifier.height(6.dp))
        Text(
            if (bill.daysUntil == 0) stringResource(R.string.dash_today)
            else stringResource(R.string.dash_in_days, bill.daysUntil),
            style = Eyebrow.copy(
                fontSize = 12.sp,
                color = if (bill.daysUntil <= 1) Danger else InkFaint,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
