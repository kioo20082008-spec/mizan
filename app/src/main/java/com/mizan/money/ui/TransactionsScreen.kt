package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import com.mizan.money.sms.CategoryClassifier

// ============ TRANSACTIONS ============
@Composable
fun TransactionsScreen(vm: MainViewModel, initialQuery: String? = null) {
    val txs by vm.transactions.collectAsState()
    var query by remember(initialQuery) { mutableStateOf(initialQuery ?: "") }
    var selected by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val filtered = remember(txs, query) {
        if (query.isBlank()) txs
        else txs.filter {
            (it.merchant ?: "").contains(query, ignoreCase = true) ||
            it.category.contains(query, ignoreCase = true) ||
            FinancialAdvisor.fmt(it.amount).contains(query)
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
                    Modifier.size(48.dp).clip(RoundedCornerShape(RadiusSm)).background(Ink900)
                        .clickable { showAdd = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, "إضافة عملية يدوية", tint = Lime, modifier = Modifier.size(22.dp)) }
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
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = Body
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "مسح البحث", Modifier.size(16.dp), tint = InkFaint)
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item { EmptyState(if (txs.isEmpty()) "لا توجد عمليات بعد — أضف عملية أو امسح رسائل البنك" else "لا توجد عمليات مطابقة لبحثك") }
        } else {
            items(filtered, key = { it.id }) { tx ->
                TransactionCard(tx, onClick = { selected = tx })
            }
        }
    }

    selected?.let { current ->
        TxDetailDialog(
            tx = current,
            onDismiss = { selected = null },
            onDelete = { vm.delete(current); selected = null },
            onSave = { updated -> vm.update(updated); selected = null }
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
private fun TxDetailDialog(
    tx: TransactionEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (TransactionEntity) -> Unit
) {
    var editing by remember(tx.id) { mutableStateOf(false) }
    var amount by remember(tx.id) { mutableStateOf("%.2f".format(tx.amount)) }
    var merchant by remember(tx.id) { mutableStateOf(tx.merchant ?: "") }
    var category by remember(tx.id) { mutableStateOf(tx.category) }
    var type by remember(tx.id) { mutableStateOf(tx.type) }
    var isSelfTransfer by remember(tx.id) { mutableStateOf(tx.isSelfTransfer) }
    var confirmingDelete by remember(tx.id) { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text("حذف هذه العملية؟", style = H2) },
            text = { Text("لا يمكن التراجع عن هذا الإجراء.", style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text("حذف", style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("إلغاء", style = Body.copy(color = InkSoft)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text(if (editing) "تعديل العملية" else "تفاصيل العملية", style = H2) },
        text = {
            if (editing) {
                Column {
                    SegmentedToggle(
                        options = listOf("مصروف" to Danger, "دخل" to Success),
                        selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                        onSelect = { type = if (it == 0) TxType.EXPENSE else TxType.INCOME }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = sanitizeAmountInput(it) },
                        label = { Text("المبلغ (${currencyLabel(tx.currency)})") },
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
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(RadiusSm))
                            .background(PaperOuter)
                            .clickable { isSelfTransfer = !isSelfTransfer }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("تحويل بين حساباتك", style = Body.copy(fontWeight = FontWeight.Medium))
                            Text("لا يُحتسب ضمن دخلك أو مصاريفك", style = Eyebrow.copy(fontSize = 11.sp))
                        }
                        Switch(
                            checked = isSelfTransfer,
                            onCheckedChange = { checked ->
                                isSelfTransfer = checked
                                // Mirrors what the SMS parser itself does (see
                                // ParsedSms.toEntity()), so a manually-flagged self
                                // transfer can't be left pointing at an unrelated
                                // category chip (e.g. "طعام وشراب") once saved.
                                if (checked) category = com.mizan.money.data.SELF_TRANSFER_CATEGORY
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
                        )
                    }
                    if (!isSelfTransfer) {
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
                }
            } else {
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
                            "${if (tx.type == TxType.EXPENSE) "-" else "+"}${FinancialAdvisor.fmt(tx.amount)} ${currencyLabel(tx.currency)}",
                            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black,
                                color = if (tx.isSelfTransfer) InkFaint else if (tx.type == TxType.EXPENSE) Danger else Success)
                        )
                        if (tx.isSelfTransfer) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "تحويل بين حساباتك — لا يُحتسب ضمن دخلك أو مصاريفك",
                                style = Eyebrow.copy(fontSize = 11.sp),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text("حذف العملية", style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                    }
                }
            }
        },
        confirmButton = {
            if (editing) {
                TextButton(onClick = {
                    amount.toDoubleOrNull()?.let {
                        onSave(tx.copy(amount = it, merchant = merchant.ifBlank { null }, category = category, type = type, isSelfTransfer = isSelfTransfer))
                    }
                }) { Text("حفظ", style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)) }
            } else {
                TextButton(onClick = { editing = true }) {
                    Text("تعديل", style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
                }
            }
        },
        dismissButton = {
            if (editing) {
                TextButton(onClick = { editing = false }) { Text("إلغاء", style = Body.copy(color = InkSoft)) }
            } else {
                TextButton(onClick = onDismiss) { Text("إغلاق", style = Body.copy(color = InkSoft)) }
            }
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
                    value = amount, onValueChange = { amount = sanitizeAmountInput(it) },
                    label = { Text("المبلغ (${currencyLabel("SAR")})") },
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
