package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.TOTAL_BUDGET
import kotlin.math.roundToInt

// ============ BUDGET ============
@Composable
fun BudgetScreen(vm: MainViewModel, offset: Int) {
    val budgets by vm.budgets.collectAsState()
    val categories by vm.categories.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val monthKey = Dates.monthKey(offset, startDay)
    val txs by vm.transactions.collectAsState()
    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val summary = remember(txs, offset, startDay) { FinancialAdvisor.summarize(txs, range.first, range.last) }

    fun Double.toBudgetInput() = if (this % 1.0 == 0.0) toInt().toString() else toString()

    var totalInput by remember(monthKey) {
        mutableStateOf(
            budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }
                ?.limitAmount?.toBudgetInput() ?: ""
        )
    }
    var editingTotal by remember(monthKey) { mutableStateOf(false) }
    var catInputs by remember(monthKey) {
        mutableStateOf(
            categories.associateWith { c ->
                budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                    ?.limitAmount?.toBudgetInput() ?: ""
            }
        )
    }
    var editingCategory by remember(monthKey) { mutableStateOf<String?>(null) }
    var addingCategory by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    // Rollover toggle state per category — kept separate from the DB so a user
    // can flip it before hitting save, and so it discards on collapse without
    // saving (mirrors catInputs' behavior).
    var catRollover by remember(monthKey) {
        mutableStateOf(
            categories.associateWith { c ->
                budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                    ?.rolloverEnabled ?: false
            }
        )
    }

    // Previous month's spending by category — needed to compute how much of
    // last month's limit went unspent, which is what actually rolls forward.
    val prevMonthKey = Dates.monthKey(offset - 1, startDay)
    val prevRange = remember(offset, startDay) { Dates.monthRange(offset - 1, startDay) }
    val prevSpentByCat = remember(txs, offset, startDay) {
        FinancialAdvisor.summarize(txs, prevRange.first, prevRange.last)
            .categoryTotals.associate { it.category to it.amount }
    }
    val rolloverByCat = remember(budgets, prevSpentByCat, monthKey, prevMonthKey, categories) {
        categories.associateWith { c ->
            val cur = budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
            if (cur?.rolloverEnabled != true) 0.0
            else {
                val prev = budgets.firstOrNull { it.monthKey == prevMonthKey && it.category == c }
                if (prev == null) 0.0
                else (prev.limitAmount - (prevSpentByCat[c] ?: 0.0)).coerceAtLeast(0.0)
            }
        }
    }

    LaunchedEffect(budgets, monthKey, categories) {
        if (!editingTotal) {
            totalInput = budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }
                ?.limitAmount?.toBudgetInput() ?: ""
        }
        catInputs = categories.associateWith { c ->
            if (c == editingCategory) catInputs[c] ?: ""
            else budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                ?.limitAmount?.toBudgetInput() ?: ""
        }
        catRollover = categories.associateWith { c ->
            if (c == editingCategory) catRollover[c] ?: false
            else budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                ?.rolloverEnabled ?: false
        }
    }

    val spentByCat = remember(summary) { summary.categoryTotals.associate { it.category to it.amount } }
    val hasPrevBudget = budgets.any { it.monthKey == prevMonthKey }
    val hasCurrentBudget = budgets.any { it.monthKey == monthKey }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("الميزانية والتصنيفات — ${monthName(offset, startDay)}", style = H1)
            Text("راقب إنفاقك وقارنه بالحدود المحددة", style = Eyebrow)
        }

        if (!hasCurrentBudget && hasPrevBudget) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(IndigoSoft)
                        .clickable {
                            budgets.filter { it.monthKey == prevMonthKey }
                                .forEach { vm.setBudget(monthKey, it.category, it.limitAmount, it.rolloverEnabled) }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp), tint = Indigo)
                    Spacer(Modifier.width(10.dp))
                    Text("نسخ ميزانية الشهر الماضي", style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
                }
            }
        }

        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusXl))
                    .background(Brush.linearGradient(listOf(Ink800, Ink900)))
                    .padding(22.dp)
            ) {
                Column {
                    Text("الميزانية الإجمالية للشهر", style = Eyebrow.copy(color = OnInkSoft))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(FinancialAdvisor.fmt(totalInput.toDoubleOrNull() ?: 0.0), style = Display.copy(fontSize = 32.sp))
                        Spacer(Modifier.width(6.dp))
                        Text("ر.س", style = Body.copy(color = OnInkSoft, fontWeight = FontWeight.Medium))
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = totalInput,
                        onValueChange = { editingTotal = true; totalInput = sanitizeAmountInput(it) },
                        label = { Text("الحد الشهري") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body.copy(color = White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lime,
                            unfocusedBorderColor = White.copy(alpha = 0.2f),
                            focusedLabelColor = Lime,
                            unfocusedLabelColor = OnInkSoft,
                            cursorColor = Lime,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) }
                            editingTotal = false
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(RadiusSm),
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink900)
                    ) { Text("حفظ", style = Body.copy(color = Ink900, fontWeight = FontWeight.Bold)) }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text("الميزانية لكل تصنيف", style = H2, modifier = Modifier.weight(1f))
                Text(
                    if (addingCategory) "إلغاء" else "+ تصنيف",
                    style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clickable { addingCategory = !addingCategory; newCategoryInput = "" }
                        .padding(6.dp)
                )
            }
        }

        if (addingCategory) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        placeholder = { Text("اسم التصنيف", style = Eyebrow) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            vm.addCategory(newCategoryInput); newCategoryInput = ""; addingCategory = false
                        })
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(RadiusSm)).background(Indigo)
                            .clickable { vm.addCategory(newCategoryInput); newCategoryInput = ""; addingCategory = false },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Check, "إضافة", tint = White, modifier = Modifier.size(18.dp)) }
                }
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusLg))
                    .background(White)
                    .border(1.dp, Line, RoundedCornerShape(RadiusLg))
            ) {
                categories.forEachIndexed { index, cat ->
                    val spentInCat = spentByCat[cat] ?: 0.0
                    val limit = catInputs[cat]?.toDoubleOrNull() ?: 0.0
                    val rollover = rolloverByCat[cat] ?: 0.0
                    val effectiveLimit = limit + rollover
                    val pct = if (effectiveLimit > 0) (spentInCat / effectiveLimit).coerceIn(0.0, 1.0).toFloat() else 0f
                    val isOver = effectiveLimit > 0 && spentInCat > effectiveLimit
                    val isEditing = editingCategory == cat

                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { editingCategory = if (isEditing) null else cat }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(catIcon(cat), catColor(cat), catColorSoft(cat), size = 32.dp, iconSize = 15.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cat, style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                Text(
                                    if (effectiveLimit > 0) {
                                        "${FinancialAdvisor.fmt(spentInCat)} / ${FinancialAdvisor.fmt(effectiveLimit)} ر.س" +
                                            (if (rollover > 0.0) "  (+${FinancialAdvisor.fmt(rollover)} مرحّل)" else "")
                                    } else "${FinancialAdvisor.fmt(spentInCat)} ر.س — بدون حد",
                                    style = Eyebrow.copy(
                                        fontSize = 10.sp,
                                        color = if (isOver) Danger else InkFaint,
                                        fontWeight = if (isOver) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                            if (effectiveLimit > 0) {
                                Text(
                                    "${(pct * 100).roundToInt()}٪",
                                    style = Eyebrow.copy(
                                        fontSize = 10.sp,
                                        color = if (isOver) Danger else Indigo,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Icon(
                                if (isEditing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, Modifier.size(18.dp), tint = InkFaint
                            )
                        }
                        if (effectiveLimit > 0) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier.fillMaxWidth().height(4.dp)
                                    .clip(RoundedCornerShape(Pill))
                                    .background(PaperOuter)
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(pct).fillMaxHeight()
                                        .clip(RoundedCornerShape(Pill))
                                        .background(if (isOver) Danger else catColor(cat))
                                )
                            }
                        }
                        if (isEditing) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(RadiusSm))
                                    .background(PaperOuter)
                                    .clickable {
                                        catRollover = catRollover + (cat to !(catRollover[cat] ?: false))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("ترحيل فائض الشهر الماضي", style = Body.copy(fontWeight = FontWeight.Medium))
                                    Text("يُضاف أي مبلغ لم تصرفه من الحد السابق لهذا الشهر", style = Eyebrow.copy(fontSize = 11.sp))
                                }
                                Switch(
                                    checked = catRollover[cat] ?: false,
                                    onCheckedChange = { v -> catRollover = catRollover + (cat to v) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = catInputs[cat] ?: "",
                                    onValueChange = { v ->
                                        catInputs = catInputs + (cat to sanitizeAmountInput(v))
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
                                            val amt = catInputs[cat]?.toDoubleOrNull() ?: 0.0
                                            val roll = catRollover[cat] ?: false
                                            vm.setBudget(monthKey, cat, amt, roll)
                                            editingCategory = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Check, "حفظ", tint = White, modifier = Modifier.size(18.dp)) }
                                if (cat != "أخرى") {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(RadiusSm)).background(Danger.copy(alpha = 0.1f))
                                            .clickable {
                                                vm.deleteCategory(cat)
                                                editingCategory = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Delete, "حذف $cat", tint = Danger, modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                    if (index < categories.lastIndex) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                        }
                    }
                }
            }
        }
    }
}
