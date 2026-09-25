package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.Advice
import com.mizan.money.advisor.AdviceTarget
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.advisor.Level
import com.mizan.money.data.TOTAL_BUDGET

// ============ ADVISOR ============
@Composable
fun AdvisorScreen(
    vm: MainViewModel,
    offset: Int,
    onOpenCategory: (String) -> Unit = {},
    onOpenPlanning: () -> Unit = {},
) {
    val txs by vm.transactions.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val rates by vm.exchangeRates.collectAsState()
    val goals by vm.goals.collectAsState()
    val contributions by vm.goalContributions.collectAsState()
    val debts by vm.debts.collectAsState()
    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val summary = remember(txs, offset, startDay, rates) { FinancialAdvisor.summarize(txs, range.first, range.last, rates) }
    val prevRange = remember(offset, startDay) { Dates.monthRange(offset - 1, startDay) }
    val prevSummary = remember(txs, offset, startDay, rates) {
        FinancialAdvisor.summarize(txs, prevRange.first, prevRange.last, rates)
    }
    val savedThisMonth = remember(contributions, range) {
        contributions.filter { it.timestamp in range.first..range.last }.sumOf { it.amount }
    }
    val monthKey = remember(offset, startDay) { Dates.monthKey(offset, startDay) }
    val manualBudget = remember(budgets, monthKey) {
        budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }?.limitAmount?.takeIf { it > 0 }
    }
    val budget = remember(summary, txs, manualSalary, manualBudget, rates) {
        manualBudget ?: (FinancialAdvisor.planningIncome(summary, txs, manualSalary, rates) ?: 0.0)
    }
    fun severity(level: Level) = when (level) {
        Level.DANGER -> 0
        Level.WARN -> 1
        Level.GOOD -> 2
        Level.INFO -> 3
    }
    val advice = remember(summary, budget, txs, manualSalary, rates, prevSummary, goals, savedThisMonth, debts) {
        FinancialAdvisor.advise(
            summary, budget, txs, range.first, range.last,
            manualSalary = manualSalary, rates = rates, prevSummary = prevSummary,
            goals = goals, savedThisMonth = savedThisMonth, debts = debts
        ).sortedBy { severity(it.level) }
    }
    val alertCount = advice.count { it.level == Level.DANGER || it.level == Level.WARN }
    val budgetPct = if (budget > 0.0) summary.spent / budget else null

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AdviceHealthCard(
                alertCount = alertCount,
                hasDanger = advice.any { it.level == Level.DANGER },
                budgetPct = budgetPct,
                onClick = onOpenPlanning
            )
        }
        if (advice.isEmpty()) {
            item { EmptyState(stringResource(R.string.advisor_empty)) }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusLg))
                        .background(White)
                ) {
                    advice.forEachIndexed { index, a ->
                        val onClick: (() -> Unit)? = when (a.target) {
                            AdviceTarget.CATEGORY -> a.category?.let { c -> { onOpenCategory(c) } }
                            AdviceTarget.BUDGET, AdviceTarget.GOALS -> onOpenPlanning
                            AdviceTarget.NONE -> null
                        }
                        AdviceRow(a, showDivider = index < advice.lastIndex, onClick = onClick)
                    }
                }
            }
        }
    }
}

// One-line health summary: "3 alerts · 72% of budget spent" plus a thin bar.
@Composable
private fun AdviceHealthCard(alertCount: Int, hasDanger: Boolean, budgetPct: Double?, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val alertsText = if (alertCount == 0) stringResource(R.string.insights_health_no_alerts)
        else ctx.resources.getQuantityString(R.plurals.insights_health_alerts, alertCount, insNum(alertCount))
    val budgetText = if (budgetPct != null)
        stringResource(R.string.insights_health_budget_fmt, insNum((budgetPct * 100).toInt()))
        else stringResource(R.string.insights_health_no_budget)
    val tone = when {
        hasDanger || (budgetPct != null && budgetPct >= 1.0) -> 2
        alertCount > 0 || (budgetPct != null && budgetPct >= 0.8) -> 1
        else -> 0
    }
    val color = when (tone) { 2 -> Danger; 1 -> Amber; else -> Success }
    val icon = when (tone) {
        2 -> Icons.Default.ErrorOutline
        1 -> Icons.Default.Warning
        else -> Icons.Default.CheckCircle
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, color, color.copy(alpha = 0.12f), size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.insights_health_join_fmt, alertsText, budgetText),
                style = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (budgetPct != null) {
                Spacer(Modifier.height(8.dp))
                val frac = budgetPct.toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(Pill))
                        .background(PaperOuter)
                ) {
                    if (frac > 0f) {
                        Box(
                            Modifier.fillMaxWidth(frac).fillMaxHeight()
                                .clip(RoundedCornerShape(Pill))
                                .background(if (budgetPct >= 1.0) Danger else if (budgetPct >= 0.8) Amber else Indigo)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun adviceText(resId: Int, args: List<Any>, category: String?): String {
    if (args.isEmpty()) return stringResource(resId)
    // The advisor passes the stored (Arabic) category key as an argument;
    // show it in the UI language instead.
    val shown = if (category == null) args else {
        val display = categoryDisplay(category)
        args.map { if (it == category) display else it }
    }
    return stringResource(resId, *shown.toTypedArray())
}

@Composable
private fun AdviceRow(a: Advice, showDivider: Boolean, onClick: (() -> Unit)?) {
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
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconBadge(icon, color, color.copy(alpha = 0.12f), size = 40.dp, iconSize = 19.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    adviceText(a.titleRes, a.titleArgs, a.category),
                    style = Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    adviceText(a.bodyRes, a.bodyArgs, a.category),
                    style = Body.copy(fontSize = 13.sp, color = InkSoft, lineHeight = 19.sp)
                )
            }
            if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = InkFaint,
                    modifier = Modifier.padding(top = 10.dp).size(20.dp)
                )
            }
        }
        if (showDivider) {
            Box(Modifier.padding(start = 70.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(Line))
        }
    }
}
