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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.CategoryTotal
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.TOTAL_BUDGET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

// ============ INSIGHTS ============
@Composable
fun InsightsScreen(vm: MainViewModel, offset: Int) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 10.dp)) {
            Text(stringResource(R.string.insights_title), style = H1)
            Spacer(Modifier.height(10.dp))
            TabSwitcher(
                listOf(
                    stringResource(R.string.insights_tab_advice),
                    stringResource(R.string.insights_tab_reports),
                ),
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

// ============ REPORTS ============
@Composable
private fun ReportsSection(vm: MainViewModel, offset: Int) {
    val txs by vm.transactions.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareChooserTitle = stringResource(R.string.reports_share_chooser)
    // Locale is captured at @Composable scope so the (non-composable)
    // monthShortLabel below can use it from inside remember{} lambdas.
    val locale = LocalConfiguration.current.locales[0]
    // Pre-computed at @Composable scope: the stringResource/mothName getters
    // read from CompositionLocals, which the scope.launch below cannot access.
    val categoryTitle = stringResource(
        R.string.reports_categories_title_fmt,
        monthName(offset, startDay)
    )
    val pdfPeriodLabel = "${monthName(offset, startDay)} (${Dates.monthKey(offset, startDay)})"

    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val summary = remember(txs, offset, startDay) { FinancialAdvisor.summarize(txs, range.first, range.last) }
    val monthKey = remember(offset, startDay) { Dates.monthKey(offset, startDay) }
    val manualBudget = remember(budgets, monthKey) {
        budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }?.limitAmount?.takeIf { it > 0 }
    }
    val budget = remember(summary, txs, manualSalary, manualBudget) {
        manualBudget ?: (FinancialAdvisor.planningIncome(summary, txs, manualSalary) ?: 0.0)
    }
    val trend = remember(txs, offset, startDay, locale) {
        (5 downTo 0).map { back ->
            val o = offset - back
            val r = Dates.monthRange(o, startDay)
            val s = FinancialAdvisor.summarize(txs, r.first, r.last)
            Triple(monthShortLabel(o, startDay, locale), s.spent, s.income)
        }
    }
    val prevSummary = remember(txs, offset, startDay) {
        val r = Dates.monthRange(offset - 1, startDay)
        FinancialAdvisor.summarize(txs, r.first, r.last)
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
                    Text(categoryTitle, style = H2)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryDonut(summary.categoryTotals)
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            summary.categoryTotals.take(5).forEach { c ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(Pill)).background(catColor(c.category)))
                                    Spacer(Modifier.width(6.dp))
                                    Text(categoryDisplay(c.category), style = Eyebrow.copy(fontSize = 10.sp), modifier = Modifier.weight(1f), maxLines = 1)
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
                        scope.launch(Dispatchers.IO) {
                            val file = writePdfReport(ctx, capturedLabel, summary, budget)
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
    // Resolve per-slice colors at @Composable scope, then pass into the draw lambda.
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
        Text("${FinancialAdvisor.fmt(amount)} ${currencyLabel("SAR")}", style = NumBold.copy(fontSize = 16.sp))
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

// Non-composable so it can be safely called from inside remember{} lambdas.
private fun monthShortLabel(offset: Int, startDay: Int, locale: Locale): String {
    val c = Calendar.getInstance().apply { timeInMillis = Dates.monthRange(offset, startDay).first }
    val symbols = DateFormatSymbols(locale)
    return symbols.shortMonths[c.get(Calendar.MONTH)]
}
