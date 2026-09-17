package com.mizan.money.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.CategoryTotal
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import com.mizan.money.data.SELF_TRANSFER_CATEGORY
import com.mizan.money.data.TOTAL_BUDGET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============ INSIGHTS — Advisor / Reports under one bottom-nav slot ============
@Composable
fun InsightsScreen(vm: MainViewModel, offset: Int) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 10.dp)) {
            Text(stringResource(R.string.insights_title), style = H1)
            Spacer(Modifier.height(10.dp))
            TabSwitcher(
                listOf(stringResource(R.string.insights_tab_advice), stringResource(R.string.insights_tab_reports)),
                subTab
            ) { subTab = it }
        }
        Box(Modifier.weight(1f)) {
            when (subTab) {
                0 -> AdvisorScreen(vm, offset)
                else -> ReportsSection(vm, offset)
            }
        }
    }
}

@Composable
private fun ReportsSection(vm: MainViewModel, offset: Int) {
    val txs by vm.transactions.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val rates by vm.exchangeRates.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareChooserTitle = stringResource(R.string.reports_share_chooser)

    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val pdfPeriodLabel = "${monthName(offset, startDay)} (${Dates.monthKey(offset, startDay)})"
    val summary = remember(txs, offset, startDay, rates) { FinancialAdvisor.summarize(txs, range.first, range.last, rates) }
    val monthKey = remember(offset, startDay) { Dates.monthKey(offset, startDay) }
    val manualBudget = remember(budgets, monthKey) {
        budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }?.limitAmount?.takeIf { it > 0 }
    }
    val budget = remember(summary, txs, manualSalary, manualBudget, rates) {
        manualBudget ?: (FinancialAdvisor.planningIncome(summary, txs, manualSalary, rates) ?: 0.0)
    }
    val trend = remember(txs, offset, startDay, rates) {
        (5 downTo 0).map { back ->
            val o = offset - back
            val r = Dates.monthRange(o, startDay)
            val s = FinancialAdvisor.summarize(txs, r.first, r.last, rates)
            Triple(monthShortLabel(o, startDay), s.spent, s.income)
        }
    }
    val prevSummary = remember(txs, offset, startDay, rates) {
        val r = Dates.monthRange(offset - 1, startDay)
        FinancialAdvisor.summarize(txs, r.first, r.last, rates)
    }
    var exporting by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SoftCard {
                Text(stringResource(R.string.reports_trend_title), style = H2)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.reports_trend_subtitle), style = Eyebrow)
                Spacer(Modifier.height(16.dp))
                TrendChart(trend)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    LegendDot(Danger, stringResource(R.string.reports_legend_spend))
                    Spacer(Modifier.width(14.dp))
                    LegendDot(Success, stringResource(R.string.reports_legend_income))
                }
            }
        }
        if (summary.categoryTotals.isNotEmpty()) {
            item {
                SoftCard {
                    Text(
                        stringResource(R.string.reports_categories_title_fmt, monthName(offset, startDay)),
                        style = H2
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryDonut(summary.categoryTotals)
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            summary.categoryTotals.take(5).forEach { c ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(Pill)).background(catColor(c.category)))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        categoryDisplay(c.category),
                                        style = Eyebrow.copy(fontSize = 10.sp),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        stringResource(R.string.reports_percent_fmt, (c.share * 100).toInt()),
                                        style = Eyebrow.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SoftCard {
                Text(stringResource(R.string.reports_comparison_title), style = H2)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    ComparisonColumn(stringResource(R.string.reports_this_month), summary.spent, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(40.dp).background(Line))
                    ComparisonColumn(stringResource(R.string.reports_last_month), prevSummary.spent, Modifier.weight(1f))
                }
                if (prevSummary.spent > 0) {
                    Spacer(Modifier.height(12.dp))
                    val diff = ((summary.spent - prevSummary.spent) / prevSummary.spent) * 100
                    Text(
                        if (diff >= 0) stringResource(R.string.reports_diff_higher_fmt, diff.toInt())
                        else stringResource(R.string.reports_diff_lower_fmt, (-diff).toInt()),
                        style = Eyebrow.copy(fontSize = 11.sp, color = if (diff > 0) Danger else Success, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
        item {
            SoftCard {
                Text(stringResource(R.string.reports_export_title), style = H2)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.reports_export_subtitle), style = Eyebrow)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExportButton(
                        stringResource(R.string.reports_export_csv),
                        Icons.Default.TableChart,
                        Modifier.weight(1f),
                        enabled = !exporting
                    ) {
                        exporting = true
                        scope.launch(Dispatchers.IO) {
                            val monthTxs = txs.filter { it.timestamp in range }
                            val file = writeCsvReport(ctx, monthTxs, monthKey)
                            withContext(Dispatchers.Main) {
                                shareExportFile(ctx, file, "text/csv", shareChooserTitle)
                                exporting = false
                            }
                        }
                    }
                    ExportButton(
                        stringResource(R.string.reports_export_pdf),
                        Icons.Default.PictureAsPdf,
                        Modifier.weight(1f),
                        enabled = !exporting
                    ) {
                        exporting = true
                        val capturedLabel = pdfPeriodLabel
                        val capturedSummary = summary
                        val capturedPrev = prevSummary
                        val capturedBudget = budget
                        val capturedTxs = txs
                        val capturedRange = range
                        val capturedSalary = manualSalary
                        val capturedRates = rates
                        scope.launch(Dispatchers.IO) {
                            val monthTxs = capturedTxs.filter { it.timestamp in capturedRange }
                            val advice = FinancialAdvisor.advise(
                                capturedSummary, capturedBudget, capturedTxs,
                                capturedRange.first, capturedRange.last,
                                manualSalary = capturedSalary, rates = capturedRates
                            )
                            val resolvedAdvice = advice.map {
                                PdfAdvice(
                                    title = if (it.titleArgs.isEmpty()) ctx.getString(it.titleRes)
                                            else ctx.getString(it.titleRes, *it.titleArgs.toTypedArray()),
                                    body = if (it.bodyArgs.isEmpty()) ctx.getString(it.bodyRes)
                                           else ctx.getString(it.bodyRes, *it.bodyArgs.toTypedArray()),
                                    level = when (it.level) {
                                        com.mizan.money.advisor.Level.DANGER -> "danger"
                                        com.mizan.money.advisor.Level.WARN -> "warn"
                                        com.mizan.money.advisor.Level.GOOD -> "good"
                                        else -> "info"
                                    }
                                )
                            }
                            val categoryMap = mapOf(
                                "طعام وشراب" to ctx.getString(R.string.cat_food),
                                "بقالة" to ctx.getString(R.string.cat_groceries),
                                "مواصلات" to ctx.getString(R.string.cat_transport),
                                "وقود" to ctx.getString(R.string.cat_fuel),
                                "تسوق" to ctx.getString(R.string.cat_shopping),
                                "فواتير" to ctx.getString(R.string.cat_bills),
                                "اتصالات" to ctx.getString(R.string.cat_telecom),
                                "صحة" to ctx.getString(R.string.cat_health),
                                "ترفيه" to ctx.getString(R.string.cat_entertainment),
                                "اشتراكات" to ctx.getString(R.string.cat_subscriptions),
                                "تعليم" to ctx.getString(R.string.cat_education),
                                "تحويلات" to ctx.getString(R.string.cat_transfers),
                                CASH_WITHDRAWAL_CATEGORY to ctx.getString(R.string.cat_cash),
                                SELF_TRANSFER_CATEGORY to ctx.getString(R.string.cat_self_transfer),
                                "أخرى" to ctx.getString(R.string.cat_other),
                            )
                            val labels = PdfLabels(
                                reportTitle = ctx.getString(R.string.pdf_report_title),
                                netLabel = ctx.getString(R.string.pdf_net_label),
                                incomeLabel = ctx.getString(R.string.dash_total_income),
                                spendingLabel = ctx.getString(R.string.dash_total_spend),
                                savingsRateLabel = ctx.getString(R.string.pdf_savings_rate),
                                budgetUsageLabel = ctx.getString(R.string.pdf_budget_usage),
                                budgetSpentOf = ctx.getString(R.string.pdf_budget_spent_of),
                                compareLabel = ctx.getString(R.string.reports_comparison_title),
                                thisMonthLabel = ctx.getString(R.string.reports_this_month),
                                lastMonthLabel = ctx.getString(R.string.reports_last_month),
                                higherFormat = ctx.getString(R.string.pdf_higher_fmt),
                                lowerFormat = ctx.getString(R.string.pdf_lower_fmt),
                                insightsTitle = ctx.getString(R.string.pdf_insights_title),
                                categoriesTitle = ctx.getString(R.string.pdf_categories_title),
                                noLimitLabel = ctx.getString(R.string.pdf_no_limit),
                                overLimitFormat = ctx.getString(R.string.pdf_over_limit_fmt),
                                remainingFormat = ctx.getString(R.string.pdf_remaining_fmt),
                                topTransactionsTitle = ctx.getString(R.string.pdf_top_tx_title),
                                allTransactionsTitle = ctx.getString(R.string.pdf_all_tx_title),
                                pageLabelFormat = ctx.getString(R.string.pdf_page_fmt),
                                generatedBy = ctx.getString(R.string.pdf_generated_by),
                                currencyLabel = ctx.getString(R.string.currency_sar),
                            )
                            val file = writePdfReport(
                                ctx = ctx,
                                monthLabel = capturedLabel,
                                summary = capturedSummary,
                                prevSummary = capturedPrev,
                                budget = capturedBudget,
                                transactions = monthTxs,
                                advice = resolvedAdvice,
                                categoryNames = categoryMap,
                                labels = labels,
                            )
                            withContext(Dispatchers.Main) {
                                shareExportFile(ctx, file, "application/pdf", shareChooserTitle)
                                exporting = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<Triple<String, Double, Double>>) {
    val maxVal = (points.maxOfOrNull { maxOf(it.second, it.third) } ?: 0.0).coerceAtLeast(1.0)
    val dangerColor = Danger
    val successColor = Success
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val groupWidth = size.width / points.size
            val barWidth = groupWidth / 3.5f
            points.forEachIndexed { i, (_, spent, income) ->
                val groupX = i * groupWidth
                val spentH = (spent / maxVal * size.height).toFloat()
                val incomeH = (income / maxVal * size.height).toFloat()
                drawRect(
                    color = dangerColor,
                    topLeft = Offset(groupX + barWidth * 0.5f, size.height - spentH),
                    size = Size(barWidth, spentH)
                )
                drawRect(
                    color = successColor,
                    topLeft = Offset(groupX + barWidth * 1.9f, size.height - incomeH),
                    size = Size(barWidth, incomeH)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { (label, _, _) -> Text(label, style = Eyebrow.copy(fontSize = 9.sp)) }
        }
    }
}

@Composable
private fun CategoryDonut(categories: List<CategoryTotal>) {
    val total = categories.sumOf { it.amount }.coerceAtLeast(0.01)
    val sliceColors = categories.map { catColor(it.category) }
    Canvas(Modifier.size(112.dp)) {
        var startAngle = -90f
        categories.forEachIndexed { index, c ->
            val sweep = (c.amount / total * 360.0).toFloat().coerceAtLeast(1f)
            drawArc(
                color = sliceColors[index],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 20f, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun ComparisonColumn(label: String, amount: Double, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Eyebrow)
        Spacer(Modifier.height(6.dp))
        Text("${FinancialAdvisor.fmt(amount)} ر.س", style = NumBold.copy(fontSize = 16.sp))
    }
}

@Composable
private fun ExportButton(label: String, icon: ImageVector, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(RadiusMd))
            .background(if (enabled) Ink900 else PaperOuter)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (enabled) Lime else InkFaint)
        Spacer(Modifier.width(8.dp))
        Text(label, style = Body.copy(color = if (enabled) Lime else InkFaint, fontWeight = FontWeight.Bold, fontSize = 13.sp))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(Pill)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Eyebrow.copy(fontSize = 10.sp))
    }
}

private fun monthShortLabel(offset: Int, startDay: Int): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = Dates.monthRange(offset, startDay).first }
    val names = listOf("ينا", "فبر", "مار", "أبر", "ماي", "يون", "يول", "أغس", "سبت", "أكت", "نوف", "ديس")
    return names[c.get(java.util.Calendar.MONTH)]
}
