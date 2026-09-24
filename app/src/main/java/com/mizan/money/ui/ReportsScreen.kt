package com.mizan.money.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.CategoryTotal
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.ExchangeRates
import com.mizan.money.data.TOTAL_BUDGET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt

// ============ INSIGHTS — Advisor / Reports under one bottom-nav slot ============
@Composable
fun InsightsScreen(
    vm: MainViewModel,
    offset: Int,
    onOpenCategory: (String) -> Unit = {},
    onOpenPlanning: () -> Unit = {},
) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 10.dp)) {
            TabSwitcher(
                listOf(stringResource(R.string.insights_tab_advice), stringResource(R.string.insights_tab_reports)),
                subTab
            ) { subTab = it }
        }
        Box(Modifier.weight(1f)) {
            when (subTab) {
                0 -> AdvisorScreen(vm, offset, onOpenCategory = onOpenCategory, onOpenPlanning = onOpenPlanning)
                else -> ReportsSection(vm, offset, onOpenCategory)
            }
        }
    }
}

// Whole number in the UI's digit system (Arabic-Indic when the app is Arabic).
@Composable
internal fun insNum(n: Int): String =
    if (isArabicUi()) toArabicIndicDigits(n.toString()) else n.toString()

// Integer percentages that always add up to exactly 100 (largest remainder).
internal fun largestRemainderPercents(values: List<Double>): List<Int> {
    val total = values.sum()
    if (total <= 0.0) return values.map { 0 }
    val raw = values.map { it / total * 100.0 }
    val result = raw.map { floor(it).toInt() }.toMutableList()
    var left = 100 - result.sum()
    for (i in raw.indices.sortedByDescending { raw[it] - result[it] }) {
        if (left <= 0) break
        result[i] = result[i] + 1
        left--
    }
    return result
}

private data class InsightsTrendPoint(val offset: Int, val label: String, val spent: Double, val income: Double)

@Composable
private fun ReportsSection(vm: MainViewModel, offset: Int, onOpenCategory: (String) -> Unit) {
    val txs by vm.transactions.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val rates by vm.exchangeRates.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareChooserTitle = stringResource(R.string.reports_share_chooser)
    // Locale the whole screen is rendering with, so the trend chart's month
    // abbreviations follow the chosen app language instead of a hardcoded list.
    val locale = LocalConfiguration.current.locales[0]
    val currency = currencyLabel(ExchangeRates.BASE)

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
    val trend = remember(txs, offset, startDay, rates, locale) {
        (5 downTo 0).map { back ->
            val o = offset - back
            val r = Dates.monthRange(o, startDay)
            val s = FinancialAdvisor.summarize(txs, r.first, r.last, rates)
            InsightsTrendPoint(o, monthShortLabel(o, startDay, locale), s.spent, s.income)
        }
    }
    val prevSummary = remember(txs, offset, startDay, rates) {
        val r = Dates.monthRange(offset - 1, startDay)
        FinancialAdvisor.summarize(txs, r.first, r.last, rates)
    }
    // Month-over-month on the same pace: while a cycle is still running, compare
    // it with last cycle up to the same day, not with last cycle's full total.
    val now = remember(txs, offset, startDay) { System.currentTimeMillis() }
    val inProgress = now in range
    val cycleDay = if (inProgress) ((now - range.first) / 86_400_000L).toInt() + 1 else 0
    val prevCompare = remember(txs, offset, startDay, rates, now) {
        val r = Dates.monthRange(offset - 1, startDay)
        val end = if (inProgress) (r.first + (now - range.first)).coerceAtMost(r.last) else r.last
        FinancialAdvisor.summarize(txs, r.first, end, rates)
    }
    val percents = remember(summary) { largestRemainderPercents(summary.categoryTotals.map { it.amount }) }
    var showAllCategories by remember(offset) { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    val runCsvExport: () -> Unit = {
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
    val runPdfExport: () -> Unit = {
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
            fun localizeArgs(args: List<Any>, category: String?): Array<Any> =
                (if (category == null) args
                 else args.map { if (it == category) categoryDisplayName(ctx, category) else it }).toTypedArray()
            val resolvedAdvice = advice.map {
                PdfAdvice(
                    title = if (it.titleArgs.isEmpty()) ctx.getString(it.titleRes)
                            else ctx.getString(it.titleRes, *localizeArgs(it.titleArgs, it.category)),
                    body = if (it.bodyArgs.isEmpty()) ctx.getString(it.bodyRes)
                           else ctx.getString(it.bodyRes, *localizeArgs(it.bodyArgs, it.category)),
                    level = when (it.level) {
                        com.mizan.money.advisor.Level.DANGER -> "danger"
                        com.mizan.money.advisor.Level.WARN -> "warn"
                        com.mizan.money.advisor.Level.GOOD -> "good"
                        else -> "info"
                    }
                )
            }
            val categoryMap = (monthTxs.map { it.category } + capturedSummary.categoryTotals.map { it.category })
                .distinct()
                .associateWith { categoryDisplayName(ctx, it) }
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

    if (showExportSheet) {
        ReportExportSheet(
            onDismiss = { showExportSheet = false },
            onCsv = { showExportSheet = false; runCsvExport() },
            onPdf = { showExportSheet = false; runPdfExport() },
        )
    }

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
                Spacer(Modifier.height(14.dp))
                TrendChart(trend, startDay, currency)
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
                val cats = summary.categoryTotals
                // Collapsing a single leftover row into "+1 more" saves nothing.
                val visible = if (showAllCategories || cats.size <= 6) cats.size else 5
                SectionCard(stringResource(R.string.reports_categories_title_fmt, monthName(offset, startDay))) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CategoryDonut(cats, summary.spent, currency)
                    }
                    Spacer(Modifier.height(4.dp))
                    cats.take(visible).forEachIndexed { i, c ->
                        ListRow(
                            icon = catIcon(c.category),
                            iconTint = catColor(c.category),
                            title = categoryDisplay(c.category),
                            subtitle = stringResource(R.string.insights_percent_fmt, insNum(percents.getOrElse(i) { 0 })),
                            trailing = fmt(c.amount),
                            trailingSub = currency,
                            showDivider = i < visible - 1 || visible < cats.size,
                            onClick = { onOpenCategory(c.category) }
                        )
                    }
                    if (visible < cats.size) {
                        val rest = cats.drop(visible)
                        val restPct = percents.drop(visible).sum()
                        ListRow(
                            icon = Icons.Default.MoreHoriz,
                            iconTint = InkSoft,
                            title = stringResource(R.string.insights_more_categories_fmt, insNum(rest.size)),
                            subtitle = stringResource(R.string.insights_percent_fmt, insNum(restPct)),
                            trailing = fmt(rest.sumOf { it.amount }),
                            trailingSub = currency,
                            showDivider = false,
                            onClick = { showAllCategories = true }
                        )
                    }
                }
            }
        }
        item {
            SoftCard {
                Text(stringResource(R.string.reports_comparison_title), style = H2)
                if (inProgress) {
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.insights_compare_pace_fmt, insNum(cycleDay)), style = Eyebrow)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ComparisonColumn(
                        stringResource(if (inProgress) R.string.insights_this_month_so_far else R.string.reports_this_month),
                        summary.spent, currency, Modifier.weight(1f)
                    )
                    Box(Modifier.width(1.dp).height(40.dp).background(Line))
                    ComparisonColumn(
                        if (inProgress) stringResource(R.string.insights_last_month_same_day_fmt, insNum(cycleDay))
                        else stringResource(R.string.reports_last_month),
                        prevCompare.spent, currency, Modifier.weight(1f)
                    )
                }
                if (prevCompare.spent > 0) {
                    Spacer(Modifier.height(12.dp))
                    val diff = ((summary.spent - prevCompare.spent) / prevCompare.spent) * 100
                    val rounded = diff.roundToInt()
                    Text(
                        when {
                            rounded == 0 -> stringResource(R.string.insights_diff_same)
                            rounded > 0 -> stringResource(R.string.reports_diff_higher_fmt, rounded)
                            else -> stringResource(R.string.reports_diff_lower_fmt, -rounded)
                        },
                        style = Eyebrow.copy(
                            fontSize = 13.sp,
                            color = when {
                                rounded == 0 -> InkSoft
                                rounded > 0 -> Danger
                                else -> Success
                            },
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusLg))
                    .background(White)
                    .clickable(enabled = !exporting) { showExportSheet = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Default.Share, Indigo, Indigo.copy(alpha = 0.12f), size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.insights_share_report),
                        style = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        stringResource(R.string.insights_share_report_sub),
                        style = Body.copy(fontSize = 13.sp, color = InkSoft)
                    )
                }
                if (exporting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Indigo, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = InkFaint)
                }
            }
        }
    }
}

@Composable
private fun ReportExportSheet(onDismiss: () -> Unit, onCsv: () -> Unit, onPdf: () -> Unit) {
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insights_share_report), style = H2) },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusLg))
                    .background(Paper)
            ) {
                ExportOptionRow(Icons.Default.TableChart, Success, stringResource(R.string.reports_export_csv),
                    stringResource(R.string.insights_export_csv_sub), showDivider = true, onClick = onCsv)
                ExportOptionRow(Icons.Default.PictureAsPdf, Danger, stringResource(R.string.reports_export_pdf),
                    stringResource(R.string.insights_export_pdf_sub), showDivider = false, onClick = onPdf)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.stg_cancel), style = Body.copy(color = InkSoft))
            }
        }
    )
}

@Composable
private fun ExportOptionRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, tint, tint.copy(alpha = 0.12f), size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = Body.copy(fontSize = 13.sp, color = InkSoft))
            }
        }
        if (showDivider) {
            Box(Modifier.padding(start = 70.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(Line))
        }
    }
}

@Composable
private fun TrendChart(points: List<InsightsTrendPoint>, startDay: Int, currency: String) {
    if (points.isEmpty()) return
    val maxVal = (points.maxOfOrNull { maxOf(it.spent, it.income) } ?: 0.0).coerceAtLeast(1.0)
    val dangerColor = Danger
    val successColor = Success
    val gridColor = Line
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var selected by remember(points.size, points.lastOrNull()?.offset) { mutableIntStateOf(points.lastIndex) }
    val sel = points[selected.coerceIn(0, points.lastIndex)]
    val n = points.size

    Column {
        // Values for the tapped month, shown above the bars.
        Text(monthName(sel.offset, startDay), style = Eyebrow)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reports_legend_spend), style = Eyebrow.copy(fontSize = 13.sp))
            Spacer(Modifier.width(6.dp))
            Text("${fmt(sel.spent)} $currency", style = NumBold.copy(fontSize = 15.sp, color = dangerColor))
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.reports_legend_income), style = Eyebrow.copy(fontSize = 13.sp))
            Spacer(Modifier.width(6.dp))
            Text("${fmt(sel.income)} $currency", style = NumBold.copy(fontSize = 15.sp, color = successColor))
        }
        Spacer(Modifier.height(12.dp))
        Text(fmt(maxVal), style = Eyebrow.copy(fontSize = 11.sp, color = InkFaint))
        Spacer(Modifier.height(2.dp))
        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
                .pointerInput(n, isRtl) {
                    detectTapGestures { pos ->
                        val groupW = size.width.toFloat() / n
                        val slot = (pos.x / groupW).toInt().coerceIn(0, n - 1)
                        selected = if (isRtl) n - 1 - slot else slot
                    }
                }
        ) {
            val groupWidth = size.width / n
            val barWidth = groupWidth / 3.5f
            val gap = barWidth * 0.4f
            val inset = (groupWidth - (barWidth * 2 + gap)) / 2f
            // Mirrors x in RTL so the oldest month sits at the start edge,
            // matching the label row below (which Compose flips on its own).
            fun x(left: Float, w: Float): Float = if (isRtl) size.width - left - w else left

            drawLine(
                color = gridColor,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            )
            points.forEachIndexed { i, p ->
                val alpha = if (i == selected) 1f else 0.4f
                val groupX = i * groupWidth
                val spentH = (p.spent / maxVal * size.height).toFloat()
                val incomeH = (p.income / maxVal * size.height).toFloat()
                drawRect(
                    color = dangerColor.copy(alpha = alpha),
                    topLeft = Offset(x(groupX + inset, barWidth), size.height - spentH),
                    size = Size(barWidth, spentH)
                )
                drawRect(
                    color = successColor.copy(alpha = alpha),
                    topLeft = Offset(x(groupX + inset + barWidth + gap, barWidth), size.height - incomeH),
                    size = Size(barWidth, incomeH)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Same equal-width slots as the canvas groups, so each label sits
        // under its own bars in both directions.
        Row(Modifier.fillMaxWidth()) {
            points.forEachIndexed { i, p ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        p.label,
                        style = Eyebrow.copy(
                            fontSize = 11.sp,
                            color = if (i == selected) Ink else InkSoft,
                            fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryDonut(categories: List<CategoryTotal>, total: Double, currency: String) {
    val sum = categories.sumOf { it.amount }.coerceAtLeast(0.01)
    val sliceColors = categories.map { catColor(it.category) }
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 18.dp.toPx()
            var startAngle = -90f
            categories.forEachIndexed { index, c ->
                val sweep = (c.amount / sum * 360.0).toFloat().coerceAtLeast(1f)
                drawArc(
                    color = sliceColors[index],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = Size(size.width - sw, size.height - sw),
                    style = Stroke(width = sw, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmt(total), style = NumBold.copy(fontSize = 15.sp), maxLines = 1)
            Text(currency, style = Eyebrow.copy(fontSize = 11.sp))
        }
    }
}

@Composable
private fun ComparisonColumn(label: String, amount: Double, currency: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Eyebrow, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text("${fmt(amount)} $currency", style = NumBold.copy(fontSize = 16.sp), maxLines = 1)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(Pill)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Eyebrow.copy(fontSize = 12.sp))
    }
}

private fun monthShortLabel(offset: Int, startDay: Int, locale: java.util.Locale): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = Dates.monthRange(offset, startDay).first }
    return java.text.DateFormatSymbols(locale).shortMonths[c.get(java.util.Calendar.MONTH)]
}
