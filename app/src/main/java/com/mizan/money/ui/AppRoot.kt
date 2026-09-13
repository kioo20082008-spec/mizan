package com.mizan.money.ui

// ============================================================
// MIZAN — Precision measurement direction
// Colors, contrast ratios, and design rules are documented inline
// ============================================================

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlinx.coroutines.delay
import kotlin.math.max

// ============================================================
// COLORS
// Contrast ratios calculated via WCAG relative-luminance formula
// against Board (#EEF1EC). See CHECKLIST in code comments.
// ============================================================
private val Board     = Color(0xFFEEF1EC) // background
private val Graphite  = Color(0xFF20262B) // 12.91:1 (AAA) - all text/lines
private val Indicator = Color(0xFF2B6E6E) // 5.19:1 (AA)  - ONLY accent (never as text < 18sp)
private val Warn      = Color(0xFFA13B2E) // 5.64:1 (AA)  - expenses / danger
private val Balance   = Color(0xFF3F6B4A) // 5.32:1 (AA)  - income / good
private val Gray      = Color(0xFF5B615C) // 5.03:1 (AA)  - secondary

// ============================================================
// TYPOGRAPHY
// TODO: Replace with IBM Plex Sans Arabic + IBM Plex Mono
//       (download .ttf from https://github.com/IBM/plex and
//        place under app/src/main/res/font/ then replace
//        FontFamily.Default and FontFamily.Monospace below).
// For now: system default (Sans) + system monospace (Mono).
// Monospace guarantees column alignment without tnum feature.
// ============================================================
private val Sans = FontFamily.Default
private val Mono = FontFamily.Monospace

private val St_Display  = TextStyle(fontFamily = Sans, fontSize = 34.sp, fontWeight = FontWeight.Bold,      color = Graphite)
private val St_H1       = TextStyle(fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight.Bold,      color = Graphite)
private val St_H2       = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,  color = Graphite)
private val St_Body     = TextStyle(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Normal,    color = Graphite)
private val St_Label    = TextStyle(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Normal,    color = Gray)
private val St_NumLarge = TextStyle(fontFamily = Mono, fontSize = 36.sp, fontWeight = FontWeight.Bold,      color = Graphite)
private val St_NumRow   = TextStyle(fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Medium,    color = Graphite)

// ============================================================
// APP ROOT
// ============================================================
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val vm: MainViewModel = viewModel()
    var hasSms by remember { mutableStateOf(hasSmsPermission(ctx)) }
    var scanned by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasSms = hasSmsPermission(ctx) }

    LaunchedEffect(hasSms) {
        if (hasSms && !scanned) { scanned = true; vm.scanInbox() }
    }

    // surfaceTint = Transparent: mandatory to prevent M3 from
    // auto-tinting elevated surfaces (verified via PreviewTest #4)
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Board,
            surface = Board,
            surfaceVariant = Board,
            surfaceTint = Color.Transparent,
            primary = Indicator,
            onPrimary = Board,
            onBackground = Graphite,
            onSurface = Graphite,
            onSurfaceVariant = Gray,
            error = Warn,
            onError = Board,
            outline = Graphite.copy(alpha = 0.15f),
            outlineVariant = Graphite.copy(alpha = 0.08f),
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = Board) {
                if (!hasSms) PermissionScreen {
                    launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                } else {
                    HomeScaffold(vm)
                }
            }
        }
    }
}

private fun hasSmsPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

// ============================================================
// PERMISSION SCREEN — first trust impression
// ============================================================
@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top ruler tick
        Row(
            Modifier.fillMaxWidth(0.4f),
            horizontalArrangement = Arrangement.Center
        ) {
            Tick(12.dp); Tick(4.dp); Tick(4.dp); Tick(4.dp); Tick(12.dp)
        }
        Spacer(Modifier.height(16.dp))

        Text("ميزان", style = St_Display)
        Spacer(Modifier.height(8.dp))
        Text("دفترك المالي الشخصي", style = St_Body.copy(color = Gray))

        Spacer(Modifier.height(60.dp))

        // Trust box — sharp, left rule in Indicator
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(Indicator))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("نقرأ رسائل بنكك تلقائياً", style = St_H2)
                Spacer(Modifier.height(8.dp))
                Text("لتصنيف مصاريفك وعرض تحليل بصري.", style = St_Body.copy(color = Gray))
                Spacer(Modifier.height(14.dp))
                Text("— لا شيء يخرج من جهازك.", style = St_Label.copy(color = Indicator, fontSize = 12.sp))
            }
        }

        Spacer(Modifier.height(60.dp))

        // Bottom ruler tick
        Row(
            Modifier.fillMaxWidth(0.4f),
            horizontalArrangement = Arrangement.Center
        ) {
            Tick(12.dp); Tick(4.dp); Tick(4.dp); Tick(4.dp); Tick(12.dp)
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Graphite,
                contentColor = Board
            )
        ) {
            Text("ابدأ", style = St_Body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp))
        }
    }
}

@Composable
private fun Tick(height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .width(1.dp)
            .height(height)
            .background(Graphite.copy(alpha = 0.5f))
    )
}

// ============================================================
// HOME SCAFFOLD — flat bottom bar, no floating
// ============================================================
@Composable
private fun HomeScaffold(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> DashboardScreen(vm)
                1 -> TransactionsScreen(vm)
                2 -> BudgetScreen(vm)
                else -> AdvisorScreen(vm)
            }
        }
        BottomBar(tab) { tab = it }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("الرئيسية", Icons.Filled.Home, 0),
        Triple("العمليات", Icons.Filled.ReceiptLong, 1),
        Triple("الميزانية", Icons.Filled.Savings, 2),
        Triple("المستشار", Icons.Filled.AutoAwesome, 3)
    )
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Graphite.copy(alpha = 0.12f)))
        Row(
            Modifier.fillMaxWidth().background(Board).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (label, icon, idx) ->
                val isSel = selected == idx
                val content by animateColorAsState(
                    if (isSel) Graphite else Gray, tween(180), label = "nav"
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(idx) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, label, Modifier.size(20.dp), tint = content)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label,
                        fontSize = 10.sp,
                        color = content,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    // Active tick mark (only visible when selected)
                    Box(
                        Modifier
                            .width(if (isSel) 14.dp else 0.dp)
                            .height(2.dp)
                            .background(if (isSel) Indicator else Color.Transparent)
                    )
                }
            }
        }
    }
}

// ============================================================
// DASHBOARD
// ============================================================
@Composable
private fun DashboardScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    var offset by remember { mutableIntStateOf(0) }

    val range = remember(offset) { Dates.monthRange(offset) }
    val summary = remember(txs, offset) { FinancialAdvisor.summarize(txs, range.first, range.last) }
    val monthlyBudget = budgets.firstOrNull {
        it.monthKey == Dates.monthKey(offset) && it.category == TOTAL_BUDGET
    }?.limitAmount ?: budgets.firstOrNull {
        it.monthKey == ALL_MONTHS && it.category == TOTAL_BUDGET
    }?.limitAmount ?: 0.0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { MonthSwitcher(offset, onPrev = { offset-- }, onNext = { if (offset < 0) offset++ }) }

        item { BalanceHeroCard(summary) }

        if (monthlyBudget > 0) {
            item { BudgetRulerCard(summary.spent, monthlyBudget) }
        }

        if (summary.categoryTotals.isNotEmpty()) {
            item { SectionLabel("التصنيفات") }
            items(summary.categoryTotals) { cat -> CategoryRow(cat, summary.spent) }
        }

        if (txs.isEmpty()) item { EmptyState("لا توجد عمليات بعد") }
    }
}

@Composable
private fun MonthSwitcher(offset: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // RTL: next month goes right, prev goes left
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = if (offset < 0) Graphite else Graphite.copy(alpha = 0.2f)
            )
        }
        Text(
            monthName(offset),
            Modifier.weight(1f),
            style = St_H2,
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Graphite)
        }
    }
}

// --------- Balance Hero (the single bold moment) ---------
@Composable
private fun BalanceHeroCard(s: MonthSummary) {
    // Verify: income on the visual RIGHT (RTL start), expense on visual LEFT
    val maxVal = max(max(s.income, s.spent), 1.0)
    val incomeFrac = (s.income / maxVal).toFloat()
    val expenseFrac = (s.spent / maxVal).toFloat()

    // Animation: bars extend from 0 to their actual fraction (600ms)
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); animateIn = true }

    val incomeAnim by animateFloatAsState(
        targetValue = if (animateIn) incomeFrac else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "income-bar"
    )
    val expenseAnim by animateFloatAsState(
        targetValue = if (animateIn) expenseFrac else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "expense-bar"
    )

    Column(Modifier.fillMaxWidth()) {
        Text("صافي الشهر", style = St_Label)
        Spacer(Modifier.height(20.dp))

        // The balance beam
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val maxBarW = w * 0.42f
                val barH = 6.dp.toPx()

                // Income bar — extends RIGHT from center
                val incW = maxBarW * incomeAnim
                drawRect(
                    color = Balance,
                    topLeft = Offset(cx, cy - barH / 2f),
                    size = Size(incW, barH)
                )

                // Expense bar — extends LEFT from center
                val expW = maxBarW * expenseAnim
                drawRect(
                    color = Warn,
                    topLeft = Offset(cx - expW, cy - barH / 2f),
                    size = Size(expW, barH)
                )

                // Fulcrum — small diamond at center
                val r = 5.dp.toPx()
                val diamond = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r, cy)
                    lineTo(cx, cy + r)
                    lineTo(cx - r, cy)
                    close()
                }
                drawPath(diamond, Graphite)

                // Center vertical hairline (the "0" of the scale)
                drawLine(
                    color = Graphite.copy(alpha = 0.15f),
                    start = Offset(cx, 0f),
                    end = Offset(cx, h),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Labels + values (labels below their respective arms)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // RTL: first child = right = income side
            Column {
                Text("الدخل", style = St_Label)
                Spacer(Modifier.height(2.dp))
                Text(FinancialAdvisor.fmt(s.income), style = St_NumRow.copy(color = Balance))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("المصروف", style = St_Label)
                Spacer(Modifier.height(2.dp))
                Text(FinancialAdvisor.fmt(s.spent), style = St_NumRow.copy(color = Warn))
            }
        }

        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Graphite.copy(alpha = 0.12f)))
        Spacer(Modifier.height(16.dp))

        Text("الصافي", style = St_Label)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(FinancialAdvisor.fmt(kotlin.math.abs(s.net)), style = St_NumLarge)
            Spacer(Modifier.width(8.dp))
            Text("ر.س", style = St_Label, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

// --------- Budget Ruler (replaces circular progress) ---------
@Composable
private fun BudgetRulerCard(spent: Double, budget: Double) {
    val pct = (spent / budget).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        spent > budget -> Warn
        pct > 0.8f -> Indicator
        else -> Balance
    }

    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(120); animateIn = true }
    val anim by animateFloatAsState(
        targetValue = if (animateIn) pct else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "ruler"
    )

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("الميزانية الشهرية", style = St_H2)
            Spacer(Modifier.weight(1f))
            Text("${(spent / budget * 100).toInt()}%",
                style = St_NumRow.copy(color = color, fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(12.dp))

        // Ruler: 0% at right (RTL), 100% at left
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val w = size.width
            val h = size.height
            val baseY = h - 4.dp.toPx()
            val fillH = 6.dp.toPx()

            // Baseline
            drawLine(
                color = Graphite.copy(alpha = 0.15f),
                start = Offset(0f, baseY),
                end = Offset(w, baseY),
                strokeWidth = 1.dp.toPx()
            )

            // Fill from right
            val fillW = w * anim
            drawRect(
                color = color,
                topLeft = Offset(w - fillW, baseY - fillH),
                size = Size(fillW, fillH)
            )

            // Tick marks at 0/25/50/75/100 (RTL: 0 at x=w)
            val ticks = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            ticks.forEach { t ->
                val x = w * (1f - t)
                val tickH = if (t == 0f || t == 1f) 14.dp.toPx() else 6.dp.toPx()
                drawLine(
                    color = Graphite.copy(alpha = 0.55f),
                    start = Offset(x, baseY - tickH),
                    end = Offset(x, baseY),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(FinancialAdvisor.fmt(budget), style = St_Label)
            Text(FinancialAdvisor.fmt(spent), style = St_Label)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Column {
        Text(text, style = St_H2)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Graphite.copy(alpha = 0.15f)))
    }
}

@Composable
private fun CategoryRow(cat: com.mizan.money.advisor.CategoryTotal, total: Double) {
    val frac = if (total > 0) (cat.amount / total).toFloat() else 0f
    val animated by animateFloatAsState(frac, tween(600), label = "cat")

    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cat.category, style = St_Body)
            Spacer(Modifier.weight(1f))
            Text(FinancialAdvisor.fmt(cat.amount), style = St_NumRow)
            Spacer(Modifier.width(4.dp))
            Text("ر.س", style = St_Label)
        }
        Spacer(Modifier.height(8.dp))
        // 2dp bar, fills from right (RTL)
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val w = size.width
            val h = size.height
            drawRect(Graphite.copy(alpha = 0.06f), size = Size(w, h))
            val fw = w * animated
            drawRect(Indicator, topLeft = Offset(w - fw, 0f), size = Size(fw, h))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Graphite.copy(alpha = 0.08f)))
    }
}

// ============================================================
// TRANSACTIONS
// ============================================================
@Composable
private fun TransactionsScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val grouped = remember(txs) { txs.groupBy { Dates.dayLabel(it.timestamp) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp)
        ) {
            item {
                Text("العمليات", style = St_H1)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(32.dp).height(2.dp).background(Indicator))
                Spacer(Modifier.height(16.dp))
            }
            grouped.forEach { (day, list) ->
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(day, style = St_Label)
                    Spacer(Modifier.height(6.dp))
                }
                items(list, key = { it.id }) { tx ->
                    TransactionRow(tx, onDelete = { vm.delete(tx) })
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Graphite.copy(alpha = 0.08f)))
                }
            }
            if (txs.isEmpty()) item { EmptyState("لا توجد عمليات بعد") }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            containerColor = Graphite,
            contentColor = Board,
            shape = CircleShape
        ) { Icon(Icons.Default.Add, "إضافة") }
    }

    if (showAdd) AddTransactionDialog(
        onDismiss = { showAdd = false },
        onSave = { amt, merch, cat, type -> vm.addManual(amt, merch, cat, type); showAdd = false }
    )
}

@Composable
private fun TransactionRow(tx: TransactionEntity, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sign = if (tx.type == TxType.EXPENSE) "-" else "+"
    val amtColor = if (tx.type == TxType.EXPENSE) Warn else Balance

    Column(
        Modifier.fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 1px vertical rule in graphite (the "entry marker")
            Box(Modifier.width(1.dp).height(28.dp).background(Graphite.copy(alpha = 0.3f)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tx.merchant ?: "غير معروف",
                    style = St_Body.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tx.category + " · " + (if (tx.isManual) "يدوي" else (tx.bankName ?: "SMS")),
                    style = St_Label
                )
            }
            Text(sign + FinancialAdvisor.fmt(tx.amount),
                style = St_NumRow.copy(color = amtColor, fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(4.dp))
            Text("ر.س", style = St_Label)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Text(tx.rawSms, style = St_Label.copy(lineHeight = 17.sp))
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = Warn),
                shape = RoundedCornerShape(0.dp)
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("حذف", style = St_Body.copy(color = Warn))
            }
        }
    }
}

@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (Double, String, String, TxType) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CategoryClassifier.categories.first()) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var menuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Board,
        shape = RoundedCornerShape(0.dp),
        tonalElevation = 0.dp,
        title = { Text("إضافة عملية", style = St_H2) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("المبلغ", style = St_Label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                    textStyle = St_NumRow
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("الجهة", style = St_Label) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                    textStyle = St_Body
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    FilterChip(
                        selected = type == TxType.EXPENSE,
                        onClick = { type = TxType.EXPENSE },
                        label = { Text("مصروف") },
                        shape = RoundedCornerShape(0.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = type == TxType.INCOME,
                        onClick = { type = TxType.INCOME },
                        label = { Text("دخل") },
                        shape = RoundedCornerShape(0.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp)
                    ) { Text(category, style = St_Body) }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Board
                    ) {
                        CategoryClassifier.categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, style = St_Body) },
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
                shape = RoundedCornerShape(0.dp)
            ) { Text("حفظ", style = St_Body.copy(color = Indicator, fontWeight = FontWeight.Bold)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(0.dp)) {
                Text("إلغاء", style = St_Body.copy(color = Gray))
            }
        }
    )
}

// ============================================================
// BUDGET
// ============================================================
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
                ?.limitAmount?.let {
                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                } ?: ""
        )
    }
    var catInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(budgets) {
        catInputs = CategoryClassifier.categories.associateWith { c ->
            budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                ?.limitAmount?.let {
                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                } ?: ""
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("الميزانية", style = St_H1)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(32.dp).height(2.dp).background(Indicator))
        }

        item {
            Text("الميزانية الكلية", style = St_H2)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = totalInput,
                    onValueChange = { totalInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("الحد الشهري", style = St_Label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(0.dp),
                    textStyle = St_NumRow
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) } },
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Graphite,
                        contentColor = Board
                    )
                ) { Text("حفظ", style = St_Body) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "صرفت هذا الشهر: " + FinancialAdvisor.fmt(summary.spent) + " ر.س",
                style = St_Label
            )
        }

        item { SectionLabel("ميزانية لكل تصنيف") }

        items(CategoryClassifier.categories) { cat ->
            val spentInCat = summary.categoryTotals.firstOrNull { it.category == cat }?.amount ?: 0.0
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat, style = St_Body, modifier = Modifier.weight(1f))
                    Text(FinancialAdvisor.fmt(spentInCat) + " ر.س",
                        style = St_Label.copy(fontSize = 11.sp))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = catInputs[cat] ?: "",
                        onValueChange = { v ->
                            catInputs = catInputs + (cat to v.filter { ch -> ch.isDigit() || ch == '.' })
                        },
                        placeholder = { Text("0", style = St_Label) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(0.dp),
                        textStyle = St_NumRow
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(Graphite, RoundedCornerShape(0.dp))
                            .clickable {
                                (catInputs[cat]?.toDoubleOrNull() ?: 0.0).let {
                                    vm.setBudget(monthKey, cat, it)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Check, "حفظ", tint = Board, modifier = Modifier.size(18.dp)) }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Graphite.copy(alpha = 0.08f)))
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ============================================================
// ADVISOR
// ============================================================
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
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("المستشار", style = St_H1)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(32.dp).height(2.dp).background(Indicator))
        }

        item {
            // Report header — sharp, indicator rule on right
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Indicator))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("تقرير ذكي", style = St_H2)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "راجعت " + summary.count + " عملية. إليك " + advice.size + " ملاحظة:",
                        style = St_Body.copy(color = Gray, fontSize = 13.sp)
                    )
                }
            }
        }

        items(advice) { a -> AdviceRow(a) }
    }
}

@Composable
private fun AdviceRow(a: Advice) {
    val color = when (a.level) {
        Level.DANGER -> Warn
        Level.WARN   -> Indicator
        Level.GOOD   -> Balance
        Level.INFO   -> Gray
    }
    val icon: ImageVector = when (a.level) {
        Level.DANGER -> Icons.Default.Warning
        Level.WARN   -> Icons.Default.Info
        Level.GOOD   -> Icons.Default.CheckCircle
        Level.INFO   -> Icons.Default.Lightbulb
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 6.dp)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(color))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(14.dp), tint = color)
                Spacer(Modifier.width(6.dp))
                Text(a.title, style = St_H2.copy(color = color))
            }
            Spacer(Modifier.height(6.dp))
            Text(a.body, style = St_Body.copy(color = Gray, fontSize = 13.sp, lineHeight = 19.sp))
        }
    }
}

// ============================================================
// HELPERS
// ============================================================
@Composable
private fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.ReceiptLong, null, Modifier.size(32.dp), tint = Gray)
        Spacer(Modifier.height(12.dp))
        Text(text, style = St_Body.copy(color = Gray))
    }
}

private fun monthName(offset: Int): String {
    val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset) }
    val names = listOf(
        "يناير","فبراير","مارس","أبريل","مايو","يونيو",
        "يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر"
    )
    return names[c.get(java.util.Calendar.MONTH)] + " " + c.get(java.util.Calendar.YEAR)
}
