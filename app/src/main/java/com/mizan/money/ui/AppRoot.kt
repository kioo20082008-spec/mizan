package com.mizan.money.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

// ==================== COLORS ====================
private val Bg        = Color(0xFF0B0B10)
private val Surface1  = Color(0xFF14141C)
private val Surface2  = Color(0xFF1C1C26)
private val Glass     = Color(0x0DFFFFFF)  // white 5%
private val GlassBord = Color(0x14FFFFFF)  // white 8%
private val TextPri   = Color(0xFFFFFFFF)
private val TextSec   = Color(0xFFA1A1AA)
private val TextTer   = Color(0xFF71717A)
private val Violet    = Color(0xFF8B5CF6)
private val Pink      = Color(0xFFEC4899)
private val Emerald   = Color(0xFF10B981)
private val Rose      = Color(0xFFF43F5E)
private val Amber     = Color(0xFFF59E0B)

// ==================== TYPE ====================
private val Sans = FontFamily.Default
private val Mono = FontFamily.Monospace

private val T_Display = TextStyle(fontFamily = Sans, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPri, letterSpacing = (-0.5).sp)
private val T_H1      = TextStyle(fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPri)
private val T_H2      = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPri)
private val T_Body    = TextStyle(fontFamily = Sans, fontSize = 14.sp, color = TextSec, lineHeight = 20.sp)
private val T_Small   = TextStyle(fontFamily = Sans, fontSize = 12.sp, color = TextTer)
private val T_Big     = TextStyle(fontFamily = Mono, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TextPri, letterSpacing = (-1).sp)
private val T_Num     = TextStyle(fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPri)

// ==================== ENTRY ====================
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
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Surface1,
            surfaceTint = Color.Transparent,
            primary = Violet,
            onPrimary = TextPri,
            onBackground = TextPri,
            onSurface = TextPri,
            error = Rose,
            onError = TextPri,
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = Bg) {
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

private fun checkSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

// ==================== PERMISSION ====================
@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        // Big glowing orb
        Box(
            Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Violet.copy(alpha = 0.35f), Color.Transparent),
                        radius = size.minDimension / 2f
                    )
                )
            }
            Box(
                Modifier.size(160.dp).clip(CircleShape).background(
                    Brush.linearGradient(
                        colors = listOf(Violet, Pink),
                        start = Offset(0f, 0f),
                        end = Offset(300f, 300f)
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet, null,
                    Modifier.size(64.dp), tint = TextPri
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("ميزان", style = T_Display)
        Spacer(Modifier.height(8.dp))
        Text("مديرك المالي الذكي", style = T_Body)

        Spacer(Modifier.height(40.dp))

        GlassCard {
            PermPoint("اقرأ رسائل بنكك", "لتصنيف مصاريفك تلقائياً")
            Spacer(Modifier.height(16.dp))
            PermPoint("حلّل عاداتك", "واعرض لك أنماط صرفك بوضوح")
            Spacer(Modifier.height(16.dp))
            PermPoint("خصوصيتك أولاً", "كل البيانات على جهازك فقط")
        }

        Spacer(Modifier.weight(1f))

        GradientButton(
            text = "ابدأ الآن",
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PermPoint(title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Violet.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Violet))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = T_H2.copy(fontSize = 14.sp))
            Spacer(Modifier.height(2.dp))
            Text(desc, style = T_Small)
        }
    }
}

// ==================== SCAFFOLD ====================
@Composable
private fun RootScaffold(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            0 -> DashboardScreen(vm)
            1 -> TransactionsScreen(vm)
            2 -> BudgetScreen(vm)
            else -> AdvisorScreen(vm)
        }
        FloatingNav(tab) { tab = it }
    }
}

@Composable
private fun FloatingNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("الرئيسية", Icons.Filled.Home, 0),
        Triple("العمليات", Icons.Filled.ReceiptLong, 1),
        Triple("الميزانية", Icons.Filled.Savings, 2),
        Triple("المستشار", Icons.Filled.AutoAwesome, 3)
    )
    Box(
        Modifier.fillMaxSize().padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(40.dp))
                .background(Surface2)
                .border(0.5.dp, GlassBord, RoundedCornerShape(40.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { (label, icon, idx) ->
                val isSel = selected == idx
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSel) {
                        Box(
                            Modifier.matchParentSize()
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Violet, Pink))
                                )
                        )
                    }
                    Icon(
                        icon, label,
                        Modifier.size(22.dp),
                        tint = if (isSel) TextPri else TextTer
                    )
                }
            }
        }
    }
}

// ==================== REUSABLE ====================
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Glass)
            .border(0.5.dp, GlassBord, RoundedCornerShape(24.dp))
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Violet, Pink)))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = T_H2.copy(fontWeight = FontWeight.Bold))
    }
}

// ==================== DASHBOARD ====================
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { TopBar(offset, onPrev = { offset-- }, onNext = { if (offset < 0) offset++ }) }
        item { BalanceOrb(summary) }
        item { QuickStats(summary) }
        if (budget > 0) item { BudgetRing(budget, summary.spent) }
        if (summary.categoryTotals.isNotEmpty()) {
            item { CategoryCircles(summary.categoryTotals, summary.spent) }
        }
        if (txs.isNotEmpty()) {
            item {
                GlassCard {
                    Text("آخر العمليات", style = T_H2)
                    Spacer(Modifier.height(14.dp))
                    txs.take(3).forEachIndexed { i, tx ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        SimpleTx(tx)
                    }
                }
            }
        }
        if (txs.isEmpty()) item { EmptyOrb("لم نرصد عمليات بعد") }
    }
}

@Composable
private fun TopBar(offset: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Violet, Pink))),
            contentAlignment = Alignment.Center
        ) {
            Text("م", style = T_H2.copy(fontSize = 18.sp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ميزان", style = T_H1)
            Text(monthName(offset), style = T_Small)
        }
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = if (offset < 0) TextPri else TextTer
            )
        }
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = TextPri)
        }
    }
}

@Composable
private fun BalanceOrb(s: MonthSummary) {
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }
    val scale by animateFloatAsState(
        if (animateIn) 1f else 0.85f,
        tween(700), label = "orb"
    )

    Box(
        Modifier.fillMaxWidth().height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glow behind
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Violet.copy(alpha = 0.4f), Color.Transparent),
                    radius = size.minDimension / 2.2f
                )
            )
        }
        // Main orb
        Box(
            Modifier
                .size((240 * scale).dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Violet, Pink, Color(0xFFFF6B9D)),
                        start = Offset(0f, 0f),
                        end = Offset(400f, 400f)
                    )
                )
                .border(1.dp, Color(0x33FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("صافي الشهر", style = T_Small.copy(color = Color(0xCCFFFFFF)))
                Spacer(Modifier.height(10.dp))
                Text(
                    FinancialAdvisor.fmt(abs(s.net)),
                    style = T_Big.copy(fontSize = 42.sp)
                )
                Spacer(Modifier.height(4.dp))
                Text("ر.س", style = T_Small.copy(color = Color(0xCCFFFFFF)))
            }
        }
    }
}

@Composable
private fun QuickStats(s: MonthSummary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatPill(
            "الدخل",
            FinancialAdvisor.fmt(s.income),
            Emerald,
            Modifier.weight(1f)
        )
        StatPill(
            "المصروف",
            FinancialAdvisor.fmt(s.spent),
            Rose,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatPill(label: String, value: String, tint: Color, mod: Modifier = Modifier) {
    Row(
        mod
            .clip(RoundedCornerShape(20.dp))
            .background(Glass)
            .border(0.5.dp, GlassBord, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = T_Small)
            Spacer(Modifier.height(2.dp))
            Text(value, style = T_Num.copy(fontSize = 13.sp))
        }
    }
}

@Composable
private fun BudgetRing(budget: Double, spent: Double) {
    val pct = (spent / budget).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        spent > budget -> Rose
        pct > 0.8f -> Amber
        else -> Emerald
    }
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }
    val anim by animateFloatAsState(if (animateIn) pct else 0f, tween(900), label = "ring")

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    // Track
                    drawArc(
                        color = Surface2,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // Progress
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * anim,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(pct * 100).toInt()}%",
                        style = T_H1.copy(fontSize = 24.sp)
                    )
                    Text("مستهلك", style = T_Small.copy(fontSize = 10.sp))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("الميزانية الشهرية", style = T_H2)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${FinancialAdvisor.fmt(spent)} / ${FinancialAdvisor.fmt(budget)}",
                    style = T_Num.copy(fontSize = 13.sp)
                )
                Spacer(Modifier.height(4.dp))
                Text("ر.س", style = T_Small)
            }
        }
    }
}

@Composable
private fun CategoryCircles(cats: List<com.mizan.money.advisor.CategoryTotal>, total: Double) {
    GlassCard {
        Text("التصنيفات", style = T_H2)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            cats.take(5).forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(catColor(cat.category).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            catIcon(cat.category), null,
                            Modifier.size(20.dp),
                            tint = catColor(cat.category)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat.category, style = T_Body.copy(color = TextPri, fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.fillMaxWidth().height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Surface2)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(cat.share.toFloat())
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(catColor(cat.category), catColor(cat.category).copy(alpha = 0.6f))
                                        )
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(FinancialAdvisor.fmt(cat.amount), style = T_Num.copy(fontSize = 13.sp))
                }
            }
        }
    }
}

@Composable
private fun SimpleTx(tx: TransactionEntity) {
    val sign = if (tx.type == TxType.EXPENSE) "-" else "+"
    val color = if (tx.type == TxType.EXPENSE) Rose else Emerald
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(catColor(tx.category).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(catIcon(tx.category), null, Modifier.size(16.dp), tint = catColor(tx.category))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.merchant ?: "غير معروف", style = T_Body.copy(color = TextPri, fontWeight = FontWeight.Medium))
            Text(tx.category, style = T_Small)
        }
        Text("$sign${FinancialAdvisor.fmt(tx.amount)}",
            style = T_Num.copy(fontSize = 13.sp, color = color))
    }
}

@Composable
private fun EmptyOrb(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(Glass),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.ReceiptLong, null, Modifier.size(28.dp), tint = TextTer)
        }
        Spacer(Modifier.height(16.dp))
        Text(text, style = T_Body)
    }
}

// ==================== TRANSACTIONS ====================
@Composable
private fun TransactionsScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val grouped = remember(txs) { txs.groupBy { Dates.dayLabel(it.timestamp) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("العمليات", style = T_H1)
            }
            grouped.forEach { (day, list) ->
                item {
                    Column {
                        Text(day, style = T_Small, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                        GlassCard {
                            list.forEachIndexed { i, tx ->
                                if (i > 0) {
                                    Spacer(Modifier.height(12.dp))
                                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(GlassBord))
                                    Spacer(Modifier.height(12.dp))
                                }
                                TxRow(tx, onDelete = { vm.delete(tx) })
                            }
                        }
                    }
                }
            }
            if (txs.isEmpty()) item { EmptyOrb("لا توجد عمليات بعد") }
        }
        // FAB
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Violet, Pink)))
                .clickable { showAdd = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, "إضافة", tint = TextPri, modifier = Modifier.size(28.dp))
        }
    }

    if (showAdd) AddDialog(
        onDismiss = { showAdd = false },
        onSave = { a, m, c, t -> vm.addManual(a, m, c, t); showAdd = false }
    )
}

@Composable
private fun TxRow(tx: TransactionEntity, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sign = if (tx.type == TxType.EXPENSE) "-" else "+"
    val color = if (tx.type == TxType.EXPENSE) Rose else Emerald

    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(catColor(tx.category).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(catIcon(tx.category), null, Modifier.size(18.dp), tint = catColor(tx.category))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.merchant ?: "غير معروف",
                    style = T_Body.copy(color = TextPri, fontWeight = FontWeight.Medium))
                Text(tx.category, style = T_Small)
            }
            Text("$sign${FinancialAdvisor.fmt(tx.amount)}",
                style = T_Num.copy(fontSize = 14.sp, color = color))
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Text(tx.rawSms, style = T_Small.copy(lineHeight = 16.sp))
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Rose.copy(alpha = 0.12f))
                    .clickable { onDelete() }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = Rose)
                Spacer(Modifier.width(6.dp))
                Text("حذف", style = T_Body.copy(color = Rose, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun AddDialog(onDismiss: () -> Unit, onSave: (Double, String, String, TxType) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CategoryClassifier.categories.first()) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var menuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        shape = RoundedCornerShape(28.dp),
        title = { Text("إضافة عملية", style = T_H2) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = T_Num
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("الجهة") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = T_Body
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    FilterChip(
                        selected = type == TxType.EXPENSE,
                        onClick = { type = TxType.EXPENSE },
                        label = { Text("مصروف") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = type == TxType.INCOME,
                        onClick = { type = TxType.INCOME },
                        label = { Text("دخل") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(category, style = T_Body) }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Surface2
                    ) {
                        CategoryClassifier.categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, style = T_Body.copy(color = TextPri)) },
                                onClick = { category = c; menuOpen = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount.toDoubleOrNull()?.let { onSave(it, merchant, category, type) } },
                shape = RoundedCornerShape(12.dp)
            ) { Text("حفظ", style = T_Body.copy(color = Violet, fontWeight = FontWeight.Bold)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("إلغاء", style = T_Body.copy(color = TextSec))
            }
        }
    )
}

// ==================== BUDGET ====================
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("الميزانية", style = T_H1) }

        item {
            GlassCard {
                Text("الميزانية الكلية", style = T_H2)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = totalInput,
                    onValueChange = { totalInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("الحد الشهري") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = T_Num
                )
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    text = "حفظ الميزانية",
                    onClick = {
                        totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("صرفت هذا الشهر: ${FinancialAdvisor.fmt(summary.spent)} ر.س", style = T_Small)
            }
        }

        item { Text("ميزانية لكل تصنيف", style = T_H2, modifier = Modifier.padding(top = 8.dp)) }

        items(CategoryClassifier.categories) { cat ->
            val spentInCat = summary.categoryTotals.firstOrNull { it.category == cat }?.amount ?: 0.0
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(catColor(cat).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(catIcon(cat), null, Modifier.size(18.dp), tint = catColor(cat))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat, style = T_Body.copy(color = TextPri, fontWeight = FontWeight.Medium))
                        Text("صرفت ${FinancialAdvisor.fmt(spentInCat)} ر.س", style = T_Small)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = catInputs[cat] ?: "",
                        onValueChange = { v ->
                            catInputs = catInputs + (cat to v.filter { ch -> ch.isDigit() || ch == '.' })
                        },
                        placeholder = { Text("0", style = T_Small) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        textStyle = T_Num
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Violet, Pink)))
                            .clickable {
                                (catInputs[cat]?.toDoubleOrNull() ?: 0.0).let {
                                    vm.setBudget(monthKey, cat, it)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, "حفظ", tint = TextPri, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ==================== ADVISOR ====================
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("المستشار", style = T_H1) }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Violet, Pink))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(24.dp), tint = TextPri)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("تقرير هذا الشهر", style = T_H2)
                        Spacer(Modifier.height(2.dp))
                        Text("${summary.count} عملية · ${advice.size} ملاحظة", style = T_Small)
                    }
                }
            }
        }

        items(advice) { a -> AdviceCard(a) }
    }
}

@Composable
private fun AdviceCard(a: Advice) {
    val (color, icon) = when (a.level) {
        Level.DANGER -> Rose to Icons.Default.Warning
        Level.WARN   -> Amber to Icons.Default.Info
        Level.GOOD   -> Emerald to Icons.Default.CheckCircle
        Level.INFO   -> Violet to Icons.Default.Lightbulb
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Text(a.title, style = T_H2.copy(fontSize = 15.sp))
        }
        Spacer(Modifier.height(12.dp))
        Text(a.body, style = T_Body.copy(fontSize = 13.sp))
    }
}

// ==================== HELPERS ====================
private fun catColor(cat: String): Color = when (cat) {
    "طعام وشراب" -> Color(0xFFFF7A59)
    "بقالة" -> Color(0xFF4ADE80)
    "مواصلات" -> Color(0xFF60A5FA)
    "وقود" -> Color(0xFFFBBF24)
    "تسوق" -> Color(0xFFF472B6)
    "فواتير" -> Color(0xFFA78BFA)
    "اتصالات" -> Color(0xFF38BDF8)
    "صحة" -> Color(0xFFFB7185)
    "ترفيه" -> Color(0xFFC084FC)
    "اشتراكات" -> Color(0xFF2DD4BF)
    "تعليم" -> Color(0xFF818CF8)
    "تحويلات" -> Color(0xFF34D399)
    else -> Color(0xFF94A3B8)
}

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
