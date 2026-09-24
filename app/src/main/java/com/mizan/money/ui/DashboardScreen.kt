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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import com.mizan.money.data.TxType
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
    onNavigateToTransactions: (String?) -> Unit,
    onOpenPlanning: () -> Unit = {},
    onAddTransaction: () -> Unit = {}
) {
    val txs by vm.transactions.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val rates by vm.exchangeRates.collectAsState()
    val recurringItems by vm.recurringItems.collectAsState()
    val categories by vm.categories.collectAsState()
    val upcomingBills = remember(recurringItems, txs, startDay) {
        upcomingBillsWithinDays(recurringItems, txs, Dates.monthRange(0, startDay), 5)
    }
    var selectedTx by remember { mutableStateOf<TransactionEntity?>(null) }

    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val summary = remember(txs, offset, startDay, rates) { FinancialAdvisor.summarize(txs, range.first, range.last, rates) }
    val todaySpent = remember(txs, rates) {
        val dayStart = startOfToday()
        FinancialAdvisor.summarize(txs, dayStart, dayStart + 86_400_000L - 1, rates).spent.coerceAtLeast(0.0)
    }
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
        item {
            HeroCard(
                s = summary,
                offset = offset,
                startDay = startDay,
                range = range,
                budget = planningIncome,
                isManualBudget = manualBudget != null,
                todaySpent = todaySpent,
                onPrev = goPrev,
                onNext = goNext,
                onSetBudget = onOpenPlanning
            )
        }

        if (upcomingBills.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.dash_upcoming_bills)) {
                    upcomingBills.take(4).forEachIndexed { i, bill ->
                        val urgent = bill.daysUntil <= 1
                        val whenText = if (bill.daysUntil == 0) stringResource(R.string.dash_today)
                            else stringResource(R.string.dash_in_days, bill.daysUntil)
                        ListRow(
                            icon = catIcon(bill.item.category),
                            iconTint = catColor(bill.item.category),
                            title = bill.item.merchant,
                            subtitle = categoryDisplay(bill.item.category),
                            trailing = fmt(bill.item.expectedAmount),
                            // Amount is an estimate from past charges, so say so
                            // in words instead of a cryptic leading "~".
                            trailingSub = stringResource(R.string.home_bill_expected_fmt, whenText),
                            trailingSubColor = if (urgent) Danger else InkSoft,
                            showDivider = i < minOf(upcomingBills.size, 4) - 1
                        )
                    }
                }
            }
        }

        if (summary.categoryTotals.isNotEmpty()) {
            item {
                val top = summary.categoryTotals.take(5)
                SectionCard(
                    stringResource(R.string.dash_top_spending),
                    actionLabel = stringResource(R.string.dash_view_all),
                    onAction = { onNavigateToTransactions(null) }
                ) {
                    top.forEachIndexed { i, cat ->
                        val color = catColor(cat.category)
                        ListRow(
                            icon = catIcon(cat.category),
                            iconTint = color,
                            title = categoryDisplay(cat.category),
                            trailing = fmt(cat.amount),
                            trailingSub = "${(cat.share * 100).roundToInt()}%",
                            showDivider = i < top.lastIndex,
                            onClick = { onNavigateToTransactions(cat.category) },
                            below = {
                                Box(
                                    Modifier.fillMaxWidth().height(6.dp)
                                        .clip(RoundedCornerShape(Pill)).background(PaperOuter)
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(cat.share.toFloat().coerceIn(0f, 1f)).fillMaxHeight()
                                            .clip(RoundedCornerShape(Pill)).background(color)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        if (monthTxs.isNotEmpty()) {
            item {
                val recent = monthTxs.take(4)
                SectionCard(
                    stringResource(R.string.dash_recent),
                    actionLabel = stringResource(R.string.dash_view_all),
                    onAction = { onNavigateToTransactions(null) }
                ) {
                    recent.forEachIndexed { i, tx ->
                        TransactionCard(
                            tx,
                            position = if (i == recent.lastIndex) RowPos.Last else RowPos.Middle,
                            onClick = { selectedTx = tx }
                        )
                    }
                }
            }
        } else if (txs.isNotEmpty()) {
            item {
                HomeEmptyState(
                    stringResource(R.string.dash_empty_month),
                    ctaLabel = if (offset == 0) stringResource(R.string.home_add_transaction) else null,
                    onCta = onAddTransaction
                )
            }
        } else {
            item {
                HomeEmptyState(
                    stringResource(R.string.dash_empty_ever),
                    ctaLabel = stringResource(R.string.home_add_transaction),
                    onCta = onAddTransaction
                )
            }
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

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun HomeEmptyState(text: String, ctaLabel: String?, onCta: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(Icons.AutoMirrored.Outlined.ReceiptLong, InkFaint, White, size = 72.dp, iconSize = 30.dp)
        Spacer(Modifier.height(14.dp))
        Text(text, style = BodyMuted)
        if (ctaLabel != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onCta,
                shape = RoundedCornerShape(Pill),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Lime),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(ctaLabel, style = Body.copy(color = Lime, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

// Compact month switcher that lives inside the hero card: ‹ month › as one pill.
@Composable
private fun MonthPill(offset: Int, startDay: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Pill)).background(PaperOuter).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                stringResource(R.string.dash_month_prev),
                tint = Ink, modifier = Modifier.size(20.dp)
            )
        }
        Text(
            monthName(offset, startDay),
            style = Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        IconButton(onClick = onNext, enabled = offset < 0, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
    startDay: Int,
    range: LongRange,
    budget: Double,
    isManualBudget: Boolean,
    todaySpent: Double,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSetBudget: () -> Unit
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
                            if (towardNewer) onNext() else onPrev()
                        }
                        drag = 0f
                    },
                    onHorizontalDrag = { _, dx -> drag += dx }
                )
            }
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthPill(offset, startDay, onPrev, onNext)
            Spacer(Modifier.weight(1f))
            if (offset == 0) {
                val days = daysLeftIn(range)
                Text(
                    if (days <= 1) stringResource(R.string.widget_days_last)
                    else stringResource(R.string.widget_left_short_fmt, days),
                    style = Eyebrow.copy(color = InkSoft),
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(18.dp))

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
                (if (amount < 0) "-" else "") + fmt(abs(amount)),
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
                    stringResource(R.string.dash_used_of_fmt, fmt(s.spent), fmt(budget), currency),
                    style = Body.copy(color = InkSoft, fontSize = 13.sp),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(pct * 100).roundToInt()}%",
                    style = Body.copy(color = if (over) Danger else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                )
            }
            if (offset == 0) {
                // Secondary stats: how much can still go out per day, and how
                // much already went out today.
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeroStat(
                        Icons.Default.CalendarToday, Indigo,
                        stringResource(R.string.home_daily_allowance),
                        stringResource(
                            R.string.home_amount_currency_fmt,
                            fmt(if (over) 0.0 else remaining / daysLeftIn(range)),
                            currency
                        ),
                        Modifier.weight(1f),
                        prominent = true
                    )
                    HeroStat(
                        Icons.Default.Payments, Amber,
                        stringResource(R.string.home_spent_today),
                        stringResource(R.string.home_amount_currency_fmt, fmt(todaySpent), currency),
                        Modifier.weight(1f)
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeroStat(Icons.Default.ArrowDownward, Success, stringResource(R.string.dash_total_income), fmt(s.income), Modifier.weight(1f))
                HeroStat(Icons.Default.ArrowUpward, Danger, stringResource(R.string.dash_total_spend), fmt(s.spent), Modifier.weight(1f))
            }
            if (offset == 0) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onSetBudget,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(Pill),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Lime)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.home_set_budget_cta),
                        style = Body.copy(color = Lime, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, tint.copy(alpha = 0.12f), size = 38.dp, iconSize = 18.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = Eyebrow.copy(color = InkSoft), maxLines = 1)
            Text(
                value,
                style = if (prominent) H2.copy(fontSize = 17.sp) else Body.copy(color = Ink, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

// ============ UPCOMING BILLS ============
private data class UpcomingBill(val item: RecurringItemEntity, val daysUntil: Int)

// A reminder's day may be 29-31 (or 0 / 31 meaning "end of month"): clamp it
// to the month's real length so February still gets its reminder.
private fun dueDayIn(expected: Int, monthLength: Int): Int =
    if (expected <= 0 || expected >= 31) monthLength else expected.coerceAtMost(monthLength)

private fun upcomingBillsWithinDays(
    items: List<RecurringItemEntity>,
    txs: List<TransactionEntity>,
    cycle: LongRange,
    window: Int
): List<UpcomingBill> {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val todayDay = today.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
    val nextMonth = (today.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, 1)
    }
    val daysInNextMonth = nextMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    val candidates = txs.filter {
        it.type == TxType.EXPENSE && it.timestamp in cycle && !it.merchant.isNullOrBlank()
    }
    return items.filter { it.reminderEnabled }.mapNotNull { item ->
        val dueThisMonth = dueDayIn(item.expectedDayOfMonth, daysInMonth)
        val daysUntil = if (dueThisMonth >= todayDay) {
            dueThisMonth - todayDay
        } else {
            (daysInMonth - todayDay) + dueDayIn(item.expectedDayOfMonth, daysInNextMonth)
        }
        if (daysUntil > window) return@mapNotNull null
        // Already paid this cycle? Only payments reasonably close to this due
        // date count, so last month's charge doesn't hide next month's bill.
        val dueAt = today.timeInMillis + daysUntil * 86_400_000L
        val earliest = maxOf(cycle.first, dueAt - 25L * 86_400_000L)
        val merchant = item.merchant.trim()
        val paid = merchant.isNotEmpty() && candidates.any { tx ->
            val m = tx.merchant!!.trim()
            tx.timestamp >= earliest &&
                (m.contains(merchant, ignoreCase = true) || merchant.contains(m, ignoreCase = true)) &&
                abs(tx.amount - item.expectedAmount) <= item.expectedAmount * 0.2
        }
        if (paid) null else UpcomingBill(item, daysUntil)
    }.sortedBy { it.daysUntil }
}
