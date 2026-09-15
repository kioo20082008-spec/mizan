package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.advisor.Advice
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.advisor.Level
import com.mizan.money.data.TOTAL_BUDGET

// ============ ADVISOR ============
@Composable
fun AdvisorScreen(vm: MainViewModel, offset: Int) {
    val txs by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val summary = remember(txs, offset, startDay) { FinancialAdvisor.summarize(txs, range.first, range.last) }
    val budget = budgets.firstOrNull {
        it.monthKey == Dates.monthKey(offset, startDay) && it.category == TOTAL_BUDGET
    }?.limitAmount ?: 0.0
    // DANGER-level advice (over budget, spending more than you earn) is the most
    // urgent thing on this screen and must never be buried below WARN/GOOD/INFO
    // cards that simply happened to be generated earlier in FinancialAdvisor's
    // fixed pipeline order. Level's own declaration order isn't severity order,
    // so this maps it explicitly instead of sorting by ordinal.
    fun severity(level: Level) = when (level) {
        Level.DANGER -> 0
        Level.WARN -> 1
        Level.GOOD -> 2
        Level.INFO -> 3
    }
    val advice = remember(summary, budget, txs, manualSalary) {
        FinancialAdvisor.advise(summary, budget, txs, range.first, range.last, manualSalary = manualSalary)
            .sortedBy { severity(it.level) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusXl))
                    .background(Brush.linearGradient(listOf(Ink800, Ink900)))
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Lightbulb, Lime, White.copy(alpha = 0.08f), size = 56.dp, iconSize = 28.dp, radius = RadiusMd)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("المستشار المالي", style = H2.copy(color = White, fontSize = 17.sp))
                        Spacer(Modifier.height(4.dp))
                        Text("تحليل ذكي ومحلي لنمط إنفاقك", style = BodyMuted.copy(color = OnInkSoft))
                    }
                }
            }
        }
        if (advice.isEmpty()) {
            item { EmptyState("لا توجد نصائح بعد — أضف عمليات أو ميزانية لهذا الشهر") }
        } else {
            items(advice, key = { it.title }) { a -> AdviceRow(a) }
        }
    }
}

@Composable
private fun AdviceRow(a: Advice) {
    val color = when (a.level) {
        Level.DANGER -> Danger
        Level.WARN   -> Amber
        Level.GOOD   -> Success
        Level.INFO   -> Indigo
    }
    val icon = when (a.level) {
        Level.DANGER -> Icons.Default.ErrorOutline
        Level.WARN   -> Icons.Default.Warning
        Level.GOOD   -> Icons.Default.CheckCircle
        Level.INFO   -> Icons.Default.Lightbulb
    }
    SoftCard {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier.width(4.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(Pill))
                    .background(color)
            )
            Spacer(Modifier.width(14.dp))
            IconBadge(icon, color, color.copy(alpha = 0.12f), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            // Without weight(1f) this Column is measured against the Row's full
            // width instead of what's left after the stripe/spacer/badge ahead of
            // it, so a long advice body (e.g. the subscriptions list) overflows
            // past the card's edge instead of wrapping.
            Column(Modifier.weight(1f)) {
                Text(a.title, style = H2.copy(fontSize = 14.sp))
                Spacer(Modifier.height(6.dp))
                Text(a.body, style = BodyMuted)
            }
        }
    }
}
