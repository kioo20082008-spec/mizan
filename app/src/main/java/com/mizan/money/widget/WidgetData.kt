package com.mizan.money.widget

import android.content.Context
import com.mizan.money.MoneyApp
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.ui.Dates
import kotlinx.coroutines.flow.first
import java.util.Calendar

data class WidgetData(
    val monthLabel: String,
    val spent: Double,
    val income: Double,
    val net: Double,
    val pct: Float,
    val hasData: Boolean
)

suspend fun loadWidgetData(context: Context): WidgetData {
    val app = context.applicationContext as MoneyApp
    val txs = app.repository.allTransactions().first()
    val prefs = context.getSharedPreferences("mizan_prefs", Context.MODE_PRIVATE)
    val startDay = prefs.getInt("month_start_day", 1)
    val manualSalary = prefs.getString("manual_salary", null)?.toDoubleOrNull() ?: 0.0

    val range = Dates.monthRange(0, startDay)
    val summary = FinancialAdvisor.summarize(txs, range.first, range.last)
    val income = FinancialAdvisor.planningIncome(summary, txs, manualSalary) ?: 0.0

    return WidgetData(
        monthLabel = monthNameShort(range.first),
        spent = summary.spent,
        income = income,
        net = summary.net,
        pct = if (income > 0) (summary.spent / income).coerceIn(0.0, 1.2).toFloat() else 0f,
        hasData = txs.isNotEmpty()
    )
}

private fun monthNameShort(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val names = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )
    return names[c.get(Calendar.MONTH)]
}
