package com.mizan.money.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import com.mizan.money.data.SELF_TRANSFER_CATEGORY
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import com.mizan.money.ui.theme.MizanTheme

// ============ COLORS (read from the active theme) ============
// Every one of these is a @Composable getter reading the current palette, so
// switching between light/dark immediately updates everywhere. They MUST be
// read inside a @Composable context, which every usage site already is.

val Paper: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.paper
val PaperOuter: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.paperOuter
val White: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.white
val Line: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.line
val Ink: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.ink
val InkSoft: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.inkSoft
val InkFaint: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.inkFaint
val Ink900: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.ink900
val Ink800: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.ink800
val OnInkSoft: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.onInkSoft
val Indigo: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.indigo
val IndigoDeep: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.indigoDeep
val IndigoSoft: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.indigoSoft
val Lime: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.lime
val Success: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.success
val Danger: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.danger
val Amber: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.amber
val Purple: Color @Composable @ReadOnlyComposable get() = MizanTheme.colors.purple

// One UI rounds generously but consistently: cards ~24-28dp, controls smaller.
val RadiusSm = 12.dp
val RadiusMd = 18.dp
val RadiusLg = 24.dp
val RadiusXl = 28.dp
val Pill     = 999.dp

private val Sans = FontFamily(
    Font(R.font.ibm_plex_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_arabic_bold, FontWeight.Bold)
)

val Display: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
val H1: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
val H2: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
val Body: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 14.sp, color = Ink)
val BodyMuted: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 13.sp, color = InkSoft)
val Eyebrow: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = InkSoft)
val NumBold: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)

// ============ SHARED PRIMITIVES ============
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    bg: Color,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    radius: Dp = Pill // One UI list icons are circular
) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(bg),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size(iconSize), tint = tint) }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(White)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun oneUiSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Indigo,
    checkedBorderColor = Indigo,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = InkFaint.copy(alpha = 0.45f),
    uncheckedBorderColor = Color.Transparent,
)

// One UI "focus block": a white rounded card with its title (and an optional
// text action like "See all") inside the card, followed by its rows.
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(White)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 2.dp).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = H2.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    style = Body.copy(color = Indigo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    modifier = Modifier.clip(RoundedCornerShape(Pill)).clickable(onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
        content()
        Spacer(Modifier.height(6.dp))
    }
}

// A plain row for use inside a SectionCard: circular icon, title + subtitle,
// trailing value, inset divider below unless it's the last row.
@Composable
fun ListRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    trailingColor: Color = Ink,
    trailingSub: String? = null,
    trailingSubColor: Color = InkSoft,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, iconTint, iconTint.copy(alpha = 0.12f), size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = Body.copy(fontSize = 13.sp, color = InkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (below != null) {
                    Spacer(Modifier.height(8.dp))
                    below()
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(trailing, style = NumBold.copy(fontSize = 15.sp, color = trailingColor))
                    if (trailingSub != null) {
                        Text(trailingSub, style = Body.copy(fontSize = 12.sp, color = trailingSubColor, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
        if (showDivider) {
            Box(Modifier.padding(start = 72.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(Line))
        }
    }
}

@Composable
fun TabSwitcher(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Pill))
            .background(PaperOuter)
            .padding(4.dp)
    ) {
        items.forEachIndexed { i, label ->
            val isSel = i == selected
            val bg by animateColorAsState(if (isSel) White else Color.Transparent, tween(260), label = "tabBg")
            val fg by animateColorAsState(if (isSel) Ink else InkSoft, tween(260), label = "tabFg")
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(Pill))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = Eyebrow.copy(
                        fontSize = 13.sp,
                        color = fg,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

// Bottom-sheet counterpart of AlertDialog with the same slots, so every form
// (add/edit transaction, goals, debts, reminders) slides up within thumb reach
// instead of floating mid-screen. Short confirmations stay AlertDialogs.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = White,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null, // kept for drop-in parity with AlertDialog
) {
    // skipPartiallyExpanded sheets have a Material3 bug: hiding the IME
    // shrinks the measured content height, the sheet recomputes its anchors
    // mid-frame, and that can settle on Hidden — closing the whole sheet just
    // from tapping the keyboard's own dismiss key, with no user swipe at all.
    // Blocking the Hidden target stops that; Cancel/Save call onDismissRequest
    // directly (not through sheetState), so they still work, and the explicit
    // BackHandler below keeps the system back button working too.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    BackHandler(onBack = onDismissRequest)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        shape = RoundedCornerShape(topStart = RadiusXl, topEnd = RadiusXl),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .imePadding()
        ) {
            if (title != null) {
                title()
                Spacer(Modifier.height(16.dp))
            }
            if (text != null) {
                Box(Modifier.weight(1f, fill = false)) { text() }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dismissButton != null) {
                    dismissButton()
                    Spacer(Modifier.width(8.dp))
                }
                confirmButton()
            }
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(Icons.AutoMirrored.Outlined.ReceiptLong, InkFaint, PaperOuter, size = 72.dp, iconSize = 30.dp, radius = RadiusMd)
        Spacer(Modifier.height(14.dp))
        Text(text, style = BodyMuted)
    }
}

// Position of a row inside a One UI list group: only the group's outer corners
// are rounded and rows are separated by inset dividers, not gaps.
enum class RowPos { Single, First, Middle, Last }

fun rowPos(index: Int, size: Int): RowPos = when {
    size <= 1 -> RowPos.Single
    index == 0 -> RowPos.First
    index == size - 1 -> RowPos.Last
    else -> RowPos.Middle
}

fun RowPos.shape(r: Dp = RadiusLg): RoundedCornerShape = when (this) {
    RowPos.Single -> RoundedCornerShape(r)
    RowPos.First -> RoundedCornerShape(topStart = r, topEnd = r)
    RowPos.Middle -> RoundedCornerShape(0.dp)
    RowPos.Last -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
}

@Composable
fun TransactionCard(
    tx: TransactionEntity,
    modifier: Modifier = Modifier,
    position: RowPos = RowPos.Single,
    showDate: Boolean = true,
    onClick: () -> Unit
) {
    val isExpense = tx.type == TxType.EXPENSE
    val sign = if (isExpense) "-" else "+"
    val amtColor = if (tx.isSelfTransfer) InkFaint else if (isExpense) Ink else Success

    Column(
        modifier.fillMaxWidth()
            .clip(position.shape())
            .background(White)
            .clickable { onClick() }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(catIcon(tx.category), catColor(tx.category), catColorSoft(tx.category), size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tx.merchant ?: stringResource(R.string.tx_unknown_merchant),
                    style = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (showDate) "${categoryDisplay(tx.category)} • ${Dates.dayLabel(tx.timestamp)}"
                    else categoryDisplay(tx.category),
                    style = Eyebrow.copy(color = InkSoft, fontWeight = FontWeight.Normal, fontSize = 13.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$sign${fmt(tx.amount)}",
                    style = NumBold.copy(color = amtColor, fontSize = 15.sp)
                )
                Text(currencyLabel(tx.currency), style = Eyebrow.copy(color = InkSoft, fontWeight = FontWeight.Normal))
            }
        }
        if (position == RowPos.First || position == RowPos.Middle) {
            Box(
                Modifier.padding(start = 72.dp, end = 16.dp)
                    .fillMaxWidth().height(1.dp).background(Line)
            )
        }
    }
}

// ============ FORMATTING HELPERS ============
// Maps the Arabic category key (stored in the DB) to a display name in the
// user's chosen locale. The Arabic key itself never changes — it stays the
// join point between parser rules, budgets, and stored transactions.

// Non-composable variant so non-Compose callers (the home-screen widget) can
// localize a category with a Context they already localize for language.
fun categoryDisplayName(ctx: Context, cat: String): String = when (cat) {
    "طعام وشراب" -> ctx.getString(R.string.cat_food)
    "بقالة" -> ctx.getString(R.string.cat_groceries)
    "مواصلات" -> ctx.getString(R.string.cat_transport)
    "وقود" -> ctx.getString(R.string.cat_fuel)
    "تسوق" -> ctx.getString(R.string.cat_shopping)
    "فواتير" -> ctx.getString(R.string.cat_bills)
    "اتصالات" -> ctx.getString(R.string.cat_telecom)
    "صحة" -> ctx.getString(R.string.cat_health)
    "ترفيه" -> ctx.getString(R.string.cat_entertainment)
    "اشتراكات" -> ctx.getString(R.string.cat_subscriptions)
    "تعليم" -> ctx.getString(R.string.cat_education)
    "تحويلات" -> ctx.getString(R.string.cat_transfers)
    "أخرى" -> ctx.getString(R.string.cat_other)
    CASH_WITHDRAWAL_CATEGORY -> ctx.getString(R.string.cat_cash)
    SELF_TRANSFER_CATEGORY -> ctx.getString(R.string.cat_self_transfer)
    else -> cat
}

@Composable
fun categoryDisplay(cat: String): String =
    categoryDisplayName(androidx.compose.ui.platform.LocalContext.current, cat)
// Category colors are semantic (health=red, groceries=green) and are designed
// to read clearly on both light and dark backgrounds, so they stay fixed
// rather than being swapped per theme.
@Composable
fun catColor(cat: String): Color = when (cat) {
    "طعام وشراب" -> Amber
    "بقالة" -> Color(0xFF10B981)
    "مواصلات" -> Color(0xFF3B82F6)
    "وقود" -> Color(0xFF78716C)
    "تسوق" -> Purple
    "فواتير" -> Color(0xFF6366F1)
    "اتصالات" -> Color(0xFF0EA5E9)
    "صحة" -> Color(0xFFEC4899)
    "ترفيه" -> Color(0xFFD946EF)
    "اشتراكات" -> Color(0xFF14B8A6)
    "تعليم" -> Color(0xFFF97316)
    "تحويلات" -> Indigo
    CASH_WITHDRAWAL_CATEGORY -> Color(0xFF71717A)
    SELF_TRANSFER_CATEGORY -> InkFaint
    else -> InkFaint
}

@Composable
fun catColorSoft(cat: String): Color = catColor(cat).copy(alpha = 0.12f)

@Composable
fun currencyLabel(code: String): String = when (code) {
    "SAR" -> stringResource(R.string.currency_sar)
    "USD" -> stringResource(R.string.currency_usd)
    "EUR" -> stringResource(R.string.currency_eur)
    "AED" -> stringResource(R.string.currency_aed)
    else -> code
}

fun catIcon(cat: String): ImageVector {
    CategoryIcons.iconFor(cat)?.let { return it }
    return defaultCatIcon(cat)
}

private fun defaultCatIcon(cat: String): ImageVector = when (cat) {
    "طعام وشراب" -> Icons.Default.Restaurant
    "بقالة" -> Icons.Default.ShoppingCart
    "مواصلات" -> Icons.Default.DirectionsCar
    "وقود" -> Icons.Default.LocalGasStation
    "تسوق" -> Icons.Default.ShoppingBag
    "فواتير" -> Icons.Default.Receipt
    "اتصالات" -> Icons.Default.PhoneAndroid
    "صحة" -> Icons.Default.LocalHospital
    "ترفيه" -> Icons.Default.Movie
    "اشتراكات" -> Icons.Default.Subscriptions
    "تعليم" -> Icons.Default.School
    "تحويلات" -> Icons.Default.SwapHoriz
    CASH_WITHDRAWAL_CATEGORY -> Icons.Default.LocalAtm
    SELF_TRANSFER_CATEGORY -> Icons.AutoMirrored.Filled.CompareArrows
    else -> Icons.Default.Category
}

fun sanitizeAmountInput(raw: String): String {
    var s = raw
    val ar = "٠١٢٣٤٥٦٧٨٩"
    val fa = "۰۱۲۳۴۵۶۷۸۹"
    ar.forEachIndexed { i, c -> s = s.replace(c, ('0' + i)) }
    fa.forEachIndexed { i, c -> s = s.replace(c, ('0' + i)) }
    s = s.filter { it.isDigit() || it == '.' }
    val firstDot = s.indexOf('.')
    return if (firstDot == -1) s else s.substring(0, firstDot + 1) + s.substring(firstDot + 1).replace(".", "")
}


private const val ARABIC_INDIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

/** Swaps ASCII 0-9 for Eastern Arabic-Indic digits; everything else (grouping
 * commas, decimal point, minus sign, letters) is left untouched. */
fun toArabicIndicDigits(s: String): String =
    s.map { c -> if (c in '0'..'9') ARABIC_INDIC_DIGITS[c - '0'] else c }.joinToString("")

@Composable
fun isArabicUi(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "ar"

// Locale-aware amount formatting for on-screen display: same grouping/decimal
// style as FinancialAdvisor.fmt(), rendered in Arabic-Indic digits when the
// app's display language is Arabic. Exports, search-matching and background
// notifications keep calling FinancialAdvisor.fmt() directly so stored/shared
// text (CSV, PDF, filters) stays plain ASCII.
@Composable
fun fmt(v: Double): String {
    val base = FinancialAdvisor.fmt(v)
    return if (isArabicUi()) toArabicIndicDigits(base) else base
}

// Locale-aware month name. Uses the same locale the rest of the UI is
// rendering with (via LocalConfiguration), so switching between Arabic and
// English also flips the month names on every dashboard/budget/report header.
@Composable
fun monthName(offset: Int, startDay: Int = 1): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val c = java.util.Calendar.getInstance().apply { timeInMillis = Dates.monthRange(offset, startDay).first }
    val symbols = java.text.DateFormatSymbols(locale)
    return symbols.months[c.get(java.util.Calendar.MONTH)] + " " + c.get(java.util.Calendar.YEAR)
}
