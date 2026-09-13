package com.mizan.money.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

// ============ COLORS (from HTML) ============
private val Bg           = Color(0xFFF8FAFC)
private val BgOuter      = Color(0xFFF1F5F9)
private val White        = Color(0xFFFFFFFF)
private val TextMain     = Color(0xFF1E293B)
private val TextMuted    = Color(0xFF64748B)
private val TextLight    = Color(0xFF94A3B8)
private val Brand50      = Color(0xFFEFF6FF)
private val Brand100     = Color(0xFFDBEAFE)
private val Brand500     = Color(0xFF3B82F6)
private val Brand600     = Color(0xFF2563EB)
private val Brand700     = Color(0xFF1D4ED8)
private val Success      = Color(0xFF10B981)
private val Danger       = Color(0xFFF43F5E)
private val Amber        = Color(0xFFF59E0B)
private val Purple       = Color(0xFFA855F7)
private val BorderSoft   = Color(0xFFE2E8F0)
private val Slate100     = Color(0xFFF1F5F9)

// ============ TYPE ============
private val Sans = FontFamily.Default
private val Mono = FontFamily.Monospace

private val H1        = TextStyle(fontFamily = Sans, fontSize = 20.sp, fontWeight = FontWeight.Bold,    color = TextMain)
private val H2        = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Bold,    color = TextMain)
private val Body      = TextStyle(fontFamily = Sans, fontSize = 14.sp,                                  color = TextMain)
private val BodyMuted = TextStyle(fontFamily = Sans, fontSize = 13.sp,                                  color = TextMuted)
private val XS        = TextStyle(fontFamily = Sans, fontSize = 11.sp,                                  color = TextMuted)
private val BalanceNum= TextStyle(fontFamily = Sans, fontSize = 38.sp, fontWeight = FontWeight.Bold,    color = White)
private val NumBold   = TextStyle(fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold,    color = TextMain)

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
            background = Bg,
            surface = White,
            surfaceTint = Color.Transparent,
            primary = Brand600,
            onPrimary = White,
            onBackground = TextMain,
            onSurface = TextMain,
            error = Danger,
            onError = White,
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = BgOuter) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Surface(
                        Modifier.fillMaxWidth().fillMaxHeight(),
                        color = Bg
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

// ============ PERMISSION ============
@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        // Logo circle
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Brand100),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AccountBalance, null, Modifier.size(48.dp), tint = Brand600)
        }

        Spacer(Modifier.height(20.dp))
        Text("ميزان", style = H1.copy(fontSize = 32.sp))
        Spacer(Modifier.height(6.dp))
        Text("إدارة مالية بخصوصية تامة", style = BodyMuted)

        Spacer(Modifier.height(50.dp))

        // Trust points
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(White)
                .shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.05f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TrustPoint("اقرأ رسائل بنكك", "لتصنيف مصاريفك تلقائياً")
            Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100))
            TrustPoint("حلّل عاداتك", "اعرض أنماط صرفك بوضوح")
            Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100))
            TrustPoint("خصوصيتك أولاً", "البيانات على جهازك فقط")
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand600,
                contentColor = White
            )
        ) {
            Text("ابدأ الآن", style = Body.copy(color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp))
        }
    }
}

@Composable
private fun TrustPoint(title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Brand50),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Brand500))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = H2.copy(fontSize = 15.sp))
            Spacer(Modifier.height(2.dp))
            Text(desc, style = XS)
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
        BottomNav(tab) { tab = it }
    }
}

@Composable
private fun AppHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Brand100),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AccountBalance, null, Modifier.size(24.dp), tint = Brand600)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ميزان", style = H1)
            Text("إدارة مالية بخصوصية تامة", style = XS)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(White)
                .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Settings, null, Modifier.size(20.dp), tint = TextMuted)
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("الرئيسية",  Icons.Filled.Home,        0),
        Triple("العمليات",  Icons.Filled.ReceiptLong, 1),
        Triple("الميزانية", Icons.Filled.AccountBalanceWallet, 2),
        Triple("المستشار",  Icons.Filled.PieChart,    3)
    )
    Surface(
        Modifier.fillMaxWidth(),
        color = White,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (label, icon, idx) ->
                val isSel = selected == idx
                val tint by animateColorAsState(
                    if (isSel) Brand600 else TextLight,
                    tween(200), label = "tint"
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(idx) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, label, Modifier.size(22.dp), tint = tint)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = tint,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Balance Card
        item {
            BalanceCard(summary, offset, onPrev = { offset-- }, onNext = { if (offset < 0) offset++ })
        }

        // Budget status
        if (budget > 0) {
            item { BudgetStatusCard(budget, summary.spent) }
        }

        // Top categories
        if (summary.categoryTotals.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الأكثر استهلاكاً", style = H2, modifier = Modifier.weight(1f))
                }
            }
            items(summary.categoryTotals.take(3)) { cat ->
                CategoryCard(cat, summary.spent)
            }
        }

        // Recent transactions
        if (txs.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("أحدث العمليات", style = H2, modifier = Modifier.weight(1f))
                    Text("عرض الكل", style = BodyMuted.copy(color = Brand600, fontWeight = FontWeight.Medium))
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
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Brand500, Brand700),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            )
            .padding(24.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    monthName(offset),
                    style = Body.copy(color = White, fontWeight = FontWeight.Medium),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(White.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(White.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(12.dp), tint = White)
                    Spacer(Modifier.width(4.dp))
                    Text("محلي ١٠٠٪", style = XS.copy(color = White))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = White.copy(alpha = if (offset < 0) 1f else 0.3f), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onPrev, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("الرصيد المتبقي المتاح", style = Body.copy(color = Brand100))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(FinancialAdvisor.fmt(abs(s.net)), style = BalanceNum)
                Spacer(Modifier.width(6.dp))
                Text("ر.س", style = Body.copy(color = Brand100, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                    modifier = Modifier.padding(bottom = 6.dp))
            }

            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(White.copy(alpha = 0.2f)))
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Income side
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.ArrowDownward, null, Modifier.size(20.dp), tint = White) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("إجمالي الدخل", style = XS.copy(color = Brand100))
                        Text(FinancialAdvisor.fmt(s.income), style = Body.copy(color = White, fontWeight = FontWeight.Bold))
                    }
                }
                Box(Modifier.width(1.dp).height(32.dp).background(White.copy(alpha = 0.2f)))
                Spacer(Modifier.width(16.dp))
                // Expense side
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.ArrowUpward, null, Modifier.size(20.dp), tint = White) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("إجمالي الصرف", style = XS.copy(color = Brand100))
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

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("استهلاك ميزانية الشهر", style = H2, modifier = Modifier.weight(1f))
            Text(
                "${(spent / budget * 100).toInt()}٪",
                style = Body.copy(color = Brand600, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brand50)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Slate100)
        ) {
            Box(
                Modifier.fillMaxWidth(anim).fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Brand500)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("صرفت: ${FinancialAdvisor.fmt(spent)} ر.س", style = XS)
            Spacer(Modifier.weight(1f))
            Text("السقف: ${FinancialAdvisor.fmt(budget)} ر.س", style = XS)
        }
    }
}

@Composable
private fun CategoryCard(cat: com.mizan.money.advisor.CategoryTotal, total: Double) {
    val pct = (cat.share).toFloat()
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(catColorSoft(cat.category)),
                contentAlignment = Alignment.Center
            ) {
                Icon(catIcon(cat.category), null, Modifier.size(22.dp), tint = catColor(cat.category))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cat.category, style = H2.copy(fontSize = 14.sp))
                Text("${(cat.share * 100).toInt()}٪ من إجمالي الصرف", style = XS)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(FinancialAdvisor.fmt(cat.amount), style = NumBold)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(64.dp).height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Slate100)
                ) {
                    Box(
                        Modifier.fillMaxWidth(pct).fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(catColor(cat.category))
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionCard(tx: TransactionEntity, onClick: () -> Unit) {
    val isExpense = tx.type == TxType.EXPENSE
    val sign = if (isExpense) "-" else "+"
    val amtColor = if (isExpense) TextMain else Success

    SoftCard(Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(catColorSoft(tx.category)),
                contentAlignment = Alignment.Center
            ) {
                Icon(catIcon(tx.category), null, Modifier.size(22.dp), tint = catColor(tx.category))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.merchant ?: "غير معروف", style = H2.copy(fontSize = 14.sp))
                Spacer(Modifier.height(2.dp))
                Text("${tx.category} • ${Dates.dayLabel(tx.timestamp)}", style = XS)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$sign${FinancialAdvisor.fmt(tx.amount)}",
                    style = NumBold.copy(color = amtColor, fontSize = 16.sp)
                )
                Text("ر.س", style = XS)
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("سجل العمليات", style = H1)
                    Text("مستخرجة تلقائياً من رسائل البنك", style = XS)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Brand500)
                        .clickable { showAdd = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, "إضافة", tint = White, modifier = Modifier.size(22.dp)) }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(White)
                    .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.04f))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = TextLight)
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
        shape = RoundedCornerShape(28.dp),
        title = { Text("تفاصيل العملية", style = H2) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Slate100),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.AccountBalance, null, Modifier.size(16.dp), tint = TextMuted) }
                    Spacer(Modifier.width(8.dp))
                    Text(tx.bankName ?: "بنك", style = Body.copy(fontWeight = FontWeight.Medium))
                    Spacer(Modifier.weight(1f))
                    Text(Dates.dayLabel(tx.timestamp), style = XS)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100))
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
                Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100))
                Spacer(Modifier.height(16.dp))
                Text("الرسالة الأصلية", style = Body.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                Text(tx.rawSms, style = BodyMuted, modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate100)
                    .padding(12.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("حذف", style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق", style = Body.copy(color = TextMuted)) }
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
        shape = RoundedCornerShape(28.dp),
        title = { Text("إضافة عملية يدوية", style = H2) },
        text = {
            Column {
                Row {
                    TypeChip("مصروف", type == TxType.EXPENSE, Danger) { type = TxType.EXPENSE }
                    Spacer(Modifier.width(8.dp))
                    TypeChip("دخل", type == TxType.INCOME, Success) { type = TxType.INCOME }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("المبلغ (ر.س)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("الجهة / التاجر") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                Text("التصنيف", style = XS)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryClassifier.categories.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { c ->
                                Box(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (category == c) Brand50 else Slate100)
                                        .clickable { category = c }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        c,
                                        style = XS.copy(
                                            color = if (category == c) Brand600 else TextMuted,
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
            }) { Text("حفظ", style = Body.copy(color = Brand600, fontWeight = FontWeight.Bold)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", style = Body.copy(color = TextMuted)) }
        }
    )
}

@Composable
private fun TypeChip(text: String, selected: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) activeColor.copy(alpha = 0.1f) else Slate100)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = Body.copy(
                color = if (selected) activeColor else TextMuted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        )
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("الميزانية والتصنيفات", style = H1)
            Text("راقب إنفاقك وقارنه بالحدود المحددة", style = XS)
        }

        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الميزانية الإجمالية للشهر", style = XS)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                totalInput.ifBlank { "0" },
                                style = H1.copy(fontSize = 26.sp)
                            )
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
                    shape = RoundedCornerShape(14.dp),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) } },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand600, contentColor = White)
                ) { Text("حفظ", style = Body.copy(color = White, fontWeight = FontWeight.Bold)) }
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
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(catColorSoft(cat)),
                        contentAlignment = Alignment.Center
                    ) { Icon(catIcon(cat), null, Modifier.size(20.dp), tint = catColor(cat)) }
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
                        .clip(RoundedCornerShape(4.dp))
                        .background(Slate100)
                ) {
                    Box(
                        Modifier.fillMaxWidth(pct).fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
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
                        placeholder = { Text("0", style = XS) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = Body
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brand600)
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Lightbulb, null, Modifier.size(28.dp), tint = Amber) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("المستشار المالي", style = H2.copy(color = White, fontSize = 17.sp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "تحليل ذكي ومحلي لنمط إنفاقك",
                            style = BodyMuted.copy(color = Color(0xFFCBD5E1))
                        )
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
        Level.INFO   -> Brand600
    }
    val icon = when (a.level) {
        Level.DANGER -> Icons.Default.Warning
        Level.WARN   -> Icons.Default.Info
        Level.GOOD   -> Icons.Default.CheckCircle
        Level.INFO   -> Icons.Default.Lightbulb
    }
    SoftCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, Modifier.size(20.dp), tint = color) }
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
private fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(24.dp))
            .background(White)
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(Slate100),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.ReceiptLong, null, Modifier.size(30.dp), tint = TextLight) }
        Spacer(Modifier.height(14.dp))
        Text(text, style = BodyMuted)
    }
}

private fun catColor(cat: String): Color = when (cat) {
    "طعام وشراب" -> Amber
    "بقالة" -> Color(0xFF10B981)
    "مواصلات" -> Color(0xFF3B82F6)
    "وقود" -> Color(0xFF64748B)
    "تسوق" -> Purple
    "فواتير" -> Color(0xFF6366F1)
    "اتصالات" -> Color(0xFF0EA5E9)
    "صحة" -> Color(0xFFEC4899)
    "ترفيه" -> Color(0xFFD946EF)
    "اشتراكات" -> Color(0xFF14B8A6)
    "تعليم" -> Color(0xFF8B5CF6)
    "تحويلات" -> Color(0xFF059669)
    else -> Color(0xFF94A3B8)
}

private fun catColorSoft(cat: String): Color {
    val c = catColor(cat)
    return c.copy(alpha = 0.12f)
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
