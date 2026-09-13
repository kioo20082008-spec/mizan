package com.mizan.money.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!hasSms) PermissionScreen {
                launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
            } else HomeScaffold(vm)
        }
    }
}

private fun hasSmsPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("ميزان — مديرك المالي", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("لأتمكن من تتبع مصاريفك تلقائياً أحتاج قراءة رسائل البنك (SMS).\n\n🔒 كل البيانات تبقى على جهازك فقط.",
            fontSize = 15.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGrant, Modifier.fillMaxWidth().height(52.dp)) {
            Text("السماح بقراءة الرسائل", fontSize = 16.sp)
        }
    }
}

@Composable
private fun HomeScaffold(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Triple("الرئيسية", Icons.Default.Home, 0),
        Triple("العمليات", Icons.Default.ReceiptLong, 1),
        Triple("الميزانية", Icons.Default.Savings, 2),
        Triple("المستشار", Icons.Default.Psychology, 3)
    )
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEach { (label, icon, idx) ->
                NavigationBarItem(selected = tab == idx, onClick = { tab = idx },
                    icon = { Icon(icon, label) }, label = { Text(label, fontSize = 11.sp) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> DashboardScreen(vm)
                1 -> TransactionsScreen(vm)
                2 -> BudgetScreen(vm)
                else -> AdvisorScreen(vm)
            }
        }
    }
}

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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) { Icon(Icons.Default.ChevronRight, null) }
                Text(monthName(offset), Modifier.weight(1f), fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                IconButton(onClick = { if (offset < 0) offset++ }) { Icon(Icons.Default.ChevronLeft, null) }
            }
        }
        item { BalanceCard(summary) }
        if (monthlyBudget > 0) item { BudgetProgressCard(summary.spent, monthlyBudget) }
        if (summary.categoryTotals.isNotEmpty()) {
            item { SectionTitle("توزيع المصاريف") }
            items(summary.categoryTotals) { cat -> CategoryRow(cat, summary.spent) }
        }
        advice.firstOrNull()?.let { a ->
            item { SectionTitle("نصيحة المدير المالي") }
            item { AdviceCard(a) }
        }
        if (txs.isEmpty()) {
            item { EmptyHint("لم أرصد أي عملية بعد.\nأضف عملية يدوياً من تبويب «العمليات».") }
        }
    }
}

@Composable
private fun BalanceCard(s: MonthSummary) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("صافي الشهر", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${FinancialAdvisor.fmt(s.net)} ر.س", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                color = if (s.net >= 0) Color(0xFF4CAF50) else Color(0xFFE53935))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("المصروف", FinancialAdvisor.fmt(s.spent), Color(0xFFE53935), Modifier.weight(1f))
                Stat("الدخل", FinancialAdvisor.fmt(s.income), Color(0xFF4CAF50), Modifier.weight(1f))
                Stat("العمليات", s.count.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, mod: Modifier = Modifier) {
    Column(mod) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun BudgetProgressCard(spent: Double, budget: Double) {
    val pct = (spent / budget).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        spent > budget -> Color(0xFFE53935)
        pct > 0.8f -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الميزانية الشهرية", fontWeight = FontWeight.SemiBold)
                Text("${(spent / budget * 100).toInt()}%", color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { pct },
                modifier = Modifier.fillMaxWidth().height(10.dp), color = color,
                trackColor = color.copy(alpha = 0.15f))
            Spacer(Modifier.height(10.dp))
            Text("صرفت ${FinancialAdvisor.fmt(spent)} من ${FinancialAdvisor.fmt(budget)} ر.س",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryRow(cat: com.mizan.money.advisor.CategoryTotal, total: Double) {
    val frac = if (total > 0) (cat.amount / total).toFloat() else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(cat.category, fontSize = 14.sp)
            Text("${FinancialAdvisor.fmt(cat.amount)} ر.س · ${(cat.share * 100).toInt()}%",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { frac },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun TransactionsScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val grouped = remember(txs) { txs.groupBy { Dates.dayLabel(it.timestamp) } }
    Box(Modifier.fillMaxSize()) {
        if (txs.isEmpty()) EmptyHint("لا توجد عمليات بعد.")
        else LazyColumn(Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            grouped.forEach { (day, list) ->
                item {
                    Text(day, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(list, key = { it.id }) { tx -> TransactionRow(tx) { vm.delete(tx) } }
            }
        }
        FloatingActionButton(onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Default.Add, "إضافة")
        }
    }
    if (showAdd) AddTransactionDialog(
        onDismiss = { showAdd = false },
        onSave = { amt, merch, cat, type -> vm.addManual(amt, merch, cat, type); showAdd = false }
    )
}

@Composable
private fun TransactionRow(tx: TransactionEntity, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(
                    MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(if (tx.type == TxType.EXPENSE) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        null, tint = if (tx.type == TxType.EXPENSE) Color(0xFFE53935) else Color(0xFF4CAF50))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tx.merchant ?: "غير معروف", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("${tx.category} · ${if (tx.isManual) "يدوي" else (tx.bankName ?: "SMS")}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text((if (tx.type == TxType.EXPENSE) "-" else "+") + FinancialAdvisor.fmt(tx.amount),
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = if (tx.type == TxType.EXPENSE) Color(0xFFE53935) else Color(0xFF4CAF50))
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(tx.rawSms, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("حذف")
                }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة عملية") },
        text = {
            Column {
                OutlinedTextField(value = amount, onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = merchant, onValueChange = { merchant = it },
                    label = { Text("الجهة / التاجر") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row {
                    FilterChip(selected = type == TxType.EXPENSE,
                        onClick = { type = TxType.EXPENSE }, label = { Text("مصروف") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = type == TxType.INCOME,
                        onClick = { type = TxType.INCOME }, label = { Text("دخل") })
                }
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, Modifier.fillMaxWidth()) {
                        Text("التصنيف: $category")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        CategoryClassifier.categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { category = c; menuOpen = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { amount.toDoubleOrNull()?.let { onSave(it, merchant, category, type) } }) {
                Text("حفظ")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("الميزانية الكلية لشهر $monthKey") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = totalInput,
                    onValueChange = { totalInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("الحد الشهري (ر.س)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Button(onClick = { totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) } }) {
                    Text("حفظ")
                }
            }
        }
        item {
            Text("صرفت حتى الآن: ${FinancialAdvisor.fmt(summary.spent)} ر.س",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionTitle("ميزانية لكل تصنيف (اختياري)") }
        items(CategoryClassifier.categories) { cat ->
            val spentInCat = summary.categoryTotals.firstOrNull { it.category == cat }?.amount ?: 0.0
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(cat, fontSize = 14.sp)
                    Text("صرفت ${FinancialAdvisor.fmt(spentInCat)} ر.س",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = catInputs[cat] ?: "",
                    onValueChange = { v -> catInputs = catInputs + (cat to v.filter { ch -> ch.isDigit() || ch == '.' }) },
                    placeholder = { Text("0", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(110.dp), singleLine = true)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    (catInputs[cat]?.toDoubleOrNull() ?: 0.0).let { vm.setBudget(monthKey, cat, it) }
                }) { Icon(Icons.Default.Check, "حفظ") }
            }
        }
    }
}

@Composable
private fun AdvisorScreen(vm: MainViewModel) {
    val txs by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val range = Dates.monthRange(0)
    val summary = remember(txs) { FinancialAdvisor.summarize(txs, range.first, range.last) }
    val budget = budgets.firstOrNull { it.monthKey == Dates.monthKey(0) && it.category == TOTAL_BUDGET }?.limitAmount ?: 0.0
    val advice = remember(summary, budget, txs) {
        FinancialAdvisor.advise(summary, budget, txs, range.first, range.last)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("تقرير مديرك المالي", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("راجعت ${summary.count} عملية هذا الشهر بإجمالي ${FinancialAdvisor.fmt(summary.spent)} ر.س. إليك ما وجدته:",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(advice) { AdviceCard(it) }
    }
}

@Composable
private fun AdviceCard(a: Advice) {
    val color = when (a.level) {
        Level.DANGER -> Color(0xFFE53935)
        Level.WARN -> Color(0xFFFF9800)
        Level.GOOD -> Color(0xFF4CAF50)
        Level.INFO -> MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp)) {
            Box(Modifier.width(4.dp).height(56.dp).background(color, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(a.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
                Spacer(Modifier.height(4.dp))
                Text(a.body, fontSize = 13.5.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun monthName(offset: Int): String {
    val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset) }
    val names = listOf("يناير","فبراير","مارس","أبريل","مايو","يونيو",
        "يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر")
    return "${names[c.get(java.util.Calendar.MONTH)]} ${c.get(java.util.Calendar.YEAR)}"
}
