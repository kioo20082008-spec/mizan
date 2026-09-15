package com.mizan.money.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.TOTAL_BUDGET
import com.mizan.money.sms.CategoryClassifier

// ============ BUDGET ============
@Composable
fun BudgetScreen(vm: MainViewModel, offset: Int) {
    val budgets by vm.budgets.collectAsState()
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
    // Seeded synchronously from the already-loaded `budgets` (not emptyMap()), so
    // switching months doesn't flash every category to "0" for a frame before the
    // effect below catches up.
    var catInputs by remember(monthKey) {
        mutableStateOf(
            CategoryClassifier.categories.associateWith { c ->
                budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                    ?.limitAmount?.toBudgetInput() ?: ""
            }
        )
    }
    // Only one category's editor is open at a time, so the list stays scannable
    // instead of showing 13 always-open input rows. Keyed by month so switching
    // months doesn't leave a stale category's editor expanded.
    var editingCategory by remember(monthKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(budgets, monthKey) {
        // Previously only catInputs was resynced here, so saving the total budget
        // (or copying last month's) never refreshed the hero card's own number —
        // it stayed blank/stale until the user left and re-entered the screen.
        totalInput = budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }
            ?.limitAmount?.toBudgetInput() ?: ""
        catInputs = CategoryClassifier.categories.associateWith { c ->
            // Skip the category currently being typed into — otherwise an unrelated
            // budget write elsewhere (e.g. saving the total) re-fires this effect
            // and clobbers the in-progress, not-yet-saved keystrokes with what's
            // still in the database.
            if (c == editingCategory) catInputs[c] ?: ""
            else budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                ?.limitAmount?.toBudgetInput() ?: ""
        }
    }

    val spentByCat = remember(summary) { summary.categoryTotals.associate { it.category to it.amount } }
    val prevMonthKey = Dates.monthKey(offset - 1, startDay)
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
                                .forEach { vm.setBudget(monthKey, it.category, it.limitAmount) }
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
                        onValueChange = { totalInput = sanitizeAmountInput(it) },
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
                        onClick = { totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) } },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(RadiusSm),
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink900)
                    ) { Text("حفظ", style = Body.copy(color = Ink900, fontWeight = FontWeight.Bold)) }
                }
            }
        }

        item {
            Text("الميزانية لكل تصنيف", style = H2, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }

        items(CategoryClassifier.categories) { cat ->
            val spentInCat = spentByCat[cat] ?: 0.0
            val limit = catInputs[cat]?.toDoubleOrNull() ?: 0.0
            val pct = if (limit > 0) (spentInCat / limit).coerceIn(0.0, 1.0).toFloat() else 0f
            val isOver = limit > 0 && spentInCat > limit
            val isEditing = editingCategory == cat

            SoftCard(Modifier.clickable { editingCategory = if (isEditing) null else cat }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(catIcon(cat), catColor(cat), catColorSoft(cat), size = 40.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat, style = H2.copy(fontSize = 14.sp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (limit > 0) "${FinancialAdvisor.fmt(spentInCat)} / ${FinancialAdvisor.fmt(limit)} ر.س"
                            else "${FinancialAdvisor.fmt(spentInCat)} ر.س — بدون حد",
                            style = Eyebrow.copy(fontSize = 11.sp, color = if (isOver) Danger else InkFaint, fontWeight = if (isOver) FontWeight.Bold else FontWeight.Normal)
                        )
                    }
                    Icon(
                        if (isEditing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = InkFaint
                    )
                }
                if (limit > 0) {
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
                }
                if (isEditing) {
                    Spacer(Modifier.height(14.dp))
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
                            Modifier.size(48.dp).clip(RoundedCornerShape(RadiusSm)).background(Indigo)
                                .clickable {
                                    (catInputs[cat]?.toDoubleOrNull() ?: 0.0).let { vm.setBudget(monthKey, cat, it) }
                                    editingCategory = null
                                },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Check, "حفظ", tint = White, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}
