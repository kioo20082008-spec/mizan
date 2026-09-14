package com.mizan.money.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mizan.money.advisor.Advice
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.advisor.Level
import com.mizan.money.advisor.MonthSummary
import com.mizan.money.data.*
import com.mizan.money.sms.CategoryClassifier
import kotlin.math.abs

// ============ DESIGN SYSTEM — "Ink & Lime" ============
// Neutral paper base, near-black ink surfaces for hero moments, one bold
// signature accent (lime on ink) plus a calm indigo brand color for actions.
private val Paper       = Color(0xFFFAF9F6)
private val PaperOuter  = Color(0xFFF0EEE7)
private val White        = Color(0xFFFFFFFF)
private val Line        = Color(0xFFE9E6DE)
private val Ink         = Color(0xFF15141A)
private val InkSoft     = Color(0xFF6F6D76)
private val InkFaint    = Color(0xFFA4A2AA)
private val Ink900      = Color(0xFF121017)
private val Ink800      = Color(0xFF1E1B26)
private val OnInkSoft   = Color(0xFFACA9B8)
private val Indigo      = Color(0xFF4F46E5)
private val IndigoDeep  = Color(0xFF3730A3)
private val IndigoSoft  = Color(0xFFEEEEFD)
private val Lime        = Color(0xFFD7F26B)
private val Success     = Color(0xFF22C55E)
private val Danger      = Color(0xFFF43F5E)
private val Amber       = Color(0xFFF59E0B)
private val Purple      = Color(0xFF8B5CF6)

private val RadiusSm = 14.dp
private val RadiusMd = 20.dp
private val RadiusLg = 28.dp
private val RadiusXl = 36.dp
private val Pill     = 999.dp

private val Sans = FontFamily.Default
private val Mono = FontFamily.Monospace

private val Display    = TextStyle(fontFamily = Sans, fontSize = 38.sp, fontWeight = FontWeight.Black, color = Lime, letterSpacing = (-0.6).sp)
private val H1         = TextStyle(fontFamily = Sans, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.3).sp)
private val H2         = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
private val Body       = TextStyle(fontFamily = Sans, fontSize = 14.sp, color = Ink)
private val BodyMuted  = TextStyle(fontFamily = Sans, fontSize = 13.sp, color = InkSoft)
private val Eyebrow    = TextStyle(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkFaint, letterSpacing = 0.6.sp)
private val NumBold    = TextStyle(fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)

// ============ ENTRY ============
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val vm: MainViewModel = viewModel()
    var hasSms by remember { mutableStateOf(checkSms(ctx)) }
    var scanned by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasSms = checkSms(ctx) }

    LaunchedEffect(hasSms) {
        if (hasSms && !scanned) { scanned = true; vm.scanInbox() }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Paper,
            surface = White,
            surfaceTint = Color.Transparent,
            primary = Indigo,
            onPrimary = White,
            onBackground = Ink,
            onSurface = Ink,
            error = Danger,
            onError = White,
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = PaperOuter) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Surface(
                        Modifier.fillMaxWidth().fillMaxHeight(),
                        color = Paper
                    ) {
                        if (!hasSms) PermissionScreen {
                            launcher.launch(arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                            ))
                        } else {
                            RootScaffold(vm)
                        }
                    }
                }
            }
        }
    }
}

private fun checkSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

// ============ SHARED PRIMITIVES ============
@Composable
private fun IconBadge(
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
private fun SoftCard(
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

// ============ PERMISSION / ONBOARDING ============
@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))

        Box(
            Modifier.size(104.dp).clip(RoundedCornerShape(RadiusXl))
                .background(Ink900)
                .border(1.dp, Lime.copy(alpha = 0.25f), RoundedCornerShape(RadiusXl))
                .shadow(20.dp, RoundedCornerShape(RadiusXl), ambientColor = Ink900.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Savings, null, Modifier.size(46.dp), tint = Lime)
        }

        Spacer(Modifier.height(22.dp))
        Text("ميزان", style = H1.copy(fontSize = 34.sp, fontWeight = FontWeight.Black))
        Spacer(Modifier.height(6.dp))
        Text("إدارة مالية بخصوصية تامة", style = BodyMuted)

        Spacer(Modifier.height(44.dp))

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(RadiusLg))
                .background(White)
                .border(1.dp, Line, RoundedCornerShape(RadiusLg))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            TrustPoint(Icons.Default.MarkEmailRead, Indigo, "اقرأ رسائل بنكك", "لتصنيف مصاريفك تلقائياً")
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            TrustPoint(Icons.Default.Insights, Amber, "حلّل عاداتك", "اعرض أنماط صرفك بوضوح")
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            TrustPoint(Icons.Default.Lock, Purple, "خصوصيتك أولاً", "البيانات على جهازك فقط")
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(58.dp)
                .shadow(16.dp, RoundedCornerShape(RadiusMd), ambientColor = Ink900.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(RadiusMd),
            colors = ButtonDefaults.buttonColors(containerColor = Ink900, contentColor = Lime)
        ) {
            Text("ابدأ الآن", style = Body.copy(color = Lime, fontWeight = FontWeight.Bold, fontSize = 16.sp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp).graphicsLayer(rotationZ = 180f), tint = Lime)
        }
    }
}

@Composable
private fun TrustPoint(icon: ImageVector, accent: Color, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, accent, accent.copy(alpha = 0.12f), size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = H2.copy(fontSize = 15.sp))
            Spacer(Modifier.height(2.dp))
            Text(desc, style = Eyebrow.copy(fontSize = 12.sp))
        }
    }
}

// ============ SCAFFOLD ============
@Composable
private fun RootScaffold(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppHeader()
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> DashboardScreen(vm)
                    1 -> TransactionsScreen(vm)
                    2 -> BudgetScreen(vm)
                    else -> AdvisorScreen(vm)
                }
            }
        }
        BottomNav(tab, Modifier.align(Alignment.BottomCenter)) { tab = it }
    }
}

@Composable
private fun AppHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Default.Savings, Lime, Ink900, size = 46.dp, radius = RadiusSm)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("أهلاً بك 👋", style = Eyebrow)
            Text("ميزان", style = H1)
        }
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(RadiusSm)).background(White)
                .border(1.dp, Line, RoundedCornerShape(RadiusSm)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Settings, null, Modifier.size(20.dp), tint = InkSoft)
        }
    }
}

// ============ BOTTOM NAV — glass pill with a sliding lime indicator ============
@Composable
private fun BottomNav(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("الرئيسية",  Icons.Filled.Home,        0),
        Triple("العمليات",  Icons.Filled.ReceiptLong, 1),
        Triple("الميزانية", Icons.Filled.AccountBalanceWallet, 2),
        Triple("المستشار",  Icons.Filled.PieChart,    3)
    )
    Box(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(20.dp, RoundedCornerShape(Pill), ambientColor = Ink900.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(Pill))
                .background(Ink900)
                .padding(6.dp)
        ) {
            val itemWidth = maxWidth / items.size
            val indicatorX by animateDpAsState(itemWidth * selected, tween(320), label = "nav")
            Box(
                Modifier
                    .offset(x = indicatorX)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Pill))
                    .background(Lime)
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.SpaceBetween) {
                items.forEach { (label, icon, idx) ->
                    val isSel = selected == idx
                    val tint by animateColorAsState(if (isSel) Ink900 else OnInkSoft, tween(220), label = "tint")
                    Row(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clickable { onSelect(idx) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, label, Modifier.size(20.dp), tint = tint)
                        if (isSel) {
                            Spacer(Modifier.width(6.dp))
                            Text(label, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ============ DASHBOARD ============
@Composable
private fun DashboardScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    var offset by remember { mutableIntStateOf(0) }

    val range = remember(offset) { Dates.monthRange(offset) }
    val summary = remember(txs, offset) { FinancialAdvisor.summarize(txs, range.first, range.last) }
    val budget = budgets.firstOrNull {
        it.monthKey == Dates.monthKey(offset) && it.category == TOTAL_BUDGET
    }?.limitAmount ?: 0.0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            BalanceCard(summary, offset, onPrev = { offset-- }, onNext = { if (offset < 0) offset++ })
        }

        if (budget > 0) {
            item { BudgetStatusCard(budget, summary.spent) }
        }

        if (summary.categoryTotals.isNotEmpty()) {
            item {
                Text("الأكثر استهلاكاً", style = H2, modifier = Modifier.padding(horizontal = 4.dp))
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(summary.categoryTotals.take(6)) { cat -> CategoryChip(cat) }
                }
            }
        }

        if (txs.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("أحدث العمليات", style = H2, modifier = Modifier.weight(1f))
                    Text("عرض الكل", style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold))
                }
            }
            items(txs.take(3)) { tx ->
                TransactionCard(tx, onClick = { })
            }
        }

        if (txs.isEmpty()) {
            item { EmptyState("لم نرصد عمليات بعد") }
        }
    }
}

@Composable
private fun BalanceCard(s: MonthSummary, offset: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusXl))
            .background(Brush.linearGradient(listOf(Ink800, Ink900)))
    ) {
        Box(
            Modifier.size(240.dp).align(Alignment.TopEnd).offset(x = 80.dp, y = (-100).dp)
                .clip(CircleShape).background(Indigo.copy(alpha = 0.35f))
        )
        Box(
            Modifier.size(140.dp).align(Alignment.BottomStart).offset(x = (-50).dp, y = 40.dp)
                .clip(CircleShape).background(Lime.copy(alpha = 0.10f))
        )
        Column(Modifier.padding(26.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = White, modifier = Modifier.size(16.dp))
                }
                Text(
                    monthName(offset),
                    style = Body.copy(color = White, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(Pill))
                        .background(White.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                )
                IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, null,
                        tint = White.copy(alpha = if (offset < 0) 1f else 0.3f),
                        modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 180f)
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(Pill)).background(Lime.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shield, null, Modifier.size(12.dp), tint = Lime)
                    Spacer(Modifier.width(4.dp))
                    Text("محلي ١٠٠٪", style = Eyebrow.copy(color = Lime, fontSize = 11.sp))
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("الرصيد المتبقي المتاح", style = Body.copy(color = OnInkSoft))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(FinancialAdvisor.fmt(abs(s.net)), style = Display)
                Spacer(Modifier.width(6.dp))
                Text(
                    "ر.س", style = Body.copy(color = OnInkSoft, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(White.copy(alpha = 0.08f)))
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.ArrowDownward, Lime, White.copy(alpha = 0.08f), size = 38.dp, iconSize = 18.dp, radius = RadiusSm)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("إجمالي الدخل", style = Eyebrow.copy(color = OnInkSoft, fontSize = 11.sp))
                        Text(FinancialAdvisor.fmt(s.income), style = Body.copy(color = White, fontWeight = FontWeight.Bold))
                    }
                }
                Box(Modifier.width(1.dp).height(32.dp).background(White.copy(alpha = 0.08f)))
                Spacer(Modifier.width(16.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.ArrowUpward, Color(0xFFFB7185), White.copy(alpha = 0.08f), size = 38.dp, iconSize = 18.dp, radius = RadiusSm)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("إجمالي الصرف", style = Eyebrow.copy(color = OnInkSoft, fontSize = 11.sp))
                        Text(FinancialAdvisor.fmt(s.spent), style = Body.copy(color = White, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetStatusCard(budget: Double, spent: Double) {
    val pct = (spent / budget).coerceIn(0.0, 1.0).toFloat()
    val anim by animateFloatAsState(pct, tween(800), label = "b")
    val overBudget = spent > budget

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("استهلاك ميزانية الشهر", style = H2, modifier = Modifier.weight(1f))
            Text(
                "${(spent / budget * 100).toInt()}٪",
                style = Body.copy(color = if (overBudget) Danger else Indigo, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(if (overBudget) Danger.copy(alpha = 0.1f) else IndigoSoft)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(Pill))
                .background(PaperOuter)
        ) {
            Box(
                Modifier.fillMaxWidth(anim).fillMaxHeight()
                    .clip(RoundedCornerShape(Pill))
                    .background(
                        Brush.horizontalGradient(
                            if (overBudget) listOf(Danger, Danger) else listOf(Indigo, IndigoDeep)
                        )
                    )
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("صرفت: ${FinancialAdvisor.fmt(spent)} ر.س", style = BodyMuted.copy(fontSize = 12.sp))
            Spacer(Modifier.weight(1f))
            Text("السقف: ${FinancialAdvisor.fmt(budget)} ر.س", style = BodyMuted.copy(fontSize = 12.sp))
        }
    }
}

@Composable
private fun CategoryChip(cat: com.mizan.money.advisor.CategoryTotal) {
    Column(
        Modifier.width(136.dp)
            .clip(RoundedCornerShape(RadiusMd))
            .background(White)
            .border(1.dp, Line, RoundedCornerShape(RadiusMd))
            .padding(14.dp)
    ) {
        IconBadge(catIcon(cat.category), catColor(cat.category), catColorSoft(cat.category), size = 40.dp, iconSize = 18.dp)
        Spacer(Modifier.height(10.dp))
        Text(cat.category, style = H2.copy(fontSize = 13.sp), maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(FinancialAdvisor.fmt(cat.amount) + " ر.س", style = NumBold.copy(fontSize = 12.sp))
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(Pill)).background(PaperOuter)
        ) {
            Box(
                Modifier.fillMaxWidth(cat.share.toFloat()).fillMaxHeight()
                    .clip(RoundedCornerShape(Pill)).background(catColor(cat.category))
            )
        }
    }
}

@Composable
private fun TransactionCard(tx: TransactionEntity, onClick: () -> Unit) {
    val isExpense = tx.type == TxType.EXPENSE
    val sign = if (isExpense) "-" else "+"
    val amtColor = if (isExpense) Ink else Success

    SoftCard(Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(catIcon(tx.category), catColor(tx.category), catColorSoft(tx.category), size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.merchant ?: "غير معروف", style = H2.copy(fontSize = 14.sp))
                Spacer(Modifier.height(2.dp))
                Text("${tx.category} • ${Dates.dayLabel(tx.timestamp)}", style = Eyebrow.copy(fontSize = 11.sp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$sign${FinancialAdvisor.fmt(tx.amount)}",
                    style = NumBold.copy(color = amtColor, fontSize = 16.sp)
                )
                Text("ر.س", style = Eyebrow.copy(fontSize = 10.sp))
            }
        }
    }
}

// ============ TRANSACTIONS ============
@Composable
private fun TransactionsScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val filtered = remember(txs, query) {
        if (query.isBlank()) txs
        else txs.filter {
            (it.merchant ?: "").contains(query, ignoreCase = true) ||
            it.category.contains(query, ignoreCase = true) ||
            it.amount.toString().contains(query)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("سجل العمليات", style = H1)
                    Text("مستخرجة تلقائياً من رسائل البنك", style = Eyebrow)
                }
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(RadiusSm)).background(Ink900)
                        .clickable { showAdd = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, "إضافة", tint = Lime, modifier = Modifier.size(22.dp)) }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusMd))
                    .background(White)
                    .border(1.dp, Line, RoundedCornerShape(RadiusMd))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = InkFaint)
                Spacer(Modifier.width(10.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث عن عملية، جهة، أو تصنيف...", style = BodyMuted) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = Body
                )
            }
        }
        if (filtered.isEmpty()) {
            item { EmptyState("لا توجد عمليات مطابقة") }
        } else {
            items(filtered, key = { it.id }) { tx ->
                TransactionCard(tx, onClick = { selected = tx })
            }
        }
    }

    if (selected != null) {
        TxDetailDialog(
            tx = selected!!,
            onDismiss = { selected = null },
            onDelete = { vm.delete(selected!!); selected = null }
        )
    }
    if (showAdd) {
        AddDialog(
            onDismiss = { showAdd = false },
            onSave = { a, m, c, t -> vm.addManual(a, m, c, t); showAdd = false }
        )
    }
}

@Composable
private fun TxDetailDialog(tx: TransactionEntity, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text("تفاصيل العملية", style = H2) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.AccountBalance, InkSoft, PaperOuter, size = 32.dp, iconSize = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(tx.bankName ?: "بنك", style = Body.copy(fontWeight = FontWeight.Medium))
                    Spacer(Modifier.weight(1f))
                    Text(Dates.dayLabel(tx.timestamp), style = Eyebrow)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tx.merchant ?: "غير معروف", style = H2.copy(fontSize = 18.sp))
                    Spacer(Modifier.height(4.dp))
                    Text(tx.category, style = BodyMuted)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${if (tx.type == TxType.EXPENSE) "-" else "+"}${FinancialAdvisor.fmt(tx.amount)} ر.س",
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black,
                            color = if (tx.type == TxType.EXPENSE) Danger else Success)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Spacer(Modifier.height(16.dp))
                Text("الرسالة الأصلية", style = Body.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                Text(tx.rawSms, style = BodyMuted, modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(PaperOuter)
                    .padding(12.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("حذف", style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق", style = Body.copy(color = InkSoft)) }
        }
    )
}

@Composable
private fun AddDialog(onDismiss: () -> Unit, onSave: (Double, String, String, TxType) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CategoryClassifier.categories.first()) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text("إضافة عملية يدوية", style = H2) },
        text = {
            Column {
                SegmentedToggle(
                    options = listOf("مصروف" to Danger, "دخل" to Success),
                    selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                    onSelect = { type = if (it == 0) TxType.EXPENSE else TxType.INCOME }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("المبلغ (ر.س)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("الجهة / التاجر") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                Text("التصنيف", style = Eyebrow)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryClassifier.categories.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { c ->
                                Box(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(RadiusSm))
                                        .background(if (category == c) IndigoSoft else PaperOuter)
                                        .clickable { category = c }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        c,
                                        style = Eyebrow.copy(
                                            color = if (category == c) Indigo else InkSoft,
                                            fontWeight = if (category == c) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amount.toDoubleOrNull()?.let { onSave(it, merchant, category, type) }
            }) { Text("حفظ", style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", style = Body.copy(color = InkSoft)) }
        }
    )
}

@Composable
private fun SegmentedToggle(options: List<Pair<String, Color>>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusMd))
            .background(PaperOuter)
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, (label, color) ->
            val isSel = i == selectedIndex
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
                    style = Body.copy(
                        color = if (isSel) color else InkSoft,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
        }
    }
}

// ============ BUDGET ============
@Composable
private fun BudgetScreen(vm: MainViewModel) {
    val budgets by vm.budgets.collectAsState()
    val monthKey = Dates.monthKey(0)
    val txs by vm.transactions.collectAsState()
    val range = Dates.monthRange(0)
    val summary = remember(txs) { FinancialAdvisor.summarize(txs, range.first, range.last) }

    var totalInput by remember {
        mutableStateOf(
            budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }
                ?.limitAmount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        )
    }
    var catInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(budgets) {
        catInputs = CategoryClassifier.categories.associateWith { c ->
            budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                ?.limitAmount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("الميزانية والتصنيفات", style = H1)
            Text("راقب إنفاقك وقارنه بالحدود المحددة", style = Eyebrow)
        }

        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الميزانية الإجمالية للشهر", style = Eyebrow)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(totalInput.ifBlank { "0" }, style = H1.copy(fontSize = 26.sp))
                            Spacer(Modifier.width(4.dp))
                            Text("ر.س", style = BodyMuted)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = totalInput,
                    onValueChange = { totalInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("الحد الشهري") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) } },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(RadiusSm),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink900, contentColor = Lime)
                ) { Text("حفظ", style = Body.copy(color = Lime, fontWeight = FontWeight.Bold)) }
            }
        }

        item {
            Text("الميزانية لكل تصنيف", style = H2, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }

        items(CategoryClassifier.categories) { cat ->
            val spentInCat = summary.categoryTotals.firstOrNull { it.category == cat }?.amount ?: 0.0
            val limit = catInputs[cat]?.toDoubleOrNull() ?: 0.0
            val pct = if (limit > 0) (spentInCat / limit).coerceIn(0.0, 1.0).toFloat() else 0f
            val isOver = limit > 0 && spentInCat > limit

            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(catIcon(cat), catColor(cat), catColorSoft(cat), size = 40.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(cat, style = H2.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                    Text(
                        "${FinancialAdvisor.fmt(spentInCat)}" + (if (limit > 0) " / ${FinancialAdvisor.fmt(limit)}" else ""),
                        style = NumBold.copy(fontSize = 13.sp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(Pill))
                        .background(PaperOuter)
                ) {
                    Box(
                        Modifier.fillMaxWidth(pct).fillMaxHeight()
                            .clip(RoundedCornerShape(Pill))
                            .background(if (isOver) Danger else catColor(cat))
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = catInputs[cat] ?: "",
                        onValueChange = { v ->
                            catInputs = catInputs + (cat to v.filter { ch -> ch.isDigit() || ch == '.' })
                        },
                        placeholder = { Text("0", style = Eyebrow) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(RadiusSm)).background(Indigo)
                            .clickable {
                                (catInputs[cat]?.toDoubleOrNull() ?: 0.0).let { vm.setBudget(monthKey, cat, it) }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Check, "حفظ", tint = White, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

// ============ ADVISOR ============
@Composable
private fun AdvisorScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val range = Dates.monthRange(0)
    val summary = remember(txs) { FinancialAdvisor.summarize(txs, range.first, range.last) }
    val budget = budgets.firstOrNull {
        it.monthKey == Dates.monthKey(0) && it.category == TOTAL_BUDGET
    }?.limitAmount ?: 0.0
    val advice = remember(summary, budget, txs) {
        FinancialAdvisor.advise(summary, budget, txs, range.first, range.last)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusXl))
                    .background(Brush.linearGradient(listOf(Ink800, Ink900)))
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Lightbulb, Lime, White.copy(alpha = 0.08f), size = 56.dp, iconSize = 28.dp, radius = RadiusMd)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("المستشار المالي", style = H2.copy(color = White, fontSize = 17.sp))
                        Spacer(Modifier.height(4.dp))
                        Text("تحليل ذكي ومحلي لنمط إنفاقك", style = BodyMuted.copy(color = OnInkSoft))
                    }
                }
            }
        }
        items(advice) { a -> AdviceRow(a) }
    }
}

@Composable
private fun AdviceRow(a: Advice) {
    val color = when (a.level) {
        Level.DANGER -> Danger
        Level.WARN   -> Amber
        Level.GOOD   -> Success
        Level.INFO   -> Indigo
    }
    val icon = when (a.level) {
        Level.DANGER -> Icons.Default.Warning
        Level.WARN   -> Icons.Default.Info
        Level.GOOD   -> Icons.Default.CheckCircle
        Level.INFO   -> Icons.Default.Lightbulb
    }
    SoftCard {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier.width(4.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(Pill))
                    .background(color)
            )
            Spacer(Modifier.width(14.dp))
            IconBadge(icon, color, color.copy(alpha = 0.12f), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(a.title, style = H2.copy(fontSize = 14.sp))
                Spacer(Modifier.height(6.dp))
                Text(a.body, style = BodyMuted)
            }
        }
    }
}

// ============ HELPERS ============
@Composable
private fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(Icons.Outlined.ReceiptLong, InkFaint, PaperOuter, size = 72.dp, iconSize = 30.dp, radius = RadiusMd)
        Spacer(Modifier.height(14.dp))
        Text(text, style = BodyMuted)
    }
}

private fun catColor(cat: String): Color = when (cat) {
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
    else -> InkFaint
}

private fun catColorSoft(cat: String): Color = catColor(cat).copy(alpha = 0.12f)

private fun catIcon(cat: String): ImageVector = when (cat) {
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
    else -> Icons.Default.Category
}

private fun monthName(offset: Int): String {
    val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset) }
    val names = listOf(
        "يناير","فبراير","مارس","أبريل","مايو","يونيو",
        "يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر"
    )
    return names[c.get(java.util.Calendar.MONTH)] + " " + c.get(java.util.Calendar.YEAR)
}
