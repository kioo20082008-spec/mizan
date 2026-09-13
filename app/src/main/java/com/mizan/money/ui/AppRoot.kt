package com.mizan.money.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
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

// ============ COLORS ============
private val Paper     = Color(0xFFF7F4EF)
private val Card      = Color(0xFFFFFFFF)
private val Border    = Color(0xFFEDE8E0)
private val Ink       = Color(0xFF1F1E1C)
private val Ink2      = Color(0xFF5C5853)
private val Ink3      = Color(0xFF9B958C)
private val Accent    = Color(0xFFC8644A)
private val AccentSoft= Color(0xFFFAEDE8)
private val Success   = Color(0xFF4A7C59)
private val Danger    = Color(0xFFB54B4B)

// ============ TYPE ============
private val Sans = FontFamily.Default

private val H1      = TextStyle(fontFamily = Sans, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Ink)
private val H2      = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
private val Body    = TextStyle(fontFamily = Sans, fontSize = 15.sp, color = Ink, lineHeight = 22.sp)
private val Body2   = TextStyle(fontFamily = Sans, fontSize = 14.sp, color = Ink2, lineHeight = 20.sp)
private val Small   = TextStyle(fontFamily = Sans, fontSize = 13.sp, color = Ink2)
private val Caption = TextStyle(fontFamily = Sans, fontSize = 11.sp, color = Ink3)
private val NumHero = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, color = Ink)
private val NumRow  = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)

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
            surface = Card,
            surfaceTint = Color.Transparent,
            primary = Accent,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink,
            error = Danger,
            onError = Color.White,
            outline = Border,
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = Paper) {
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

// ============ PERMISSION ============
@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(1.dp))

        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(60.dp))
            Text("ميزان", style = H1.copy(fontSize = 40.sp))
            Spacer(Modifier.height(10.dp))
            Text(
                "مديرك المالي الشخصي",
                style = Body2
            )
        }

        Column {
            PermissionPoint(
                "اقرأ رسائل بنكك",
                "لتصنيف مصاريفك تلقائياً"
            )
            Spacer(Modifier.height(20.dp))
            PermissionPoint(
                "حلّل عاداتك",
                "واعرض لك أنماط صرفك بوضوح"
            )
            Spacer(Modifier.height(20.dp))
            PermissionPoint(
                "خصوصيتك أولاً",
                "كل البيانات على جهازك فقط"
            )
        }

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink,
                contentColor = Color.White
            )
        ) {
            Text("ابدأ الآن", style = Body.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
        }
    }
}

@Composable
private fun PermissionPoint(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(AccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Accent))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = H2.copy(fontSize = 15.sp))
            Spacer(Modifier.height(3.dp))
            Text(desc, style = Small)
        }
    }
}

// ============ SCAFFOLD ============
@Composable
private fun RootScaffold(vm: MainViewModel) {
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
        Row(
            Modifier.fillMaxWidth().background(Paper).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (label, icon, idx) ->
                val isSel = selected == idx
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(idx) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icon, label, Modifier.size(22.dp),
                        tint = if (isSel) Accent else Ink3
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        fontSize = 10.sp,
                        color = if (isSel) Ink else Ink3,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ============ SHARED ============
@Composable
private fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .border(1.dp, Border, RoundedCornerShape(20.dp))
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = Caption.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = Ink3
        ),
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
    )
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { TopHeader(offset, onPrev = { offset-- }, onNext = { if (offset < 0) offset++ }) }
        item { BalanceCard(summary) }
        if (budget > 0) item { BudgetCard(summary.spent, budget) }
        if (summary.categoryTotals.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("التصنيفات")
                    SoftCard {
                        summary.categoryTotals.take(5).forEachIndexed { i, cat ->
                            if (i > 0) Spacer(Modifier.height(14.dp))
                            CategoryRow(cat, summary.spent)
                        }
                    }
                }
            }
        }
        if (txs.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("آخر العمليات")
                    SoftCard {
                        txs.take(4).forEachIndexed { i, tx ->
                            if (i > 0) {
                                Spacer(Modifier.height(14.dp))
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
                                Spacer(Modifier.height(14.dp))
                            }
                            SimpleTxRow(tx)
                        }
                    }
                }
            }
        }
        if (txs.isEmpty()) item { EmptyState("لم نرصد عمليات بعد") }
    }
}

@Composable
private fun TopHeader(offset: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("ميزان", style = H1)
            Spacer(Modifier.height(2.dp))
            Text(monthName(offset), style = Small)
        }
        Row {
            IconButton(onClick = onNext, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = if (offset < 0) Ink else Ink3,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onPrev, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft, null,
                    tint = Ink, modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(s: MonthSummary) {
    SoftCard {
        Text("صافي الشهر", style = Small)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(FinancialAdvisor.fmt(kotlin.math.abs(s.net)), style = NumHero)
            Spacer(Modifier.width(8.dp))
            Text("ر.س", style = Small, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("المصروف", style = Caption)
                Spacer(Modifier.height(4.dp))
                Text(FinancialAdvisor.fmt(s.spent), style = NumRow.copy(color = Danger))
            }
            Column(Modifier.weight(1f)) {
                Text("الدخل", style = Caption)
                Spacer(Modifier.height(4.dp))
                Text(FinancialAdvisor.fmt(s.income), style = NumRow.copy(color = Success))
            }
        }
    }
}

@Composable
private fun BudgetCard(spent: Double, budget: Double) {
    val pct = (spent / budget).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        spent > budget -> Danger
        pct > 0.8f -> Accent
        else -> Success
    }
    val animated by animateFloatAsState(pct, tween(700), label = "b")

    SoftCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("الميزانية", style = Small)
            Spacer(Modifier.weight(1f))
            Text(
                "${(spent / budget * 100).toInt()}%",
                style = NumRow.copy(color = color, fontWeight = FontWeight.SemiBold)
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Border)
        ) {
            Box(
                Modifier.fillMaxWidth(animated).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${FinancialAdvisor.fmt(spent)} من ${FinancialAdvisor.fmt(budget)} ر.س",
            style = Caption
        )
    }
}

@Composable
private fun CategoryRow(cat: com.mizan.money.advisor.CategoryTotal, total: Double) {
    val frac = if (total > 0) (cat.amount / total).toFloat() else 0f
    val animated by animateFloatAsState(frac, tween(600), label = "c")

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                    .background(AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    categoryIcon(cat.category), null,
                    Modifier.size(16.dp), tint = Accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(cat.category, style = Body.copy(fontWeight = FontWeight.Medium))
            Spacer(Modifier.weight(1f))
            Text(FinancialAdvisor.fmt(cat.amount), style = NumRow)
            Spacer(Modifier.width(4.dp))
            Text("ر.س", style = Caption)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Border)
        ) {
            Box(
                Modifier.fillMaxWidth(animated).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Accent.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
private fun SimpleTxRow(tx: TransactionEntity) {
    val sign = if (tx.type == TxType.EXPENSE) "-" else "+"
    val color = if (tx.type == TxType.EXPENSE) Danger else Success

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(Paper),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                categoryIcon(tx.category), null,
                Modifier.size(18.dp), tint = Ink2
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tx.merchant ?: "غير معروف",
                style = Body.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.height(2.dp))
            Text(tx.category, style = Caption)
        }
        Text(sign + FinancialAdvisor.fmt(tx.amount),
            style = NumRow.copy(color = color, fontWeight = FontWeight.SemiBold))
    }
}

// ============ TRANSACTIONS ============
@Composable
private fun TransactionsScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val grouped = remember(txs) { txs.groupBy { Dates.dayLabel(it.timestamp) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Text("العمليات", style = H1) }
            grouped.forEach { (day, list) ->
                item {
                    Column {
                        SectionTitle(day)
                        SoftCard {
                            list.forEachIndexed { i, tx ->
                                if (i > 0) {
                                    Spacer(Modifier.height(12.dp))
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
                                    Spacer(Modifier.height(12.dp))
                                }
                                TransactionRow(tx, onDelete = { vm.delete(tx) })
                            }
                        }
                    }
                }
            }
            if (txs.isEmpty()) item { EmptyState("لا توجد عمليات بعد") }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            containerColor = Ink,
            contentColor = Color.White,
            shape = CircleShape
        ) { Icon(Icons.Default.Add, "إضافة") }
    }

    if (showAdd) AddDialog(
        onDismiss = { showAdd = false },
        onSave = { a, m, c, t -> vm.addManual(a, m, c, t); showAdd = false }
    )
}

@Composable
private fun TransactionRow(tx: TransactionEntity, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sign = if (tx.type == TxType.EXPENSE) "-" else "+"
    val color = if (tx.type == TxType.EXPENSE) Danger else Success

    Column(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Paper),
                contentAlignment = Alignment.Center
            ) {
                Icon(categoryIcon(tx.category), null, Modifier.size(18.dp), tint = Ink2)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tx.merchant ?: "غير معروف",
                    style = Body.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tx.category + " · " + (if (tx.isManual) "يدوي" else (tx.bankName ?: "SMS")),
                    style = Caption
                )
            }
            Text(sign + FinancialAdvisor.fmt(tx.amount),
                style = NumRow.copy(color = color, fontWeight = FontWeight.SemiBold))
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Text(tx.rawSms, style = Caption.copy(lineHeight = 17.sp))
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = Danger),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("حذف", style = Body2.copy(color = Danger))
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
        containerColor = Card,
        shape = RoundedCornerShape(24.dp),
        title = { Text("إضافة عملية", style = H2) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = NumRow
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("الجهة") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = Body
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    FilterChip(
                        selected = type == TxType.EXPENSE,
                        onClick = { type = TxType.EXPENSE },
                        label = { Text("مصروف") },
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = type == TxType.INCOME,
                        onClick = { type = TxType.INCOME },
                        label = { Text("دخل") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(category, style = Body) }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Card
                    ) {
                        CategoryClassifier.categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, style = Body) },
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
                shape = RoundedCornerShape(10.dp)
            ) { Text("حفظ", style = Body.copy(color = Accent, fontWeight = FontWeight.SemiBold)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("إلغاء", style = Body.copy(color = Ink2))
            }
        }
    )
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
        item { Text("الميزانية", style = H1) }

        item {
            Column {
                SectionTitle("الميزانية الكلية")
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = totalInput,
                            onValueChange = { totalInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("الحد الشهري") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = NumRow
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Ink,
                                contentColor = Color.White
                            )
                        ) { Text("حفظ", style = Body) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "صرفت هذا الشهر: ${FinancialAdvisor.fmt(summary.spent)} ر.س",
                        style = Caption
                    )
                }
            }
        }

        item { SectionTitle("ميزانية لكل تصنيف") }

        items(CategoryClassifier.categories) { cat ->
            val spentInCat = summary.categoryTotals.firstOrNull { it.category == cat }?.amount ?: 0.0
            SoftCard(Modifier.padding(bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(AccentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(categoryIcon(cat), null, Modifier.size(16.dp), tint = Accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat, style = Body.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(2.dp))
                        Text("صرفت ${FinancialAdvisor.fmt(spentInCat)} ر.س", style = Caption)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = catInputs[cat] ?: "",
                        onValueChange = { v ->
                            catInputs = catInputs + (cat to v.filter { ch -> ch.isDigit() || ch == '.' })
                        },
                        placeholder = { Text("0", style = Caption) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = NumRow
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink)
                            .clickable {
                                (catInputs[cat]?.toDoubleOrNull() ?: 0.0).let {
                                    vm.setBudget(monthKey, cat, it)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, "حفظ", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
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
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("المستشار", style = H1) }

        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AccentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = Accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("تقرير هذا الشهر", style = H2)
                        Spacer(Modifier.height(3.dp))
                        Text("${summary.count} عملية · ${advice.size} ملاحظة", style = Caption)
                    }
                }
            }
        }

        items(advice) { a -> AdviceCard(a) }
    }
}

@Composable
private fun AdviceCard(a: Advice) {
    val (icon, tint, bg) = when (a.level) {
        Level.DANGER -> Triple(Icons.Default.Warning, Danger, Danger.copy(alpha = 0.08f))
        Level.WARN   -> Triple(Icons.Default.Info, Accent, AccentSoft)
        Level.GOOD   -> Triple(Icons.Default.CheckCircle, Success, Success.copy(alpha = 0.08f))
        Level.INFO   -> Triple(Icons.Default.Lightbulb, Ink2, Paper)
    }

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = tint)
            }
            Spacer(Modifier.width(12.dp))
            Text(a.title, style = H2.copy(fontSize = 15.sp))
        }
        Spacer(Modifier.height(12.dp))
        Text(a.body, style = Body2)
    }
}

// ============ HELPERS ============
@Composable
private fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.ReceiptLong, null, Modifier.size(40.dp), tint = Ink3)
        Spacer(Modifier.height(14.dp))
        Text(text, style = Body2)
    }
}

private fun categoryIcon(cat: String) = when (cat) {
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
