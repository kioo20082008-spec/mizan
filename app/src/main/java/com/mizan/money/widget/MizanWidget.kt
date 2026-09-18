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
import com.mizan.money.ui.Dates
import kotlinx.coroutines.flow.first

// All strings are resolved in loadWidgetData (with a Context) rather than in
// the composable, so the widget content has no dependency on Glance's
// composition locals and stays trivially previewable.
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
)

class MizanWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent { WidgetContent(data) }
    }
}

private suspend fun loadWidgetData(context: Context): WidgetData {
    val app = context.applicationContext as MoneyApp
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
    val currency = context.getString(R.string.currency_sar)
    val money = { v: Double -> "${FinancialAdvisor.fmt(v)} $currency" }

    return WidgetData(
        monthLabel = monthLabel(context, range.first),
        titleLabel = context.getString(R.string.widget_monthly_budget),
        limitLabel = context.getString(R.string.widget_spending_limit),
        spentLabel = context.getString(R.string.widget_spent_label),
        remainingLabel = context.getString(R.string.widget_remaining_label),
        viewDetailsLabel = context.getString(R.string.widget_view_details),
        budgetAmount = if (hasBudget) money(budget) else "—",
        spentAmount = if (hasData) money(summary.spent) else context.getString(R.string.widget_no_data),
        remainingAmount = if (hasBudget) money((budget - summary.spent).coerceAtLeast(0.0)) else "—",
        pctLabel = if (hasBudget) "${(pct * 100).toInt()}%" else "",
        pct = pct,
        hasBudget = hasBudget,
    )
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
    val white = ColorProvider(Color(0xFFFFFFFF))
    val soft = ColorProvider(Color(0xFFACA9B8))
    val track = ColorProvider(Color(0xFF2A2A36))
    val accent = ColorProvider(
        when {
            data.pct >= 1.0f -> Color(0xFFFF6B7D)
            data.pct >= 0.8f -> Color(0xFFFFB84D)
            data.pct > 0f    -> Color(0xFF34D399)
            else             -> Color(0xFF7C72F0)
        }
    )
    val badgeBg = ColorProvider(
        when {
            data.pct >= 1.0f -> Color(0x33FF6B7D)
            data.pct >= 0.8f -> Color(0x33FFB84D)
            data.pct > 0f    -> Color(0x3334D399)
            else             -> Color(0x337C72F0)
        }
    )

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

        Spacer(modifier = GlanceModifier.height(8.dp))

        // ---- Big budget number ----
        Text(
            text = data.budgetAmount,
            style = TextStyle(color = white, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = GlanceModifier.height(14.dp))

        // ---- Spending-limit progress bar ----
        Text(
            text = data.limitLabel,
            style = TextStyle(color = soft, fontSize = 11.sp)
        )
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

        // ---- Spent (with %) vs Remaining ----
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = data.spentLabel,
                    style = TextStyle(color = soft, fontSize = 11.sp)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = data.spentAmount,
                        style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                    if (data.hasBudget) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Box(
                            modifier = GlanceModifier
                                .background(badgeBg)
                                .cornerRadius(8.dp)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = data.pctLabel,
                                style = TextStyle(color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = data.remainingLabel,
                    style = TextStyle(color = soft, fontSize = 11.sp)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = data.remainingAmount,
                    style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(16.dp))

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
