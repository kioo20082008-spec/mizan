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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

// ============ الألوان ============
private val Bg = Color(0xFF0A0E1A)
private val Surface1 = Color(0xFF141A29)
private val Surface2 = Color(0xFF1C2438)
private val Primary = Color(0xFF7C6BFF)
private val PrimaryDark = Color(0xFF5B4FE8)
private val Accent = Color(0xFF00E5A0)
private val Danger = Color(0xFFFF4D6D)
private val Warn = Color(0xFFFFB347)
private val TextPri = Color(0xFFF1F5FF)
private val TextSec = Color(0xFF8B94B8)

// ============ نقطة الدخول ============
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
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Surface1,
            primary = Primary,
            onBackground = TextPri,
            onSurface = TextPri,
            surfaceVariant = Surface2,
            onSurfaceVariant = TextSec,
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            if (!hasSms) PermissionScreen {
                launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
            } else HomeScaffold(vm)
        }
    }
}

private fun hasSmsPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

// ============ شاشة الصلاحية ============
@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(120.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Primary.copy(alpha = 0.4f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(80.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Primary, PrimaryDark))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AccountBalanceWallet, null,
                    Modifier.size(40.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("ميزان", fontSize = 36.sp, fontWeight = FontWeight.Black, color = TextPri)
        Spacer(Modifier.height(8.dp))
        Text("مديرك المالي الذكي", fontSize = 16.sp, color = TextSec)
        Spacer(Modifier.height(40.dp))
        Text(
            "لأتمكن من تتبع مصاريفك تلقائياً أحتاج قراءة رسائل البنك (SMS).",
            fontSize = 15.sp, textAlign = TextAlign.Center, color = TextSec, lineHeight = 22.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.clip(RoundedCornerShape(14.dp))
                .background(Accent.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(18.dp), tint = Accent)
            Spacer(Modifier.width(8.dp))
            Text("كل البيانات تبقى على جهازك فقط", fontSize = 13.sp, color = Accent)
        }
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text("السماح بقراءة الرسائل", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ============ الهيكل الرئيسي ============
@Composable
private fun HomeScaffold(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            0 -> DashboardScreen(vm)
            1 -> TransactionsScreen(vm)
            2 -> BudgetScreen(vm)
            else -> AdvisorScreen(vm)
        }
        // شريط سفلي عائم
        FloatingNavBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun FloatingNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple("الرئيسية", Icons.Filled.Home, Icons.Outlined.Home),
        Triple("العمليات", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
        Triple("الميزانية", Icons.Filled.Savings, Icons.Outlined.Savings),
        Triple("المستشار", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    )
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(28.dp), spotColor = Color.Black)
            .clip(RoundedCornerShape(28.dp))
            .background(Surface1)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEachIndexed { index, (label, filled, outlined) ->
            val isSelected = selected == index
            val bgColor by animateColorAsState(
                if (isSelected) Primary.copy(alpha = 0.15f) else Color.Transparent,
                tween(250), label = "bg"
            )
            val contentColor by animateColorAsState(
                if (isSelected) Primary else TextSec,
                tween(250), label = "content"
            )
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (isSelected) filled else outlined,
                    label,
                    Modifier.size(22.dp),
                    tint = contentColor
                )
                Spacer(Modifier.height(2.dp))
                Text(label, fontSize = 10.sp, color = contentColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

// ============ 1) الرئيسية ============
@Composable
private fun DashboardScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    var offset by remember { mutableIntStateOf(0) }

    val range = remember(offset) { Dates.monthRange(offset) }
    val summary = remember(txs, offset) { FinancialAdvisor.summarize(txs, range.first, range.last) }
    val monthlyBudget = budgets.firstOrNull {
        it.monthKey == Dates.monthKey(offset) && it.category == TOTAL_BUDGET
    }?.limitAmount ?: budgets.firstOrNull { it.monthKey == ALL_MONTHS && it.category == TOTAL_BUDGET }?.limitAmount ?: 0.0

    val advice = remember(summary, monthlyBudget) {
        FinancialAdvisor.advise(summary, monthlyBudget, txs, range.first, range.last)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("مساء الخير 👋", fontSize = 13.sp, color = TextSec)
                    Text("ميزانك", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPri)
                }
                IconButton(
                    onClick = { },
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Surface1)
                ) { Icon(Icons.Outlined.Notifications, null, tint = TextPri) }
            }
        }

        item { MonthSwitcher(offset, onPrev = { offset-- }, onNext = { if (offset < 0) offset++ }) }

        item { HeroBalanceCard(summary) }

        if (monthlyBudget > 0) {
            item { BudgetRingCard(summary.spent, monthlyBudget) }
        }

        if (summary.categoryTotals.isNotEmpty()) {
            item {
                Text("توزيع المصاريف", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = TextPri, modifier = Modifier.padding(top = 8.dp))
            }
            items(summary.categoryTotals.take(6)) { cat ->
                CategoryBarRow(cat, summary.spent)
            }
        }

        advice.firstOrNull()?.let { a ->
            item {
                Text("نصيحة اليوم", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = TextPri, modifier = Modifier.padding(top = 8.dp))
            }
            item { AdviceCard(a) }
        }

        if (txs.isEmpty()) {
            item { EmptyState() }
        }
    }
}

@Composable
private fun MonthSwitcher(offset: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronRight, null, tint = TextSec) }
        Text(
            monthName(offset),
            Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPri,
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronLeft, null, tint = if (offset < 0) TextSec else TextSec.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun HeroBalanceCard(s: MonthSummary) {
    val netColor = if (s.net >= 0) Accent else Danger
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Primary, PrimaryDark, Color(0xFF3B2DB8))))
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(20.dp), tint = Color.White) }
                Spacer(Modifier.width(10.dp))
                Text("صافي هذا الشهر", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    FinancialAdvisor.fmt(kotlin.math.abs(s.net)),
                    fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White
                )
                Spacer(Modifier.width(6.dp))
                Text("ر.س", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(vertical = 12.dp)
            ) {
                HeroStat("المصروف", FinancialAdvisor.fmt(s.spent), Danger, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.15f)))
                HeroStat("الدخل", FinancialAdvisor.fmt(s.income), Accent, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.15f)))
                HeroStat("العمليات", s.count.toString(), Color.White, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, color: Color, mod: Modifier = Modifier) {
    Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun BudgetRingCard(spent: Double, budget: Double) {
    val pct = (spent / budget).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        spent > budget -> Danger
        pct > 0.8f -> Warn
        else -> Accent
    }
    val animated by animateFloatAsState(pct, tween(800), label = "ring")

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface1)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = color.copy(alpha = 0.15f),
                strokeWidth = 8.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = 8.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Text("${(pct * 100).toInt()}%", fontSize = 16.sp,
                fontWeight = FontWeight.Black, color = color)
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text("الميزانية الشهرية", fontSize = 14.sp,
                fontWeight = FontWeight.Bold, color = TextPri)
            Spacer(Modifier.height(4.dp))
            Text(
                "صرفت ${FinancialAdvisor.fmt(spent)} من ${FinancialAdvisor.fmt(budget)} ر.س",
                fontSize = 12.sp, color = TextSec
            )
            Spacer(Modifier.height(6.dp))
            val remaining = budget - spent
            Text(
                if (remaining >= 0) "باقي لك ${FinancialAdvisor.fmt(remaining)} ر.س"
                else "تجاوزت بـ ${FinancialAdvisor.fmt(-remaining)} ر.س",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color
            )
        }
    }
}

@Composable
private fun CategoryBarRow(cat: com.mizan.money.advisor.CategoryTotal, total: Double) {
    val frac = if (total > 0) (cat.amount / total).toFloat() else 0f
    val animated by animateFloatAsState(frac, tween(700), label = "bar")
    val icon = categoryIcon(cat.category)
    val color = categoryColor(cat.category)

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(22.dp), tint = color) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat.category, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPri)
                Spacer(Modifier.weight(1f))
                Text("${FinancialAdvisor.fmt(cat.amount)}", fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, color = TextPri)
                Spacer(Modifier.width(4.dp))
                Text("ر.س", fontSize = 11.sp, color = TextSec)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(Surface2)
            ) {
                Box(
                    Modifier.fillMaxWidth(animated).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.6f))))
                )
            }
        }
    }
}

// ============ 2) العمليات ============
@Composable
private fun TransactionsScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val grouped = remember(txs) { txs.groupBy { Dates.dayLabel(it.timestamp) } }

    Box(Modifier.fillMaxSize()) {
        if (txs.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("العمليات", fontSize = 26.sp, fontWeight = FontWeight.Black,
                        color = TextPri, modifier = Modifier.padding(bottom = 8.dp))
                }
                grouped.forEach { (day, list) ->
                    item {
                        Text(day, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = TextSec, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.id }) { tx -> TransactionRow(tx) { vm.delete(tx) } }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 110.dp),
            containerColor = Primary,
            shape = RoundedCornerShape(20.dp)
        ) { Icon(Icons.Default.Add, "إضافة", tint = Color.White) }
    }

    if (showAdd) AddTransactionDialog(
        onDismiss = { showAdd = false },
        onSave = { amt, merch, cat, type -> vm.addManual(amt, merch, cat, type); showAdd = false }
    )
}

@Composable
private fun TransactionRow(tx: TransactionEntity, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val color = categoryColor(tx.category)
    val icon = categoryIcon(tx.category)
    val sign = if (tx.type == TxType.EXPENSE) "-" else "+"
    val amtColor = if (tx.type == TxType.EXPENSE) Danger else Accent

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, Modifier.size(22.dp), tint = color) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.merchant ?: "غير معروف", fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, color = TextPri)
                Spacer(Modifier.height(2.dp))
                Text("${tx.category}  ·  ${if (tx.isManual) "يدوي" else (tx.bankName ?: "SMS")}",
                    fontSize = 11.sp, color = TextSec)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$sign${FinancialAdvisor.fmt(tx.amount)}",
                    fontWeight = FontWeight.Black, fontSize = 16.sp, color = amtColor)
                Spacer(Modifier.height(2.dp))
                Text("ر.س", fontSize = 10.sp, color = TextSec)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Surface2)
            Spacer(Modifier.height(10.dp))
            Text(tx.rawSms, fontSize = 12.sp, color = TextSec, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Danger.copy(alpha = 0.1f))
                    .clickable { onDelete() }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = Danger)
                Spacer(Modifier.width(6.dp))
                Text("حذف", color = Danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
        containerColor = Surface1,
        shape = RoundedCornerShape(28.dp),
        title = { Text("إضافة عملية", fontWeight = FontWeight.Bold, color = TextPri) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = Surface2
                    )
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("الجهة / التاجر") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = Surface2
                    )
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    FilterChip(
                        selected = type == TxType.EXPENSE,
                        onClick = { type = TxType.EXPENSE },
                        label = { Text("مصروف") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Danger.copy(alpha = 0.2f),
                            selectedLabelColor = Danger
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = type == TxType.INCOME,
                        onClick = { type = TxType.INCOME },
                        label = { Text("دخل") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.2f),
                            selectedLabelColor = Accent
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPri)
                    ) { Text("التصنيف: $category") }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Surface2
                    ) {
                        CategoryClassifier.categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, color = TextPri) },
                                onClick = { category = c; menuOpen = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { amount.toDoubleOrNull()?.let { onSave(it, merchant, category, type) } },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = TextSec) }
        }
    )
}

// ============ 3) الميزانية ============
@Composable
private fun BudgetScreen(vm: MainViewModel) {
    val budgets by vm.budgets.collectAsState()
    val monthKey = Dates.monthKey(0)
    val txs by vm.transactions.collectAsState()
    val range = Dates.monthRange(0)
    val summary = remember(txs) { FinancialAdvisor.summarize(txs, range.first, range.last) }

    var totalInput by remember {
        mutableStateOf(budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }
            ?.limitAmount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("الميزانية", fontSize = 26.sp, fontWeight = FontWeight.Black, color = TextPri)
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Surface1)) {
                Column(Modifier.padding(20.dp)) {
                    Text("الميزانية الكلية", fontSize = 14.sp, color = TextSec)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = totalInput,
                        onValueChange = { totalInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("الحد الشهري") },
                        suffix = { Text("ر.س", color = TextSec) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = Surface2
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) } },
                        Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) { Text("حفظ الميزانية", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Primary.copy(alpha = 0.1f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = Primary)
                Spacer(Modifier.width(10.dp))
                Text("صرفت هذا الشهر: ${FinancialAdvisor.fmt(summary.spent)} ر.س",
                    fontSize = 13.sp, color = TextPri)
            }
        }
        item {
            Text("ميزانية لكل تصنيف", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = TextPri, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }
        items(CategoryClassifier.categories) { cat ->
            val spentInCat = summary.categoryTotals.firstOrNull { it.category == cat }?.amount ?: 0.0
            val color = categoryColor(cat)
            val icon = categoryIcon(cat)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, Modifier.size(18.dp), tint = color) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(cat, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPri)
                    Text("صرفت ${FinancialAdvisor.fmt(spentInCat)} ر.س", fontSize = 11.sp, color = TextSec)
                }
                OutlinedTextField(
                    value = catInputs[cat] ?: "",
                    onValueChange = { v ->
                        catInputs = catInputs + (cat to v.filter { ch -> ch.isDigit() || ch == '.' })
                    },
                    placeholder = { Text("0", color = TextSec, fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(90.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = Surface2
                    )
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { (catInputs[cat]?.toDoubleOrNull() ?: 0.0).let { vm.setBudget(monthKey, cat, it) } },
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Primary)
                ) { Icon(Icons.Default.Check, "حفظ", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

// ============ 4) المستشار ============
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("المستشار", fontSize = 26.sp, fontWeight = FontWeight.Black, color = TextPri)
        }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(
                        listOf(Color(0xFF2B2350), Color(0xFF3B2DB8))
                    ))
                    .padding(24.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(26.dp), tint = Color(0xFFFFD166)) }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("تقرير ذكي", fontSize = 17.sp,
                                fontWeight = FontWeight.Black, color = Color.White)
                            Text("مخصص لك هذا الشهر", fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "راجعت ${summary.count} عملية بإجمالي ${FinancialAdvisor.fmt(summary.spent)} ر.س. إليك ${advice.size} ملاحظة:",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 20.sp
                    )
                }
            }
        }
        items(advice) { a -> AdviceCard(a) }
    }
}

@Composable
private fun AdviceCard(a: Advice) {
    val color = when (a.level) {
        Level.DANGER -> Danger
        Level.WARN -> Warn
        Level.GOOD -> Accent
        Level.INFO -> Primary
    }
    val icon = when (a.level) {
        Level.DANGER -> Icons.Default.Warning
        Level.WARN -> Icons.Default.Info
        Level.GOOD -> Icons.Default.CheckCircle
        Level.INFO -> Icons.Default.Lightbulb
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .padding(16.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(20.dp), tint = color) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
            Spacer(Modifier.height(6.dp))
            Text(a.body, fontSize = 13.sp, color = TextSec, lineHeight = 20.sp)
        }
    }
}

// ============ مساعدات ============
@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(Surface1),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.ReceiptLong, null, Modifier.size(36.dp), tint = TextSec) }
        Spacer(Modifier.height(20.dp))
        Text("لا توجد عمليات بعد", fontSize = 16.sp,
            fontWeight = FontWeight.Bold, color = TextPri)
        Spacer(Modifier.height(6.dp))
        Text("أضف عملية يدوياً من تبويب العمليات",
            fontSize = 13.sp, color = TextSec, textAlign = TextAlign.Center)
    }
}

private fun categoryIcon(cat: String): ImageVector = when (cat) {
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

private fun categoryColor(cat: String): Color = when (cat) {
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

private fun monthName(offset: Int): String {
    val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset) }
    val names = listOf("يناير","فبراير","مارس","أبريل","مايو","يونيو",
        "يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر")
    return "${names[c.get(java.util.Calendar.MONTH)]} ${c.get(java.util.Calendar.YEAR)}"
}
