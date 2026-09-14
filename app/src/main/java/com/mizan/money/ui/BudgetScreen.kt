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
    val monthKey = Dates.monthKey(offset)
    val txs by vm.transactions.collectAsState()
    val range = remember(offset) { Dates.monthRange(offset) }
    val summary = remember(txs, offset) { FinancialAdvisor.summarize(txs, range.first, range.last) }

    var totalInput by remember(monthKey) {
        mutableStateOf(
            budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }
                ?.limitAmount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        )
    }
    var catInputs by remember(monthKey) { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(budgets, monthKey) {
        catInputs = CategoryClassifier.categories.associateWith { c ->
            budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                ?.limitAmount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        }
    }

    val prevMonthKey = Dates.monthKey(offset - 1)
    val hasPrevBudget = budgets.any { it.monthKey == prevMonthKey }
    val hasCurrentBudget = budgets.any { it.monthKey == monthKey }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("الميزانية والتصنيفات — ${monthName(offset)}", style = H1)
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
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الميزانية الإجمالية للشهر", style = Eyebrow)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(totalInput.ifBlank { "0" }, style = H1.copy(fontSize = 26.sp))
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
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) } },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(RadiusSm),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink900, contentColor = Lime)
                ) { Text("حفظ", style = Body.copy(color = Lime, fontWeight = FontWeight.Bold)) }
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
                    IconBadge(catIcon(cat), catColor(cat), catColorSoft(cat), size = 40.dp, iconSize = 18.dp)
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
                        .clip(RoundedCornerShape(Pill))
                        .background(PaperOuter)
                ) {
                    Box(
                        Modifier.fillMaxWidth(pct).fillMaxHeight()
                            .clip(RoundedCornerShape(Pill))
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
                                (catInputs[cat]?.toDoubleOrNull() ?: 0.0).let { vm.setBudget(monthKey, cat, it) }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Check, "حفظ", tint = White, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}
