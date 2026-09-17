package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.Advice
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.advisor.Level
import com.mizan.money.data.TOTAL_BUDGET

// ============ ADVISOR ============
@Composable
fun AdvisorScreen(vm: MainViewModel, offset: Int) {
    val txs by vm.transactions.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val summary = remember(txs, offset, startDay) { FinancialAdvisor.summarize(txs, range.first, range.last) }
    val monthKey = remember(offset, startDay) { Dates.monthKey(offset, startDay) }
    val manualBudget = remember(budgets, monthKey) {
        budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }?.limitAmount?.takeIf { it > 0 }
    }
    val budget = remember(summary, txs, manualSalary, manualBudget) {
        manualBudget ?: (FinancialAdvisor.planningIncome(summary, txs, manualSalary) ?: 0.0)
    }
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
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusLg))
                    .background(Brush.linearGradient(listOf(Ink800, Ink900)))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Default.Lightbulb, Lime, White.copy(alpha = 0.08f), size = 40.dp, iconSize = 20.dp, radius = RadiusSm)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("المستشار المالي", style = H2.copy(color = White, fontSize = 15.sp))
                    Text("تحليل ذكي ومحلي لنمط إنفاقك", style = Eyebrow.copy(color = OnInkSoft, fontSize = 11.sp))
                }
            }
        }
        if (advice.isEmpty()) {
            item { EmptyState("لا توجد نصائح بعد — أضف عمليات أو ميزانية لهذا الشهر") }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusLg))
                        .background(White)
                        .border(1.dp, Line, RoundedCornerShape(RadiusLg))
                ) {
                    advice.forEachIndexed { index, a ->
                        AdviceRow(a)
                        if (index < advice.lastIndex) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Resolves a string resource with optional format args. Calling
// stringResource(id) directly (rather than with an empty vararg) avoids
// running the String.format path on messages that have no format specifiers.
@Composable
private fun adviceText(resId: Int, args: List<Any>): String =
    if (args.isEmpty()) stringResource(resId)
    else stringResource(resId, *args.toTypedArray())

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
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier.width(3.dp).fillMaxHeight()
                .clip(RoundedCornerShape(Pill))
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        IconBadge(icon, color, color.copy(alpha = 0.12f), size = 36.dp, iconSize = 17.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(adviceText(a.titleRes, a.titleArgs), style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
            Spacer(Modifier.height(4.dp))
            Text(adviceText(a.bodyRes, a.bodyArgs), style = Eyebrow.copy(fontSize = 11.sp, color = InkSoft))
        }
    }
}
