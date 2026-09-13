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
import androidx.compose.foundation.shape.CutCornerShape
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

// ================== Colors ==================
private val Paper   = Color(0xFFE5D9BD)
private val Ink     = Color(0xFF182A20)
private val InkSoft = Color(0xFF243D32)
private val Bronze  = Color(0xFF8C6239)
private val BronzeText = Color(0xFFD9A56B)
private val Crimson = Color(0xFF7A2818)
private val Olive   = Color(0xFF4F6B43)
private val Slate   = Color(0xFF5F5A4C)

// ================== Typography ==================
private val Num = TextStyle(fontFamily = FontFamily.Monospace)
private val Lbl = TextStyle(color = Slate, fontSize = 11.sp)
private val Sec = TextStyle(color = Slate, fontSize = 12.sp)

// ================== AppRoot ==================
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

    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Paper,
            surface = Paper,
            surfaceVariant = InkSoft,
            surfaceTint = Color.Transparent,
            primary = Bronze,
            onPrimary = Paper,
            onBackground = Ink,
            onSurface = Ink,
            onSurfaceVariant = Slate,
            error = Crimson,
            onError = Paper,
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = Paper) {
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

// ================== Permission ==================
@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ميزان", fontSize = 42.sp, fontWeight = FontWeight.Black, color = Ink)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(48.dp).height(2.dp).background(Bronze))
        Spacer(Modifier.height(14.dp))
        Text("دفترك المالي الشخصي", fontSize = 15.sp, color = Slate)
        Spacer(Modifier.height(48.dp))

        Row(
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(2.dp))
                .background(Paper)
                .padding(1.dp)
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(Bronze))
            Column(Modifier.padding(16.dp)) {
                Text("نقرأ رسائل بنكك تلقائياً", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text("لتصنيف مصاريفك وعرض تحليل ذكي.", fontSize = 13.sp, color = Slate)
                Spacer(Modifier.height(10.dp))
                Text("لا شيء يخرج من جهازك.", fontSize = 12.sp, color = Bronze)
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink)
        ) {
            Text("ابدأ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paper)
        }
    }
}

// ================== Home scaffold ==================
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.copy(alpha = 0.12f)))
        Row(
            Modifier.fillMaxWidth().background(Paper).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (label, icon, idx) ->
                val isSel = selected == idx
                val content by animateColorAsState(
                    if (isSel) Ink else Slate, tween(200), label = "c"
                )
                Column(
                    Modifier.weight(1f).clickable { onSelect(idx) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, label, Modifier.size(22.dp), tint = content)
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontSize = 10.sp, color = content,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.width(if (isSel) 20.dp else 0.dp)
                            .height(2.dp).background(Bronze)
                    )
                }
            }
        }
    }
}

// ================== Dashboard ==================
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { MonthSwitcher(offset, onPrev = { offset-- }, onNext = { if (offset < 0) offset++ }) }
        item { HeroBalanceCard(summary) }
        if (monthlyBudget > 0) item { BudgetBarCard(summary.spent, monthlyBudget) }
        if (summary.categoryTotals.isNotEmpty()) {
            item { SectionLabel("التصنيفات") }
            items(summary.categoryTotals) { cat -> CategoryRow(cat, summary.spent) }
        }
        if (txs.isEmpty()) item { EmptyRow("لا توجد عمليات بعد") }
    }
}

@Composable
private fun MonthSwitcher(offset: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = if (offset < 0) Ink else Ink.copy(alpha = 0.25f))
        }
        Text(
            monthName(offset),
            Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Ink)
        }
    }
}

@Composable
private fun HeroBalanceCard(s: MonthSummary) {
    val counter by animateFloatAsState(
        targetValue = s.net.toFloat(),
        animationSpec = tween(900),
        label = "count"
    )
    Box(
        Modifier.fillMaxWidth()
            .clip(CutCornerShape(topEnd = 20.dp))
            .background(Ink)
            .padding(22.dp)
    ) {
        Column {
            Text("صافي الشهر", fontSize = 11.sp, color = Paper.copy(alpha = 0.65f))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    FinancialAdvisor.fmt(kotlin.math.abs(counter.toDouble())),
                    style = Num.copy(
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = BronzeText
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text("ر.س", fontSize = 14.sp,
                    color = Paper.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 5.dp))
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroMini("المصروف", s.spent, Modifier.weight(1f))
                HeroMini("الدخل", s.income, Modifier.weight(1f))
                HeroMini("العمليات", s.count.toDouble(), Modifier.weight(1f), isCount = true)
            }
        }
    }
}

@Composable
private fun HeroMini(label: String, value: Double, mod: Modifier = Modifier, isCount: Boolean = false) {
    Column(mod, horizontalAlignment = Alignment.Start) {
        Text(label, fontSize = 10.sp, color = Paper.copy(alpha = 0.55f))
        Spacer(Modifier.height(3.dp))
        Text(
            if (isCount) value.toInt().toString() else FinancialAdvisor.fmt(value),
            style = Num.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Paper)
        )
    }
}

@Composable
private fun BudgetBarCard(spent: Double, budget: Double) {
    val pct = (spent / budget).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        spent > budget -> Crimson
        pct > 0.8f -> Bronze
        else -> Olive
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("الميزانية", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.weight(1f))
            Text("${(spent / budget * 100).toInt()}%",
                style = Num.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color))
        }
        Spacer(Modifier.height(8.dp))
        // RTL: bar fills from right (LayoutDirection handles it)
        Box(
            Modifier.fillMaxWidth().height(8.dp).background(Ink.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier.fillMaxWidth(pct).fillMaxHeight().background(color)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            FinancialAdvisor.fmt(spent) + " / " + FinancialAdvisor.fmt(budget) + " ر.س",
            style = Num.copy(fontSize = 12.sp, color = Slate)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Column {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.copy(alpha = 0.12f)))
    }
}

@Composable
private fun CategoryRow(cat: com.mizan.money.advisor.CategoryTotal, total: Double) {
    val frac = if (total > 0) (cat.amount / total).toFloat() else 0f
    val animated by animateFloatAsState(frac, tween(700), label = "bar")
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(16.dp).background(Bronze))
            Spacer(Modifier.width(10.dp))
            Text(cat.category, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(FinancialAdvisor.fmt(cat.amount),
                style = Num.copy(fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(4.dp))
            Text("ر.س", fontSize = 10.sp, color = Slate)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(Ink.copy(alpha = 0.06f))) {
            Box(Modifier.fillMaxWidth(animated).fillMaxHeight().background(Bronze))
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.copy(alpha = 0.08f)))
    }
}

// ================== Transactions ==================
@Composable
private fun TransactionsScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val grouped = remember(txs) { txs.groupBy { Dates.dayLabel(it.timestamp) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)
        ) {
            item {
                Text("العمليات", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(48.dp).height(2.dp).background(Bronze))
                Spacer(Modifier.height(12.dp))
            }
            grouped.forEach { (day, list) ->
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(day, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate)
                    Spacer(Modifier.height(4.dp))
                }
                items(list, key = { it.id }) { tx ->
                    TransactionRow(tx, onDelete = { vm.delete(tx) })
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.copy(alpha = 0.08f)))
                }
            }
            if (txs.isEmpty()) item { EmptyRow("لا توجد عمليات بعد") }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
            containerColor = Ink,
            shape = CircleShape
        ) { Icon(Icons.Default.Add, "إضافة", tint = Paper) }
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
    val amtColor = if (tx.type == TxType.EXPENSE) Crimson else Olive

    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(28.dp).background(Bronze.copy(alpha = 0.5f)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.merchant ?: "غير معروف",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    tx.category + " · " + (if (tx.isManual) "يدوي" else (tx.bankName ?: "SMS")),
                    fontSize = 11.sp, color = Slate
                )
            }
            Text(sign + FinancialAdvisor.fmt(tx.amount),
                style = Num.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = amtColor))
            Spacer(Modifier.width(4.dp))
            Text("ر.س", fontSize = 10.sp, color = Slate)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(tx.rawSms, fontSize = 11.sp, color = Slate, lineHeight = 17.sp)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Crimson)) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("حذف", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AddTransactionDialog(onDismiss: () -> Unit, onSave: (Double, String, String, TxType) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CategoryClassifier.categories.first()) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var menuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        shape = RoundedCornerShape(4.dp),
        title = { Text("إضافة عملية", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("الجهة") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    FilterChip(
                        selected = type == TxType.EXPENSE,
                        onClick = { type = TxType.EXPENSE },
                        label = { Text("مصروف") }
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = type == TxType.INCOME,
                        onClick = { type = TxType.INCOME },
                        label = { Text("دخل") }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text(category, color = Ink) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        CategoryClassifier.categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = { category = c; menuOpen = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { amount.toDoubleOrNull()?.let { onSave(it, merchant, category, type) } }) {
                Text("حفظ", color = Bronze, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = Slate) }
        }
    )
}

// ================== Budget ==================
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("الميزانية", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(48.dp).height(2.dp).background(Bronze))
        }
        item {
            Text("الميزانية الكلية", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = totalInput,
                    onValueChange = { totalInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("الحد الشهري") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) } },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) { Text("حفظ", color = Paper) }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("صرفت هذا الشهر: " + FinancialAdvisor.fmt(summary.spent) + " ر.س",
                fontSize = 13.sp, color = Slate)
        }
        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("ميزانية لكل تصنيف")
        }
        items(CategoryClassifier.categories) { cat ->
            val spentInCat = summary.categoryTotals.firstOrNull { it.category == cat }?.amount ?: 0.0
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat, fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                    Text(FinancialAdvisor.fmt(spentInCat) + " ر.س",
                        style = Num.copy(fontSize = 11.sp, color = Slate))
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = catInputs[cat] ?: "",
                        onValueChange = { v ->
                            catInputs = catInputs + (cat to v.filter { ch -> ch.isDigit() || ch == '.' })
                        },
                        placeholder = { Text("0", color = Slate) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            (catInputs[cat]?.toDoubleOrNull() ?: 0.0).let { vm.setBudget(monthKey, cat, it) }
                        },
                        modifier = Modifier.size(40.dp).background(Ink, RoundedCornerShape(4.dp))
                    ) { Icon(Icons.Default.Check, "حفظ", tint = Paper, modifier = Modifier.size(18.dp)) }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.copy(alpha = 0.08f)))
            }
        }
    }
}

// ================== Advisor ==================
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("المستشار", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(48.dp).height(2.dp).background(Bronze))
            Spacer(Modifier.height(12.dp))
        }
        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(CutCornerShape(topEnd = 16.dp))
                    .background(Ink)
                    .padding(18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = BronzeText)
                        Spacer(Modifier.width(8.dp))
                        Text("تقرير ذكي", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paper)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "راجعت " + summary.count.toString() + " عملية. إليك " + advice.size.toString() + " ملاحظة:",
                        fontSize = 12.sp, color = Paper.copy(alpha = 0.7f)
                    )
                }
            }
        }
        items(advice) { a -> AdviceRow(a) }
    }
}

@Composable
private fun AdviceRow(a: Advice) {
    val (color, icon) = when (a.level) {
        Level.DANGER -> Crimson to Icons.Default.Warning
        Level.WARN -> Bronze to Icons.Default.Info
        Level.GOOD -> Olive to Icons.Default.CheckCircle
        Level.INFO -> Slate to Icons.Default.Lightbulb
    }
    Row(
        Modifier.fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(2.dp))
            .background(Paper)
            .padding(1.dp)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(color))
        Row(Modifier.padding(14.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(a.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.height(6.dp))
                Text(a.body, fontSize = 12.sp, color = Slate, lineHeight = 18.sp)
            }
        }
    }
}

// ================== Helpers ==================
@Composable
private fun EmptyRow(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.ReceiptLong, null, Modifier.size(36.dp), tint = Slate)
        Spacer(Modifier.height(10.dp))
        Text(text, fontSize = 14.sp, color = Slate)
    }
}

private fun monthName(offset: Int): String {
    val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset) }
    val names = listOf("يناير","فبراير","مارس","أبريل","مايو","يونيو",
        "يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر")
    return names[c.get(java.util.Calendar.MONTH)] + " " + c.get(java.util.Calendar.YEAR)
}
