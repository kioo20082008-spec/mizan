package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import com.mizan.money.data.SELF_TRANSFER_CATEGORY
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType

// ============ DESIGN SYSTEM — "Ink & Lime" ============
// Neutral paper base, near-black ink surfaces for hero moments, one bold
// signature accent (lime on ink) plus a calm indigo brand color for actions.
val Paper       = Color(0xFFFAF9F6)
val PaperOuter  = Color(0xFFF0EEE7)
val White       = Color(0xFFFFFFFF)
val Line        = Color(0xFFE9E6DE)
val Ink         = Color(0xFF15141A)
val InkSoft     = Color(0xFF6F6D76)
val InkFaint    = Color(0xFFA4A2AA)
val Ink900      = Color(0xFF121017)
val Ink800      = Color(0xFF1E1B26)
val OnInkSoft   = Color(0xFFACA9B8)
val Indigo      = Color(0xFF4F46E5)
val IndigoDeep  = Color(0xFF3730A3)
val IndigoSoft  = Color(0xFFEEEEFD)
val Lime        = Color(0xFFD7F26B)
val Success     = Color(0xFF22C55E)
val Danger      = Color(0xFFF43F5E)
val Amber       = Color(0xFFF59E0B)
val Purple      = Color(0xFF8B5CF6)

val RadiusSm = 14.dp
val RadiusMd = 20.dp
val RadiusLg = 28.dp
val RadiusXl = 36.dp
val Pill     = 999.dp

private val Sans = FontFamily.Default

val Display    = TextStyle(fontFamily = Sans, fontSize = 38.sp, fontWeight = FontWeight.Black, color = Lime, letterSpacing = (-0.6).sp)
val H1         = TextStyle(fontFamily = Sans, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.3).sp)
val H2         = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
val Body       = TextStyle(fontFamily = Sans, fontSize = 14.sp, color = Ink)
val BodyMuted  = TextStyle(fontFamily = Sans, fontSize = 13.sp, color = InkSoft)
val Eyebrow    = TextStyle(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkFaint, letterSpacing = 0.6.sp)
val NumBold    = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.2).sp)

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
fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(Icons.Outlined.ReceiptLong, InkFaint, PaperOuter, size = 72.dp, iconSize = 30.dp, radius = RadiusMd)
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
                Text("${tx.category} • ${Dates.dayLabel(tx.timestamp)}", style = Eyebrow.copy(fontSize = 11.sp))
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

fun catColorSoft(cat: String): Color = catColor(cat).copy(alpha = 0.12f)

fun currencyLabel(code: String): String = when (code) {
    "SAR" -> "ر.س"
    "USD" -> "$"
    "EUR" -> "€"
    "AED" -> "د.إ"
    else -> code
}

fun catIcon(cat: String): ImageVector = when (cat) {
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
    SELF_TRANSFER_CATEGORY -> Icons.Default.CompareArrows
    else -> Icons.Default.Category
}

// Arabic-Indic digits (٠-٩) are common on Arabic-locale numeric keyboards and pass
// Char.isDigit() fine, but String.toDoubleOrNull() only understands ASCII digits —
// so a value typed with them looks accepted in the field but silently fails to
// parse, and for a budget field that means the save is either a no-op or (since
// the repo treats amount<=0 as "delete") silently wipes the saved budget.
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

// Named after the month the cycle *starts* in — with a custom start day the
// cycle can span two calendar months, so this can't just add `offset` months
// to today; it has to read the same range DashboardScreen/BudgetScreen/
// AdvisorScreen are actually showing.
fun monthName(offset: Int, startDay: Int = 1): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = Dates.monthRange(offset, startDay).first }
    val names = listOf(
        "يناير","فبراير","مارس","أبريل","مايو","يونيو",
        "يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر"
    )
    return names[c.get(java.util.Calendar.MONTH)] + " " + c.get(java.util.Calendar.YEAR)
}
