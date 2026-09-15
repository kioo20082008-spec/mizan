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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.advisor.MonthSummary
import kotlin.math.abs
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

    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val summary = remember(txs, offset, startDay) { FinancialAdvisor.summarize(txs, range.first, range.last) }
    // "استهلاك ميزانية الشهر" is driven by what you actually earned this month
    // (or your configured/detected salary before payday), not a manually-typed
    // budget ceiling — so it never needs separate upkeep to stay meaningful.
    val planningIncome = remember(summary, txs, manualSalary) {
        FinancialAdvisor.planningIncome(summary, txs, manualSalary) ?: 0.0
    }
    // "أحدث العمليات" must reflect the month being browsed — otherwise paging to
    // an older month still shows today's latest transactions as if they belonged
    // to it, while every other card on this screen (balance, budget, categories)
    // correctly updates.
    val monthTxs = remember(txs, range) { txs.filter { it.timestamp in range } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            BalanceCard(summary, offset, startDay, planningIncome, onPrev = { onOffsetChange(offset - 1) }, onNext = { if (offset < 0) onOffsetChange(offset + 1) })
        }

        if (planningIncome > 0) {
            item { BudgetStatusCard(planningIncome, summary.spent, title = "استهلاك دخل الشهر", capLabel = "الدخل") }
        }

        if (summary.categoryTotals.isNotEmpty()) {
            item {
                Text("الأكثر استهلاكاً", style = H2, modifier = Modifier.padding(horizontal = 4.dp))
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
                    Text("أحدث العمليات", style = H2, modifier = Modifier.weight(1f))
                    Text(
                        "عرض الكل",
                        style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold),
                        modifier = Modifier.clickable { onNavigateToTransactions(null) }.padding(8.dp)
                    )
                }
            }
            items(monthTxs.take(3)) { tx ->
                TransactionCard(tx, onClick = { onNavigateToTransactions(null) })
            }
        } else if (txs.isNotEmpty()) {
            item { EmptyState("لا توجد عمليات في هذا الشهر") }
        } else {
            item { EmptyState("لم نرصد عمليات بعد") }
        }
    }
}

@Composable
private fun BalanceCard(s: MonthSummary, offset: Int, startDay: Int, budget: Double, onPrev: () -> Unit, onNext: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusXl))
            .background(Brush.linearGradient(listOf(Ink800, Ink900)))
    ) {
        Box(
            Modifier.size(240.dp).align(Alignment.TopEnd).offset(x = 80.dp, y = (-100).dp)
                .clip(CircleShape).background(Indigo.copy(alpha = 0.35f))
        )
        Box(
            Modifier.size(140.dp).align(Alignment.BottomStart).offset(x = (-50).dp, y = 40.dp)
                .clip(CircleShape).background(Lime.copy(alpha = 0.10f))
        )
        Column(Modifier.padding(26.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "الشهر السابق", tint = White, modifier = Modifier.size(16.dp))
                }
                Text(
                    monthName(offset, startDay),
                    style = Body.copy(color = White, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(Pill))
                        .background(White.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                )
                IconButton(onClick = onNext, enabled = offset < 0, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, "الشهر التالي",
                        tint = White.copy(alpha = if (offset < 0) 1f else 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(Pill)).background(Lime.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shield, null, Modifier.size(12.dp), tint = Lime)
                    Spacer(Modifier.width(4.dp))
                    Text("محلي 100٪", style = Eyebrow.copy(color = Lime, fontSize = 11.sp))
                }
            }

            Spacer(Modifier.height(24.dp))
            // "تجاوزت ميزانيتك" (exceeded your budget) must actually compare spend
            // to your (income-based) budget. Once that budget is known, compare
            // against IT — not against s.net, which would otherwise false-alarm
            // before payday (income hasn't posted yet this month even though
            // spending against the *expected* income is perfectly fine). The
            // net-based fallback only applies when there's no income signal at all.
            val isOverBudget = budget > 0 && s.spent > budget
            val isNegativeNet = budget <= 0 && s.net < 0
            val isPastMonth = offset < 0
            val label = when {
                isOverBudget -> "تجاوزت ميزانيتك هذا الشهر"
                isNegativeNet -> "صرفت أكثر مما دخلت هذا الشهر"
                isPastMonth -> "صافي الشهر"
                else -> "الرصيد المتبقي المتاح"
            }
            val isDanger = isOverBudget || isNegativeNet
            Text(label, style = Body.copy(color = OnInkSoft))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    (if (isNegativeNet) "-" else "") + FinancialAdvisor.fmt(abs(s.net)),
                    style = Display.copy(color = if (isDanger) Danger else Lime)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    currencyLabel("SAR"), style = Body.copy(color = OnInkSoft, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(White.copy(alpha = 0.08f)))
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.ArrowDownward, Lime, White.copy(alpha = 0.08f), size = 38.dp, iconSize = 18.dp, radius = RadiusSm)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("إجمالي الدخل", style = Eyebrow.copy(color = OnInkSoft, fontSize = 11.sp))
                        Text(FinancialAdvisor.fmt(s.income), style = Body.copy(color = White, fontWeight = FontWeight.Bold))
                    }
                }
                Box(Modifier.width(1.dp).height(32.dp).background(White.copy(alpha = 0.08f)))
                Spacer(Modifier.width(16.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.ArrowUpward, Danger, White.copy(alpha = 0.08f), size = 38.dp, iconSize = 18.dp, radius = RadiusSm)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("إجمالي الصرف", style = Eyebrow.copy(color = OnInkSoft, fontSize = 11.sp))
                        Text(FinancialAdvisor.fmt(s.spent), style = Body.copy(color = White, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetStatusCard(budget: Double, spent: Double, title: String = "استهلاك ميزانية الشهر", capLabel: String = "السقف") {
    val pct = (spent / budget).coerceIn(0.0, 1.0).toFloat()
    val anim by animateFloatAsState(pct, tween(800), label = "b")
    val overBudget = spent > budget

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = H2, modifier = Modifier.weight(1f))
            Text(
                "${(spent / budget * 100).roundToInt()}٪",
                style = Body.copy(color = if (overBudget) Danger else Indigo, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(if (overBudget) Danger.copy(alpha = 0.1f) else IndigoSoft)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(Pill))
                .background(PaperOuter)
        ) {
            Box(
                Modifier.fillMaxWidth(anim).fillMaxHeight()
                    .clip(RoundedCornerShape(Pill))
                    .background(
                        Brush.horizontalGradient(
                            if (overBudget) listOf(Danger, Danger) else listOf(Indigo, IndigoDeep)
                        )
                    )
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("صرفت: ${FinancialAdvisor.fmt(spent)} ${currencyLabel("SAR")}", style = BodyMuted.copy(fontSize = 12.sp))
            Spacer(Modifier.weight(1f))
            Text("$capLabel: ${FinancialAdvisor.fmt(budget)} ${currencyLabel("SAR")}", style = BodyMuted.copy(fontSize = 12.sp))
        }
    }
}

@Composable
private fun CategoryChip(cat: com.mizan.money.advisor.CategoryTotal, onClick: () -> Unit) {
    Column(
        Modifier.width(136.dp)
            .clip(RoundedCornerShape(RadiusMd))
            .background(White)
            .border(1.dp, Line, RoundedCornerShape(RadiusMd))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        IconBadge(catIcon(cat.category), catColor(cat.category), catColorSoft(cat.category), size = 40.dp, iconSize = 18.dp)
        Spacer(Modifier.height(10.dp))
        Text(cat.category, style = H2.copy(fontSize = 13.sp), maxLines = 1)
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
