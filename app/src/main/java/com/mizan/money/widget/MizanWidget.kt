package com.mizan.money.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.view.View
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as FixedColor
import com.mizan.money.MainActivity
import com.mizan.money.MoneyApp
import com.mizan.money.R
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.ExchangeRates
import com.mizan.money.data.RecurringItemEntity
import com.mizan.money.data.TOTAL_BUDGET
import com.mizan.money.ui.Dates
import com.mizan.money.ui.theme.localizedContext
import com.mizan.money.ui.toArabicIndicDigits
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// One UI–style home-screen widget.
//
// The hero is what's LEFT to spend this cycle; the ring shows how much of the
// budget is used and turns amber/red near or past the limit. The layout adds
// detail as the widget grows (2x2 ring → 4x1 → 4x2 stats → 4x3+ next bill).
//
// Direction: RemoteViews are laid out by the *launcher* using the system
// locale, not the app's language. When the two disagree (Arabic app on an
// English phone, or the reverse) every row is mirrored by hand so Arabic
// always reads right-to-left.
//
// Colors come from the user's choice in Settings → Appearance (WidgetTheme).

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
    val billLabel: String,
    val billMerchant: String,
    val billAmount: String,
    val billWhen: String,
    val billUrgent: Boolean,
    val hasBill: Boolean,
    val status: Status,
    val flip: Boolean,
)

class MizanWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL, MEDIUM, LARGE, XLARGE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Never let a data hiccup blank the widget: fall back to an empty card.
        val data = runCatching { loadWidgetData(context) }.getOrElse { fallbackData(context) }
        val p = palette(WidgetThemePreference.load(context))
        // One shared bitmap for every size class keeps the RemoteViews payload small.
        val ring = ringBitmap(context, data.pct, data.status, p)
        provideContent { WidgetContent(data, p, ring, LocalSize.current) }
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

private fun wholeMoney(v: Double, arabic: Boolean): String {
    val s = String.format(Locale.US, "%,.0f", v)
    return if (arabic) toArabicIndicDigits(s) else s
}

private fun isArabic(ctx: Context) = ctx.resources.configuration.locales[0].language == "ar"

// True when the launcher's direction (system locale) differs from the app's.
private fun needsFlip(appRtl: Boolean): Boolean {
    val sys = Resources.getSystem().configuration.locales[0]
    val sysRtl = TextUtils.getLayoutDirectionFromLocale(sys) == View.LAYOUT_DIRECTION_RTL
    return appRtl != sysRtl
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
        billLabel = ctx.getString(R.string.dash_upcoming_bills),
        billMerchant = "",
        billAmount = "",
        billWhen = "",
        billUrgent = false,
        hasBill = false,
        status = Status.NONE,
        flip = needsFlip(isArabic(ctx)),
    )
}

private suspend fun loadWidgetData(context: Context): WidgetData {
    val app = context.applicationContext as MoneyApp
    val ctx = localizedContext(context)
    val arabic = isArabic(ctx)
    fun digits(s: String) = if (arabic) toArabicIndicDigits(s) else s

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

    // Days left in the current cycle (the cycle may start mid-month on payday).
    val now = System.currentTimeMillis()
    val cycleEnd = range.last + 1
    val daysLeft = ceil((cycleEnd - now).toDouble() / 86_400_000.0).toInt().coerceAtLeast(1)
    val daysLeftText = if (daysLeft <= 1) ctx.getString(R.string.widget_days_last)
    else digits(ctx.getString(R.string.widget_left_short_fmt, daysLeft))

    // Pace: spending faster than time passes turns the ring amber early.
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

    val bill = nearestBill(recurring)
    val pctInt = (pct * 100).roundToInt()

    return WidgetData(
        appName = ctx.getString(R.string.app_name),
        heroLabel = heroLabel,
        heroAmount = heroAmount,
        currency = currency,
        daysLeftText = daysLeftText,
        pct = pct,
        pctLabel = if (!hasBudget) "" else if (arabic) toArabicIndicDigits("$pctInt") + "٪" else "$pctInt%",
        subLine = subLine,
        spentLabel = ctx.getString(R.string.widget_spent_label),
        spentValue = "${wholeMoney(spent, arabic)} $currency",
        dailyLabel = ctx.getString(R.string.widget_daily_label),
        dailyValue = if (hasBudget && !over) "${wholeMoney(remaining / daysLeft, arabic)} $currency" else "—",
        billLabel = ctx.getString(R.string.dash_upcoming_bills),
        billMerchant = bill?.first?.merchant.orEmpty(),
        billAmount = bill?.let { "${wholeMoney(it.first.expectedAmount, arabic)} $currency" }.orEmpty(),
        billWhen = bill?.let {
            if (it.second == 0) ctx.getString(R.string.dash_today)
            else digits(ctx.getString(R.string.dash_in_days, it.second))
        }.orEmpty(),
        billUrgent = (bill?.second ?: Int.MAX_VALUE) <= 1,
        hasBill = bill != null,
        status = status,
        flip = needsFlip(arabic),
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
// Colors
// ---------------------------------------------------------------------------

private typealias CP = androidx.glance.unit.ColorProvider

private class Palette(
    val bg: CP,
    val tile: CP,
    val text: CP,
    val sub: CP,
    val ok: Color,
    val warn: Color,
    val over: Color,
    val track: Color,
)

private fun fixed(bg: Long, tile: Long, text: Long, sub: Long, ok: Long, warn: Long, over: Long, track: Long) =
    Palette(
        FixedColor(Color(bg)), FixedColor(Color(tile)), FixedColor(Color(text)), FixedColor(Color(sub)),
        Color(ok), Color(warn), Color(over), Color(track),
    )

// Solid-color themes: white text, translucent white tiles, white ring.
private fun colored(bg: Long) =
    fixed(bg, 0x29FFFFFF, 0xFFFFFFFF, 0xD9FFFFFF, 0xFFFFFFFF, 0xFFFFD60A, 0xFFFFC2BD, 0x40FFFFFF)

private fun palette(theme: WidgetTheme): Palette = when (theme) {
    WidgetTheme.AUTO -> Palette(
        bg = ColorProvider(day = Color(0xFFF7F7F9), night = Color(0xFF17171A)),
        tile = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF26262B)),
        text = ColorProvider(day = Color(0xFF111111), night = Color(0xFFF5F5F7)),
        sub = ColorProvider(day = Color(0xFF6E6E73), night = Color(0xFF9A9AA0)),
        ok = Color(0xFF3E86FF), warn = Color(0xFFFF9F0A), over = Color(0xFFFF453A),
        track = Color(0x33888890),
    )
    WidgetTheme.LIGHT -> fixed(0xFFF7F7F9, 0xFFFFFFFF, 0xFF111111, 0xFF6E6E73, 0xFF2F6FED, 0xFFE8870A, 0xFFE5383B, 0x1A000000)
    WidgetTheme.DARK -> fixed(0xFF17171A, 0xFF26262B, 0xFFF5F5F7, 0xFF9A9AA0, 0xFF5B9BFF, 0xFFFF9F0A, 0xFFFF453A, 0x2EFFFFFF)
    WidgetTheme.BLUE -> colored(WidgetTheme.BLUE.argb)
    WidgetTheme.GREEN -> colored(WidgetTheme.GREEN.argb)
    WidgetTheme.PURPLE -> colored(WidgetTheme.PURPLE.argb)
    WidgetTheme.ROSE -> colored(WidgetTheme.ROSE.argb)
}

private fun Palette.status(s: Status): Color = when (s) {
    Status.OVER -> over
    Status.WARN -> warn
    Status.OK, Status.NONE -> ok
}

// Glance has no determinate circular progress, so the ring is a small bitmap.
private fun ringBitmap(context: Context, pct: Float, status: Status, p: Palette): Bitmap {
    val px = (context.resources.displayMetrics.density * 96f).toInt().coerceIn(120, 288)
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val stroke = px * 0.11f
    val rect = RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = p.track.toArgb()
    canvas.drawArc(rect, 0f, 360f, false, paint)
    if (status != Status.NONE) {
        val sweep = (pct.coerceIn(0f, 1f) * 360f).coerceAtLeast(if (pct > 0f) 6f else 0f)
        if (sweep > 0f) {
            paint.color = p.status(status).toArgb()
            canvas.drawArc(rect, -90f, sweep, false, paint)
        }
    }
    return bmp
}

// ---------------------------------------------------------------------------
// Layout helpers (direction-aware)
// ---------------------------------------------------------------------------

// A Row whose children are listed in reading order and mirrored when the
// launcher's direction disagrees with the app's language.
@Composable
private fun DirRow(
    flip: Boolean,
    modifier: GlanceModifier,
    verticalAlignment: Alignment.Vertical,
    vararg items: @Composable RowScope.() -> Unit,
) {
    val ordered = if (flip) items.reversed() else items.toList()
    Row(modifier, verticalAlignment = verticalAlignment) {
        ordered.forEach { item -> item(this) }
    }
}

private fun WidgetData.hAlign(): Alignment.Horizontal = if (flip) Alignment.End else Alignment.Start
private fun WidgetData.tAlign(): TextAlign = if (flip) TextAlign.End else TextAlign.Start

// ---------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------

@Composable
private fun WidgetContent(d: WidgetData, p: Palette, ring: Bitmap, size: DpSize) {
    val root = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(p.bg)
        .cornerRadius(28.dp)
        .clickable(actionStartActivity<MainActivity>())

    if (size.width < 200.dp) {
        SmallLayout(d, p, ring, root)
        return
    }

    val tall = size.height >= 175.dp
    val xl = size.height >= 290.dp

    Column(modifier = root.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Header(d, p)
        Spacer(GlanceModifier.height(if (tall) 16.dp else 8.dp))
        HeroRow(d, p, ring, ringSize = if (xl) 88.dp else if (tall) 72.dp else 56.dp, big = xl)

        if (tall) {
            Spacer(GlanceModifier.defaultWeight())
            DirRow(
                d.flip, GlanceModifier.fillMaxWidth(), Alignment.CenterVertically,
                { StatTile(d.spentLabel, d.spentValue, d, p) },
                { Spacer(GlanceModifier.width(8.dp)) },
                { StatTile(d.dailyLabel, d.dailyValue, d, p) },
            )
            if (xl && d.hasBill) {
                Spacer(GlanceModifier.height(8.dp))
                BillTile(d, p)
            }
        }
    }
}

@Composable
private fun Header(d: WidgetData, p: Palette) {
    DirRow(
        d.flip, GlanceModifier.fillMaxWidth(), Alignment.CenterVertically,
        {
            Box(
                GlanceModifier.size(8.dp).cornerRadius(4.dp)
                    .background(FixedColor(p.status(d.status)))
            ) { }
        },
        { Spacer(GlanceModifier.width(8.dp)) },
        {
            Text(
                d.appName,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = d.tAlign()),
            )
        },
        {
            if (d.daysLeftText.isNotBlank()) {
                Box(
                    GlanceModifier.background(p.tile).cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        d.daysLeftText,
                        maxLines = 1,
                        style = TextStyle(color = p.sub, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        },
    )
}

@Composable
private fun HeroRow(d: WidgetData, p: Palette, ring: Bitmap, ringSize: Dp, big: Boolean) {
    val isOver = d.status == Status.OVER
    val heroColor = if (isOver) FixedColor(p.over) else p.text
    DirRow(
        d.flip, GlanceModifier.fillMaxWidth(), Alignment.CenterVertically,
        {
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = d.hAlign()) {
                Text(
                    d.heroLabel,
                    maxLines = 1,
                    style = TextStyle(
                        color = if (isOver) FixedColor(p.over) else p.sub,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = d.tAlign(),
                    ),
                )
                DirRow(
                    d.flip, GlanceModifier, Alignment.Bottom,
                    {
                        Text(
                            d.heroAmount,
                            maxLines = 1,
                            style = TextStyle(
                                color = heroColor,
                                fontSize = heroFontSize(d.heroAmount, big),
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    },
                    { Spacer(GlanceModifier.width(6.dp)) },
                    {
                        Text(
                            d.currency,
                            modifier = GlanceModifier.padding(bottom = if (big) 7.dp else 5.dp),
                            style = TextStyle(color = p.sub, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        )
                    },
                )
                Text(
                    d.subLine,
                    maxLines = 1,
                    style = TextStyle(color = p.sub, fontSize = 12.sp, textAlign = d.tAlign()),
                )
            }
        },
        { Spacer(GlanceModifier.width(12.dp)) },
        { Ring(d, p, ring, ringSize) },
    )
}

@Composable
private fun Ring(d: WidgetData, p: Palette, ring: Bitmap, ringSize: Dp) {
    Box(GlanceModifier.size(ringSize), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(ring),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.size(ringSize),
        )
        if (d.pctLabel.isNotBlank()) {
            Text(
                d.pctLabel,
                style = TextStyle(
                    color = p.text,
                    fontSize = if (ringSize >= 80.dp) 16.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

// Long amounts step down so "123,456" still fits on one line beside the ring.
private fun heroFontSize(amount: String, big: Boolean): TextUnit {
    val base = if (big) 42 else 34
    return when {
        amount.length <= 5 -> base.sp
        amount.length <= 7 -> (base - 4).sp
        else -> (base - 8).sp
    }
}

@Composable
private fun SmallLayout(d: WidgetData, p: Palette, ring: Bitmap, root: GlanceModifier) {
    Box(root.padding(12.dp), contentAlignment = Alignment.Center) {
        Box(GlanceModifier.size(108.dp), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(ring),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.size(108.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    d.heroAmount,
                    maxLines = 1,
                    style = TextStyle(
                        color = if (d.status == Status.OVER) FixedColor(p.over) else p.text,
                        fontSize = if (d.heroAmount.length <= 5) 22.sp else 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    d.heroLabel,
                    maxLines = 1,
                    style = TextStyle(color = p.sub, fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatTile(label: String, value: String, d: WidgetData, p: Palette) {
    Column(
        GlanceModifier.defaultWeight()
            .background(p.tile)
            .cornerRadius(18.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = d.hAlign(),
    ) {
        Text(label, maxLines = 1, style = TextStyle(color = p.sub, fontSize = 12.sp, textAlign = d.tAlign()))
        Spacer(GlanceModifier.height(2.dp))
        Text(value, maxLines = 1, style = TextStyle(color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = d.tAlign()))
    }
}

@Composable
private fun BillTile(d: WidgetData, p: Palette) {
    val endAlign = if (d.flip) Alignment.Start else Alignment.End
    DirRow(
        d.flip,
        GlanceModifier.fillMaxWidth()
            .background(p.tile)
            .cornerRadius(18.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        Alignment.CenterVertically,
        {
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = d.hAlign()) {
                Text(d.billLabel, maxLines = 1, style = TextStyle(color = p.sub, fontSize = 12.sp, textAlign = d.tAlign()))
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    d.billMerchant,
                    maxLines = 1,
                    style = TextStyle(color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = d.tAlign()),
                )
            }
        },
        { Spacer(GlanceModifier.width(8.dp)) },
        {
            Column(horizontalAlignment = endAlign) {
                Text(d.billAmount, maxLines = 1, style = TextStyle(color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    d.billWhen,
                    maxLines = 1,
                    style = TextStyle(
                        color = if (d.billUrgent) FixedColor(p.over) else p.sub,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        },
    )
}
