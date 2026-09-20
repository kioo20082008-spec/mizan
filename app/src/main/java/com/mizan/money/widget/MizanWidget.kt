package com.mizan.money.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mizan.money.MainActivity
import com.mizan.money.MoneyApp
import com.mizan.money.R
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.ExchangeRates
import com.mizan.money.data.RecurringItemEntity
import com.mizan.money.data.TOTAL_BUDGET
import com.mizan.money.data.TxType
import com.mizan.money.ui.Dates
import com.mizan.money.ui.categoryDisplayName
import com.mizan.money.ui.theme.localizedContext
import kotlinx.coroutines.flow.first
import kotlin.math.ceil

// All strings are resolved in loadWidgetData (with a Context) rather than in
// the composable, so the widget content has no dependency on Glance's
// composition locals and stays trivially previewable. The Context used there
// is the *localized* one, so the widget follows the user's in-app language
// choice (Arabic/English) instead of the system locale.
data class WidgetData(
    val monthLabel: String,
    val titleLabel: String,
    val limitLabel: String,
    val spentLabel: String,
    val remainingLabel: String,
    val viewDetailsLabel: String,
    val budgetAmount: String,
    val spentAmount: String,
    val remainingAmount: String,
    val pctLabel: String,
    val pct: Float,
    val hasBudget: Boolean,
    val paceLabel: String,
    val paceDelta: Float,
    val daysLeftLabel: String,
    val daysLeftValue: String,
    val dailyLabel: String,
    val dailyValue: String,
    val incomeLabel: String,
    val incomeValue: String,
    val netLabel: String,
    val netValue: String,
    val netIsNegative: Boolean,
    val goalLabel: String,
    val goalName: String,
    val goalPctLabel: String,
    val goalPct: Float,
    val hasGoal: Boolean,
    val billLabel: String,
    val billMerchant: String,
    val billAmount: String,
    val billWhen: String,
    val billUrgent: Boolean,
    val hasBill: Boolean,
    val lastTxLabel: String,
    val lastTxMerchant: String,
    val lastTxCategory: String,
    val lastTxAmount: String,
    val lastTxIsExpense: Boolean,
    val hasLastTx: Boolean,
)

class MizanWidget : GlanceAppWidget() {
    // Exact lets the content read its real footprint (LocalSize) and show more
    // sections on taller sizes / hide them on short ones instead of clipping.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent { WidgetContent(data, LocalSize.current) }
    }
}

private suspend fun loadWidgetData(context: Context): WidgetData {
    val app = context.applicationContext as MoneyApp
    // Resolve every label through the localized context so an English user gets
    // an English widget even though the Application context stays on the
    // system locale (same approach as notifications).
    val ctx = localizedContext(context)
    val txs = app.repository.allTransactions().first()
    val budgets = app.repository.budgets().first()
    val goals = app.repository.goals().first()
    val recurring = app.repository.recurringItems().first()

    val prefs = context.getSharedPreferences("mizan_prefs", Context.MODE_PRIVATE)
    val startDay = prefs.getInt("month_start_day", 1)
    val manualSalary = prefs.getString("manual_salary", null)?.toDoubleOrNull() ?: 0.0
    val rates = ExchangeRates.load(prefs)

    val range = Dates.monthRange(0, startDay)
    val summary = FinancialAdvisor.summarize(txs, range.first, range.last, rates)
    val monthKey = Dates.monthKey(0, startDay)
    val manualBudget = budgets.firstOrNull {
        it.monthKey == monthKey && it.category == TOTAL_BUDGET
    }?.limitAmount?.takeIf { it > 0 }
    val budget = manualBudget ?: (FinancialAdvisor.planningIncome(summary, txs, manualSalary, rates) ?: 0.0)

    val hasBudget = budget > 0
    val hasData = txs.isNotEmpty()
    val pct = if (hasBudget) (summary.spent / budget).toFloat() else 0f
    val currency = ctx.getString(R.string.currency_sar)
    val money = { v: Double -> "${FinancialAdvisor.fmt(v)} $currency" }

    // Deplete over the remaining days of the cycle so the number actually
    // tells the user what they can still spend per day.
    val cycleEnd = range.last + 1
    val now = System.currentTimeMillis()
    val daysLeft = ceil((cycleEnd - now).toDouble() / 86_400_000.0)
        .toInt().coerceAtLeast(1)
    val remaining = (budget - summary.spent).coerceAtLeast(0.0)

    // Pace compares how much of the budget is spent against how much of the
    // month has elapsed: >0 means spending faster than the month is passing.
    val elapsedFraction = ((now - range.first).toDouble() / (cycleEnd - range.first).toDouble())
        .coerceIn(0.0, 1.0).toFloat()
    val paceDelta = pct - elapsedFraction
    val paceLabel = when {
        !hasBudget -> ""
        paceDelta > 0.05f -> ctx.getString(R.string.widget_pace_ahead)
        paceDelta < -0.05f -> ctx.getString(R.string.widget_pace_behind)
        else -> ctx.getString(R.string.widget_pace_on)
    }

    // Skip self-transfers (money moved between the user's own accounts) so the
    // "last transaction" reflects real spending/income, like the app's lists.
    val last = txs.filter { !it.isSelfTransfer }.maxByOrNull { it.timestamp }
    val lastIsExpense = last?.type == TxType.EXPENSE
    val lastMerchant = last?.let {
        it.merchant?.takeIf { m -> m.isNotBlank() } ?: categoryDisplayName(ctx, it.category)
    } ?: ctx.getString(R.string.widget_no_tx)

    // Nearest active savings goal (soonest deadline) for a one-line progress row.
    val activeGoal = goals
        .filter { !it.isArchived && it.targetAmount > 0.0 }
        .minByOrNull { it.targetDate ?: Long.MAX_VALUE }
    val goalPct = activeGoal?.let { (it.currentAmount / it.targetAmount).toFloat().coerceIn(0f, 1f) } ?: 0f

    val bill = nearestBill(recurring)
    val net = summary.net

    return WidgetData(
        monthLabel = monthLabel(ctx, range.first),
        titleLabel = ctx.getString(R.string.widget_monthly_budget),
        limitLabel = ctx.getString(R.string.widget_spending_limit),
        spentLabel = ctx.getString(R.string.widget_spent_label),
        remainingLabel = ctx.getString(R.string.widget_remaining_label),
        viewDetailsLabel = ctx.getString(R.string.widget_view_details),
        budgetAmount = if (hasBudget) money(budget) else "—",
        spentAmount = if (hasData) money(summary.spent) else ctx.getString(R.string.widget_no_data),
        remainingAmount = if (hasBudget) money(remaining) else "—",
        pctLabel = if (hasBudget) "${(pct * 100).toInt()}%" else "",
        pct = pct,
        hasBudget = hasBudget,
        paceLabel = paceLabel,
        paceDelta = paceDelta,
        daysLeftLabel = ctx.getString(R.string.widget_days_left_label),
        daysLeftValue = if (daysLeft <= 1) ctx.getString(R.string.widget_days_last)
        else ctx.getString(R.string.widget_days_left_fmt, daysLeft),
        dailyLabel = ctx.getString(R.string.widget_daily_label),
        dailyValue = if (hasBudget) money(remaining / daysLeft) else "—",
        incomeLabel = ctx.getString(R.string.dash_total_income),
        incomeValue = money(summary.income),
        netLabel = ctx.getString(R.string.pdf_net_label),
        netValue = money(net),
        netIsNegative = net < 0,
        goalLabel = ctx.getString(R.string.widget_goal_label),
        goalName = activeGoal?.name.orEmpty(),
        goalPctLabel = if (activeGoal != null) "${(goalPct * 100).toInt()}%" else "",
        goalPct = goalPct,
        hasGoal = activeGoal != null,
        billLabel = ctx.getString(R.string.dash_upcoming_bills),
        billMerchant = bill?.first?.merchant.orEmpty(),
        billAmount = bill?.let { "~" + money(it.first.expectedAmount) }.orEmpty(),
        billWhen = bill?.let {
            if (it.second == 0) ctx.getString(R.string.dash_today)
            else ctx.getString(R.string.dash_in_days, it.second)
        }.orEmpty(),
        billUrgent = (bill?.second ?: Int.MAX_VALUE) <= 1,
        hasBill = bill != null,
        lastTxLabel = if (last != null)
            ctx.getString(R.string.widget_last_tx_label) + "  ·  " + shortDate(last.timestamp)
        else ctx.getString(R.string.widget_last_tx_label),
        lastTxMerchant = lastMerchant,
        lastTxCategory = last?.let { categoryDisplayName(ctx, it.category) } ?: "",
        lastTxAmount = last?.let { (if (lastIsExpense) "-" else "+") + money(it.amount) } ?: "",
        lastTxIsExpense = lastIsExpense,
        hasLastTx = last != null,
    )
}

// Next due recurring bill within the current calendar month, mirroring the
// logic used by the dashboard's "upcoming bills" row.
private fun nearestBill(items: List<RecurringItemEntity>): Pair<RecurringItemEntity, Int>? {
    val today = java.util.Calendar.getInstance()
    val todayDay = today.get(java.util.Calendar.DAY_OF_MONTH)
    val daysInMonth = today.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    return items.filter { it.reminderEnabled }.map { item ->
        val daysUntil = if (item.expectedDayOfMonth >= todayDay) {
            item.expectedDayOfMonth - todayDay
        } else {
            (daysInMonth - todayDay) + item.expectedDayOfMonth
        }
        item to daysUntil
    }.minByOrNull { it.second }
}

private fun shortDate(ts: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    return "%02d/%02d".format(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1)
}

private fun monthLabel(context: Context, ts: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val monthRes = when (c.get(java.util.Calendar.MONTH)) {
        0 -> R.string.month_jan
        1 -> R.string.month_feb
        2 -> R.string.month_mar
        3 -> R.string.month_apr
        4 -> R.string.month_may
        5 -> R.string.month_jun
        6 -> R.string.month_jul
        7 -> R.string.month_aug
        8 -> R.string.month_sep
        9 -> R.string.month_oct
        10 -> R.string.month_nov
        else -> R.string.month_dec
    }
    return context.getString(monthRes) + " " + c.get(java.util.Calendar.YEAR)
}

@Composable
private fun WidgetContent(data: WidgetData, size: DpSize) {
    // Progressive disclosure: the widget keeps its core (budget + bar) at any
    // size and reveals the extra rows only when the cell is tall enough, so it
    // never clips on a small home-screen cell.
    val h = size.height
    val showDays = h >= 260.dp
    val showLastTx = h >= 340.dp
    val showIncome = h >= 375.dp
    val showGoal = h >= 450.dp && data.hasGoal
    val showBill = h >= 525.dp && data.hasBill

    val cardBg = ColorProvider(Color(0xFF16151C))
    val pillBg = ColorProvider(Color(0xFF23222B))
    val divider = ColorProvider(Color(0xFF2A2A36))
    val white = ColorProvider(Color(0xFFFFFFFF))
    val soft = ColorProvider(Color(0xFFACA9B8))
    val track = ColorProvider(Color(0xFF2A2A36))
    val pos = Color(0xFF34D399)
    val neg = Color(0xFFFF6B7D)
    val amber = Color(0xFFFFB84D)
    val yellow = Color(0xFFF6D365)
    val accent = ColorProvider(
        when {
            !data.hasBudget -> Color(0xFF7C72F0)
            data.pct >= 1.0f -> neg
            data.pct >= 0.85f || data.paceDelta > 0.10f -> amber
            data.paceDelta > 0.03f -> yellow
            else -> pos
        }
    )
    val paceColor = ColorProvider(
        when {
            data.paceDelta > 0.10f -> neg
            data.paceDelta > 0.03f -> amber
            data.paceDelta < -0.03f -> pos
            else -> Color(0xFFACA9B8)
        }
    )
    val netColor = ColorProvider(if (data.netIsNegative) neg else pos)
    val lastAmountColor = ColorProvider(if (data.lastTxIsExpense) neg else pos)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(cardBg)
            .cornerRadius(26.dp)
            .padding(18.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // ---- Header: title + month pill ----
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = data.titleLabel,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = soft, fontSize = 12.sp)
            )
            Box(
                modifier = GlanceModifier
                    .background(pillBg)
                    .cornerRadius(10.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = data.monthLabel,
                    style = TextStyle(color = soft, fontSize = 11.sp)
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        // ---- Big budget number ----
        Text(
            text = data.budgetAmount,
            style = TextStyle(color = white, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = GlanceModifier.height(12.dp))

        // ---- Spending-limit progress bar (label + % on one line) ----
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = data.limitLabel,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = soft, fontSize = 11.sp)
            )
            if (data.hasBudget) {
                Text(
                    text = data.pctLabel,
                    style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(7.dp))
        if (data.hasBudget) {
            LinearProgressIndicator(
                progress = data.pct.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = accent,
                backgroundColor = track
            )
        } else {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .cornerRadius(3.dp)
                    .background(track)
            ) { }
        }
        if (data.hasBudget && data.paceLabel.isNotBlank()) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = data.paceLabel,
                style = TextStyle(color = paceColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = GlanceModifier.height(14.dp))
        Divider(divider)
        Spacer(modifier = GlanceModifier.height(14.dp))

        // ---- Metrics: spent/remaining, then days-left/daily (+ income/net) ----
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Metric(data.spentLabel, data.spentAmount, Alignment.Start)
            Metric(data.remainingLabel, data.remainingAmount, Alignment.End)
        }
        if (showDays) {
            Spacer(modifier = GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Metric(data.daysLeftLabel, data.daysLeftValue, Alignment.Start)
                Metric(data.dailyLabel, data.dailyValue, Alignment.End)
            }
        }
        if (showIncome) {
            Spacer(modifier = GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Metric(data.incomeLabel, data.incomeValue, Alignment.Start)
                MetricValue(data.netLabel, data.netValue, netColor, Alignment.End)
            }
        }

        if (showLastTx) {
            Spacer(modifier = GlanceModifier.height(14.dp))
            Divider(divider)
            Spacer(modifier = GlanceModifier.height(12.dp))
            LastTxRow(data, soft, white, lastAmountColor)
        }

        if (showGoal) {
            Spacer(modifier = GlanceModifier.height(14.dp))
            Divider(divider)
            Spacer(modifier = GlanceModifier.height(12.dp))
            GoalRow(data, soft, white, track)
        }

        if (showBill) {
            Spacer(modifier = GlanceModifier.height(14.dp))
            Divider(divider)
            Spacer(modifier = GlanceModifier.height(12.dp))
            BillRow(data, soft, white, neg)
        }

        Spacer(modifier = GlanceModifier.height(14.dp))

        // ---- View details ----
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(pillBg)
                .cornerRadius(14.dp)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = data.viewDetailsLabel,
                style = TextStyle(color = white, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun LastTxRow(
    data: WidgetData,
    soft: ColorProvider,
    white: ColorProvider,
    amountColor: ColorProvider,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = data.lastTxLabel,
                style = TextStyle(color = soft, fontSize = 10.sp)
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = data.lastTxMerchant,
                maxLines = 1,
                style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
            if (data.hasLastTx && data.lastTxCategory.isNotBlank()) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = data.lastTxCategory,
                    maxLines = 1,
                    style = TextStyle(color = soft, fontSize = 10.sp)
                )
            }
        }
        if (data.hasLastTx) {
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = data.lastTxAmount,
                style = TextStyle(color = amountColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun GoalRow(
    data: WidgetData,
    soft: ColorProvider,
    white: ColorProvider,
    track: ColorProvider,
) {
    val goalColor = ColorProvider(Color(0xFF7C72F0))
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = data.goalLabel, style = TextStyle(color = soft, fontSize = 10.sp))
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = data.goalName,
                    maxLines = 1,
                    style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = data.goalPctLabel,
                style = TextStyle(color = goalColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = data.goalPct.coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(5.dp).cornerRadius(3.dp),
            color = goalColor,
            backgroundColor = track
        )
    }
}

@Composable
private fun BillRow(
    data: WidgetData,
    soft: ColorProvider,
    white: ColorProvider,
    danger: Color,
) {
    val whenColor = ColorProvider(if (data.billUrgent) danger else Color(0xFFACA9B8))
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(text = data.billLabel, style = TextStyle(color = soft, fontSize = 10.sp))
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = data.billMerchant,
                maxLines = 1,
                style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = data.billAmount,
                style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = data.billWhen,
                style = TextStyle(color = whenColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

// A metric cell. It is a RowScope extension because `defaultWeight` is only
// available inside a Row, and being an extension lets the two cells split the
// width evenly (start-aligned label/value vs end-aligned) without extra glue.
@Composable
private fun RowScope.Metric(label: String, value: String, alignment: Alignment.Horizontal) {
    MetricValue(label, value, ColorProvider(Color(0xFFFFFFFF)), alignment)
}

@Composable
private fun RowScope.MetricValue(
    label: String,
    value: String,
    valueColor: ColorProvider,
    alignment: Alignment.Horizontal,
) {
    val soft = ColorProvider(Color(0xFFACA9B8))
    Column(horizontalAlignment = alignment, modifier = GlanceModifier.defaultWeight()) {
        Text(text = label, style = TextStyle(color = soft, fontSize = 11.sp))
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = value,
            maxLines = 1,
            style = TextStyle(color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun Divider(color: ColorProvider) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    ) { }
}
