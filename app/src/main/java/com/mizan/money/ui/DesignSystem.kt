package com.mizan.money.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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

val RadiusSm = 14.dp
val RadiusMd = 20.dp
val RadiusLg = 28.dp
val RadiusXl = 36.dp
val Pill     = 999.dp

private val Sans = FontFamily.Default

val Display: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 38.sp, fontWeight = FontWeight.Black, color = Lime)
val H1: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
val H2: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
val Body: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 14.sp, color = Ink)
val BodyMuted: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 13.sp, color = InkSoft)
val Eyebrow: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkFaint)
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
    radius: Dp = RadiusSm
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
            .shadow(3.dp, RoundedCornerShape(RadiusLg), ambientColor = Ink.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(RadiusLg))
            .background(White)
            .border(1.dp, Line, RoundedCornerShape(RadiusLg))
            .padding(18.dp),
        content = content
    )
}

@Composable
fun TabSwitcher(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusMd))
            .background(PaperOuter)
            .padding(4.dp)
    ) {
        items.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(if (isSel) White else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = Eyebrow.copy(
                        fontSize = 12.sp,
                        color = if (isSel) Indigo else InkSoft,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold
                    )
                )
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

@Composable
fun TransactionCard(tx: TransactionEntity, onClick: () -> Unit) {
    val isExpense = tx.type == TxType.EXPENSE
    val sign = if (isExpense) "-" else "+"
    val amtColor = if (tx.isSelfTransfer) InkFaint else if (isExpense) Ink else Success

    SoftCard(Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(catIcon(tx.category), catColor(tx.category), catColorSoft(tx.category), size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.merchant ?: "غير معروف", style = H2.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("${categoryDisplay(tx.category)} • ${Dates.dayLabel(tx.timestamp)}", style = Eyebrow.copy(fontSize = 11.sp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$sign${FinancialAdvisor.fmt(tx.amount)}",
                    style = NumBold.copy(color = amtColor, fontSize = 16.sp)
                )
                Text(currencyLabel(tx.currency), style = Eyebrow.copy(fontSize = 10.sp))
            }
        }
    }
}

// ============ FORMATTING HELPERS ============
// Maps the Arabic category key (stored in the DB) to a display name in the
// user's chosen locale. The Arabic key itself never changes — it stays the
// join point between parser rules, budgets, and stored transactions.

@Composable
fun categoryDisplay(cat: String): String = when (cat) {
    "طعام وشراب" -> stringResource(R.string.cat_food)
    "بقالة" -> stringResource(R.string.cat_groceries)
    "مواصلات" -> stringResource(R.string.cat_transport)
    "وقود" -> stringResource(R.string.cat_fuel)
    "تسوق" -> stringResource(R.string.cat_shopping)
    "فواتير" -> stringResource(R.string.cat_bills)
    "اتصالات" -> stringResource(R.string.cat_telecom)
    "صحة" -> stringResource(R.string.cat_health)
    "ترفيه" -> stringResource(R.string.cat_entertainment)
    "اشتراكات" -> stringResource(R.string.cat_subscriptions)
    "تعليم" -> stringResource(R.string.cat_education)
    "تحويلات" -> stringResource(R.string.cat_transfers)
    "أخرى" -> stringResource(R.string.cat_other)
    CASH_WITHDRAWAL_CATEGORY -> stringResource(R.string.cat_cash)
    SELF_TRANSFER_CATEGORY -> stringResource(R.string.cat_self_transfer)
    else -> cat
}
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
