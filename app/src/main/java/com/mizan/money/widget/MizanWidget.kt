package com.mizan.money.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
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
    val daysLeftLabel: String,
    val daysLeftValue: String,
    val dailyLabel: String,
    val dailyValue: String,
    val lastTxLabel: String,
    val lastTxMerchant: String,
    val lastTxCategory: String,
    val lastTxAmount: String,
    val lastTxIsExpense: Boolean,
    val hasLastTx: Boolean,
)

class MizanWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent { WidgetContent(data) }
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
    val daysLeft = ceil((cycleEnd - System.currentTimeMillis()).toDouble() / 86_400_000.0)
        .toInt().coerceAtLeast(1)
    val remaining = (budget - summary.spent).coerceAtLeast(0.0)

    // Skip self-transfers (money moved between the user's own accounts) so the
    // "last transaction" reflects real spending/income, like the app's lists.
    val last = txs.filter { !it.isSelfTransfer }.maxByOrNull { it.timestamp }
    val lastIsExpense = last?.type == TxType.EXPENSE
    val lastMerchant = last?.let {
        it.merchant?.takeIf { m -> m.isNotBlank() } ?: categoryDisplayName(ctx, it.category)
    } ?: ctx.getString(R.string.widget_no_tx)

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
        daysLeftLabel = ctx.getString(R.string.widget_days_left_label),
        daysLeftValue = if (daysLeft <= 1) ctx.getString(R.string.widget_days_last)
        else ctx.getString(R.string.widget_days_left_fmt, daysLeft),
        dailyLabel = ctx.getString(R.string.widget_daily_label),
        dailyValue = if (hasBudget) money(remaining / daysLeft) else "—",
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
private fun WidgetContent(data: WidgetData) {
    val cardBg = ColorProvider(Color(0xFF16151C))
    val pillBg = ColorProvider(Color(0xFF23222B))
    val divider = ColorProvider(Color(0xFF2A2A36))
    val white = ColorProvider(Color(0xFFFFFFFF))
    val soft = ColorProvider(Color(0xFFACA9B8))
    val track = ColorProvider(Color(0xFF2A2A36))
    val pos = Color(0xFF34D399)
    val neg = Color(0xFFFF6B7D)
    val accent = ColorProvider(
        when {
            data.pct >= 1.0f -> neg
            data.pct >= 0.8f -> Color(0xFFFFB84D)
            data.pct > 0f    -> pos
            else             -> Color(0xFF7C72F0)
        }
    )
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

        Spacer(modifier = GlanceModifier.height(14.dp))
        Divider(divider)
        Spacer(modifier = GlanceModifier.height(14.dp))

        // ---- 2x2 metrics: spent/remaining then days-left/daily ----
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Metric(data.spentLabel, data.spentAmount, Alignment.Start)
            Metric(data.remainingLabel, data.remainingAmount, Alignment.End)
        }
        Spacer(modifier = GlanceModifier.height(12.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Metric(data.daysLeftLabel, data.daysLeftValue, Alignment.Start)
            Metric(data.dailyLabel, data.dailyValue, Alignment.End)
        }

        Spacer(modifier = GlanceModifier.height(14.dp))
        Divider(divider)
        Spacer(modifier = GlanceModifier.height(12.dp))

        // ---- Last transaction ----
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
                    style = TextStyle(color = lastAmountColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
            }
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

// A metric cell. It is a RowScope extension because `defaultWeight` is only
// available inside a Row, and being an extension lets the two cells split the
// width evenly (start-aligned label/value vs end-aligned) without extra glue.
@Composable
private fun RowScope.Metric(label: String, value: String, alignment: Alignment.Horizontal) {
    val soft = ColorProvider(Color(0xFFACA9B8))
    val white = ColorProvider(Color(0xFFFFFFFF))
    Column(horizontalAlignment = alignment, modifier = GlanceModifier.defaultWeight()) {
        Text(text = label, style = TextStyle(color = soft, fontSize = 11.sp))
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = value,
            maxLines = 1,
            style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
