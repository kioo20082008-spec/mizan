package com.mizan.money.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as FixedColor
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
import com.mizan.money.ui.toArabicIndicDigits
import com.mizan.money.ui.theme.localizedContext
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// One UI–style home-screen widget.
//
// The one number that matters is what's LEFT to spend this cycle, so it is the
// hero everywhere; the ring beside it shows how much of the budget is used and
// turns amber/red as the user gets close to or past the limit. Colors follow
// the system light/dark mode like Samsung's own widgets, and the layout adds
// detail as the widget is resized (2x2 ring → 4x1 → 4x2 stats → 4x3+ activity).
//
// All strings are resolved in loadWidgetData through the *localized* context so
// the widget follows the in-app language choice rather than the system locale.

private enum class Status { NONE, OK, WARN, OVER }

private data class WidgetData(
    val appName: String,
    val heroLabel: String,
    val heroAmount: String,
    val currency: String,
    val daysLeftText: String,
    val pct: Float,
    val pctLabel: String,
    val subLine: String,
    val spentLabel: String,
    val spentValue: String,
    val dailyLabel: String,
    val dailyValue: String,
    val lastTxLabel: String,
    val lastTxMerchant: String,
    val lastTxAmount: String,
    val lastTxIsExpense: Boolean,
    val hasLastTx: Boolean,
    val billLabel: String,
    val billMerchant: String,
    val billWhen: String,
    val billUrgent: Boolean,
    val hasBill: Boolean,
    val status: Status,
)

class MizanWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL, MEDIUM, LARGE, XLARGE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Never let a data hiccup blank the widget: fall back to an empty card.
        val data = runCatching { loadWidgetData(context) }.getOrElse { fallbackData(context) }
        // One shared bitmap instance for every size class keeps the RemoteViews
        // payload small (the bitmap cache de-duplicates identical instances).
        val ring = ringBitmap(context, data.pct, data.status)
        provideContent { WidgetContent(data, ring, LocalSize.current) }
    }

    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)    // ~2x2
        private val MEDIUM = DpSize(250.dp, 110.dp)   // ~4x1
        private val LARGE = DpSize(250.dp, 175.dp)    // ~4x2
        private val XLARGE = DpSize(250.dp, 290.dp)   // ~4x3 and up
    }
}

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

private fun wholeMoney(v: Double, arabic: Boolean = false): String {
    val s = String.format(Locale.US, "%,.0f", v)
    return if (arabic) toArabicIndicDigits(s) else s
}

private fun fallbackData(context: Context): WidgetData {
    val ctx = localizedContext(context)
    return WidgetData(
        appName = ctx.getString(R.string.app_name),
        heroLabel = ctx.getString(R.string.widget_remaining_label),
        heroAmount = "—",
        currency = ctx.getString(R.string.currency_sar),
        daysLeftText = "",
        pct = 0f,
        pctLabel = "",
        subLine = ctx.getString(R.string.widget_no_data),
        spentLabel = ctx.getString(R.string.widget_spent_label),
        spentValue = "—",
        dailyLabel = ctx.getString(R.string.widget_daily_label),
        dailyValue = "—",
        lastTxLabel = ctx.getString(R.string.widget_last_tx_label),
        lastTxMerchant = ctx.getString(R.string.widget_no_tx),
        lastTxAmount = "",
        lastTxIsExpense = true,
        hasLastTx = false,
        billLabel = ctx.getString(R.string.dash_upcoming_bills),
        billMerchant = "",
        billWhen = "",
        billUrgent = false,
        hasBill = false,
        status = Status.NONE,
    )
}

private suspend fun loadWidgetData(context: Context): WidgetData {
    val app = context.applicationContext as MoneyApp
    val ctx = localizedContext(context)
    val arabic = ctx.resources.configuration.locales[0].language == "ar"
    val txs = app.repository.allTransactions().first()
    val budgets = app.repository.budgets().first()
    val recurring = runCatching { app.repository.recurringItems().first() }.getOrDefault(emptyList())

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
    val spent = summary.spent
    val pct = if (hasBudget) (spent / budget).toFloat() else 0f
    val remaining = budget - spent
    val over = hasBudget && remaining < 0

    // Days left in the current cycle (the cycle may start mid-month, e.g. on
    // payday, so this — not a month name — is what the user needs to see).
    val now = System.currentTimeMillis()
    val cycleEnd = range.last + 1
    val daysLeft = ceil((cycleEnd - now).toDouble() / 86_400_000.0).toInt().coerceAtLeast(1)
    val daysLeftText = if (daysLeft <= 1) ctx.getString(R.string.widget_days_last)
    else ctx.getString(R.string.widget_left_short_fmt, daysLeft)

    // Pace: share of budget spent vs share of the cycle elapsed. Spending
    // faster than time passes turns the ring amber before the limit is hit.
    val elapsed = ((now - range.first).toDouble() / (cycleEnd - range.first).toDouble())
        .coerceIn(0.0, 1.0).toFloat()
    val status = when {
        !hasBudget -> Status.NONE
        over || pct >= 1f -> Status.OVER
        pct >= 0.85f || pct - elapsed > 0.10f -> Status.WARN
        else -> Status.OK
    }

    val currency = ctx.getString(R.string.currency_sar)
    val heroLabel: String
    val heroAmount: String
    val subLine: String
    when {
        !hasBudget -> {
            heroLabel = ctx.getString(R.string.widget_spent_month)
            heroAmount = wholeMoney(spent, arabic)
            subLine = ctx.getString(R.string.widget_set_budget_hint)
        }
        over -> {
            heroLabel = ctx.getString(R.string.widget_over_label)
            heroAmount = "-" + wholeMoney(-remaining, arabic)
            subLine = ctx.getString(R.string.widget_of_fmt, wholeMoney(budget, arabic))
        }
        else -> {
            heroLabel = ctx.getString(R.string.widget_remaining_label)
            heroAmount = wholeMoney(remaining, arabic)
            subLine = ctx.getString(R.string.widget_of_fmt, wholeMoney(budget, arabic))
        }
    }

    // Skip self-transfers so "last transaction" reflects real spending/income.
    val last = txs.filter { !it.isSelfTransfer }.maxByOrNull { it.timestamp }
    val lastIsExpense = last?.type != TxType.INCOME
    val bill = nearestBill(recurring)

    return WidgetData(
        appName = ctx.getString(R.string.app_name),
        heroLabel = heroLabel,
        heroAmount = heroAmount,
        currency = currency,
        daysLeftText = daysLeftText,
        pct = pct,
        pctLabel = if (hasBudget) (if (arabic) toArabicIndicDigits("${(pct * 100).roundToInt()}") else "${(pct * 100).roundToInt()}") + "%" else "",
        subLine = subLine,
        spentLabel = ctx.getString(R.string.widget_spent_label),
        spentValue = "${wholeMoney(spent, arabic)} $currency",
        dailyLabel = ctx.getString(R.string.widget_daily_label),
        dailyValue = if (hasBudget && !over) "${wholeMoney(remaining / daysLeft, arabic)} $currency" else "—",
        lastTxLabel = ctx.getString(R.string.widget_last_tx_label),
        lastTxMerchant = last?.let {
            it.merchant?.takeIf { m -> m.isNotBlank() } ?: categoryDisplayName(ctx, it.category)
        } ?: ctx.getString(R.string.widget_no_tx),
        lastTxAmount = last?.let {
            val amt = FinancialAdvisor.fmt(it.amount)
            (if (lastIsExpense) "-" else "+") + (if (arabic) toArabicIndicDigits(amt) else amt)
        }.orEmpty(),
        lastTxIsExpense = lastIsExpense,
        hasLastTx = last != null,
        billLabel = ctx.getString(R.string.dash_upcoming_bills),
        billMerchant = bill?.let { it.first.merchant + "  ·  ~" + wholeMoney(it.first.expectedAmount, arabic) }.orEmpty(),
        billWhen = bill?.let {
            if (it.second == 0) ctx.getString(R.string.dash_today)
            else ctx.getString(R.string.dash_in_days, it.second)
        }.orEmpty(),
        billUrgent = (bill?.second ?: Int.MAX_VALUE) <= 1,
        hasBill = bill != null,
        status = status,
    )
}

// Next due recurring bill, mirroring the dashboard's "upcoming bills" row.
private fun nearestBill(items: List<RecurringItemEntity>): Pair<RecurringItemEntity, Int>? {
    val today = java.util.Calendar.getInstance()
    val todayDay = today.get(java.util.Calendar.DAY_OF_MONTH)
    val daysInMonth = today.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    return items.filter { it.reminderEnabled }.map { item ->
        val daysUntil = if (item.expectedDayOfMonth >= todayDay) item.expectedDayOfMonth - todayDay
        else (daysInMonth - todayDay) + item.expectedDayOfMonth
        item to daysUntil
    }.minByOrNull { it.second }
}

// ---------------------------------------------------------------------------
// Colors — One UI palette, day/night aware
// ---------------------------------------------------------------------------

private object W {
    val bg = ColorProvider(day = Color(0xFFFCFCFC), night = Color(0xFF121214))
    val tile = ColorProvider(day = Color(0xFFF1F2F5), night = Color(0xFF1F1F23))
    val text = ColorProvider(day = Color(0xFF111111), night = Color(0xFFF5F5F7))
    val sub = ColorProvider(day = Color(0xFF6E6E73), night = Color(0xFF9A9AA0))

    // Samsung-style status colors (fixed; readable on both backgrounds).
    val blue = Color(0xFF3E91FF)
    val amber = Color(0xFFFF9F0A)
    val red = Color(0xFFFF453A)
    val green = Color(0xFF30C85E)
    val track = Color(0x33888890)
}

private fun statusColor(s: Status): Color = when (s) {
    Status.OVER -> W.red
    Status.WARN -> W.amber
    Status.OK -> W.blue
    Status.NONE -> W.blue
}

// Glance has no determinate circular progress, so the ring is drawn once into
// a small bitmap. The track is a translucent grey so the same image works on
// both the light and dark backgrounds.
private fun ringBitmap(context: Context, pct: Float, status: Status): Bitmap {
    val px = (context.resources.displayMetrics.density * 96f).toInt().coerceIn(120, 288)
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val stroke = px * 0.12f
    val rect = RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = W.track.toArgb()
    canvas.drawArc(rect, 0f, 360f, false, paint)
    if (status != Status.NONE) {
        val sweep = (pct.coerceIn(0f, 1f) * 360f).coerceAtLeast(if (pct > 0f) 6f else 0f)
        if (sweep > 0f) {
            paint.color = statusColor(status).toArgb()
            canvas.drawArc(rect, -90f, sweep, false, paint)
        }
    }
    return bmp
}

// ---------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------

@Composable
private fun WidgetContent(data: WidgetData, ring: Bitmap, size: DpSize) {
    val root = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(W.bg)
        .cornerRadius(26.dp)
        .clickable(actionStartActivity<MainActivity>())

    if (size.width < 200.dp) {
        SmallLayout(data, ring, root)
        return
    }

    Column(modifier = root.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Header(data)
        Spacer(GlanceModifier.height(if (size.height >= 175.dp) 14.dp else 10.dp))
        HeroRow(data, ring, ringSize = if (size.height >= 175.dp) 68.dp else 56.dp)

        if (size.height >= 175.dp) {
            Spacer(GlanceModifier.height(14.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                StatTile(data.spentLabel, data.spentValue)
                Spacer(GlanceModifier.width(8.dp))
                StatTile(data.dailyLabel, data.dailyValue)
            }
        }

        if (size.height >= 290.dp) {
            if (data.hasLastTx) {
                Spacer(GlanceModifier.height(8.dp))
                InfoTile(
                    label = data.lastTxLabel,
                    title = data.lastTxMerchant,
                    trailing = data.lastTxAmount,
                    trailingColor = FixedColor(if (data.lastTxIsExpense) W.red else W.green),
                )
            }
            if (data.hasBill) {
                Spacer(GlanceModifier.height(8.dp))
                InfoTile(
                    label = data.billLabel,
                    title = data.billMerchant,
                    trailing = data.billWhen,
                    trailingColor = if (data.billUrgent) FixedColor(W.red) else W.sub,
                )
            }
        }
    }
}

@Composable
private fun Header(data: WidgetData) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.size(8.dp).cornerRadius(4.dp)
                .background(FixedColor(statusColor(data.status)))
        ) { }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            data.appName,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = W.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        if (data.daysLeftText.isNotBlank()) {
            Text(data.daysLeftText, style = TextStyle(color = W.sub, fontSize = 12.sp))
        }
    }
}

@Composable
private fun HeroRow(data: WidgetData, ring: Bitmap, ringSize: Dp) {
    val heroColor = if (data.status == Status.OVER) FixedColor(W.red) else W.text
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(ringSize), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(ring),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.size(ringSize),
            )
            if (data.pctLabel.isNotBlank()) {
                Text(
                    data.pctLabel,
                    style = TextStyle(color = W.text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Spacer(GlanceModifier.width(16.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                data.heroLabel,
                maxLines = 1,
                style = TextStyle(
                    color = if (data.status == Status.OVER) FixedColor(W.red) else W.sub,
                    fontSize = 13.sp,
                ),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    data.heroAmount,
                    maxLines = 1,
                    style = TextStyle(color = heroColor, fontSize = heroFontSize(data.heroAmount), fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    data.currency,
                    modifier = GlanceModifier.padding(bottom = 4.dp),
                    style = TextStyle(color = W.sub, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }
            Text(data.subLine, maxLines = 1, style = TextStyle(color = W.sub, fontSize = 12.sp))
        }
    }
}

// Long amounts step down so "123,456" still fits on one line beside the ring.
private fun heroFontSize(amount: String): TextUnit = when {
    amount.length <= 5 -> 34.sp
    amount.length <= 7 -> 30.sp
    else -> 26.sp
}

@Composable
private fun SmallLayout(data: WidgetData, ring: Bitmap, root: GlanceModifier) {
    Box(root.padding(12.dp), contentAlignment = Alignment.Center) {
        Box(GlanceModifier.size(104.dp), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(ring),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.size(104.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    data.heroAmount,
                    maxLines = 1,
                    style = TextStyle(
                        color = if (data.status == Status.OVER) FixedColor(W.red) else W.text,
                        fontSize = if (data.heroAmount.length <= 5) 20.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    data.heroLabel,
                    maxLines = 1,
                    style = TextStyle(color = W.sub, fontSize = 10.sp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatTile(label: String, value: String) {
    Column(
        GlanceModifier.defaultWeight()
            .background(W.tile)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, maxLines = 1, style = TextStyle(color = W.sub, fontSize = 11.sp))
        Text(value, maxLines = 1, style = TextStyle(color = W.text, fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun InfoTile(label: String, title: String, trailing: String, trailingColor: ColorProviderAlias) {
    Row(
        GlanceModifier.fillMaxWidth()
            .background(W.tile)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight()) {
            Text(label, maxLines = 1, style = TextStyle(color = W.sub, fontSize = 11.sp))
            Text(title, maxLines = 1, style = TextStyle(color = W.text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(trailing, maxLines = 1, style = TextStyle(color = trailingColor, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}

private typealias ColorProviderAlias = androidx.glance.unit.ColorProvider
