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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
import kotlin.math.roundToInt

data class WidgetData(
    val headerLine: String,
    val usageLabel: String,
    val bigNumber: String,
    val spentLine: String,
    val pct: Float,
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
    val pct = if (budget > 0) (summary.spent / budget).coerceIn(0.0, 1.2).toFloat() else 0f
    val hasBudget = budget > 0
    val currency = context.getString(R.string.currency_sar)

    val hasData = txs.isNotEmpty()
    val brand = context.getString(R.string.app_name)
    val month = monthLabel(context, range.first)

    return WidgetData(
        headerLine = "$brand  ·  $month",
        usageLabel = if (hasBudget) context.getString(R.string.widget_usage_label)
                     else context.getString(R.string.widget_no_budget),
        bigNumber = if (hasBudget) "${(pct * 100).toInt()}%" else "—",
        spentLine = if (hasData)
            context.getString(R.string.widget_spent_fmt, FinancialAdvisor.fmt(summary.spent), currency)
        else context.getString(R.string.widget_no_data),
        pct = pct,
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
    val bg = ColorProvider(Color(0xFF121017))
    val lime = ColorProvider(Color(0xFFD7F26B))
    val soft = ColorProvider(Color(0xFFACA9B8))
    val line = ColorProvider(Color(0xFF2A2A36))
    val accent = when {
        data.pct >= 1.0f -> ColorProvider(Color(0xFFFF6B7D))
        data.pct >= 0.8f -> ColorProvider(Color(0xFFFFB84D))
        data.pct > 0f    -> ColorProvider(Color(0xFF34D399))
        else             -> ColorProvider(Color(0xFF7C72F0))
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Text(
            text = data.headerLine,
            style = TextStyle(color = lime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.height(12.dp))
        Text(
            text = data.usageLabel,
            style = TextStyle(color = soft, fontSize = 10.sp)
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = data.bigNumber,
            style = TextStyle(color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        // Glance has no fractional fillMaxWidth, so the progress bar is drawn as
        // 10 equal-weight segments — as many lit as the current percentage.
        Row(modifier = GlanceModifier.fillMaxWidth().height(4.dp)) {
            val filled = (data.pct.coerceIn(0f, 1f) * 10f).roundToInt()
            repeat(10) { i ->
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(if (i < filled) accent else line)
                ) { }
            }
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = data.spentLine,
            style = TextStyle(color = soft, fontSize = 11.sp)
        )
    }
}
