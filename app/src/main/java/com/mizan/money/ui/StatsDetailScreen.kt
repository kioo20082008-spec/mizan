package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.TxType
import java.util.Calendar

sealed class DetailFilter {
    abstract val label: String
    data class ByMerchant(override val label: String) : DetailFilter()
    data class ByCategory(override val label: String) : DetailFilter()
}

// "كم صرفت على X؟" — a merchant or category's full history across every month
// on record (not scoped to the currently-browsed month), reached from a
// transaction's own detail view or a dashboard category chip.
@Composable
fun StatsDetailScreen(vm: MainViewModel, filter: DetailFilter, onBack: () -> Unit) {
    val allTxs by vm.transactions.collectAsState()

    val filtered = remember(filter, allTxs) {
        allTxs.filter {
            !it.isSelfTransfer && when (filter) {
                is DetailFilter.ByMerchant -> it.merchant == filter.label
                is DetailFilter.ByCategory -> it.category == filter.label
            }
        }
    }
    val expenses = remember(filtered) { filtered.filter { it.type == TxType.EXPENSE } }
    val totalSpent = remember(expenses) { expenses.sumOf { it.amount } }
    val totalIncome = remember(filtered) { filtered.filter { it.type == TxType.INCOME }.sumOf { it.amount } }
    val count = expenses.size
    val avg = if (count > 0) totalSpent / count else 0.0
    val largest = expenses.maxByOrNull { it.amount }
    val last = filtered.maxByOrNull { it.timestamp }

    // Monthly trend: sum per calendar month, oldest to newest.
    val monthly: Map<String, Double> = remember(expenses) {
        expenses.groupBy {
            val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        }.mapValues { (_, list) -> list.sumOf { it.amount } }.toSortedMap()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Ink)
                }
                Column(Modifier.weight(1f)) {
                    Text(filter.label, style = H1, maxLines = 1)
                    Text(if (filter is DetailFilter.ByMerchant) "تاجر" else "تصنيف", style = Eyebrow)
                }
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusXl))
                    .background(Ink900)
                    .padding(24.dp)
            ) {
                Text("إجمالي ما صرفت", style = Eyebrow.copy(color = OnInkSoft))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(FinancialAdvisor.fmt(totalSpent), style = Display.copy(fontSize = 36.sp))
                    Spacer(Modifier.width(6.dp))
                    Text("ر.س", style = Body.copy(color = OnInkSoft, fontWeight = FontWeight.Medium))
                }
                if (totalIncome > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("الدخل: ${FinancialAdvisor.fmt(totalIncome)} ر.س", style = Eyebrow.copy(color = Lime))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("عدد العمليات", count.toString(), Modifier.weight(1f))
                StatTile("المتوسط", FinancialAdvisor.fmt(avg), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("أكبر عملية", largest?.let { FinancialAdvisor.fmt(it.amount) } ?: "—", Modifier.weight(1f))
                StatTile("آخر عملية", last?.let { Dates.dayLabel(it.timestamp) } ?: "—", Modifier.weight(1f))
            }
        }

        if (monthly.size > 1) {
            item { Text("الاتجاه الشهري", style = H2, modifier = Modifier.padding(top = 8.dp)) }
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusLg))
                        .background(White)
                        .border(1.dp, Line, RoundedCornerShape(RadiusLg))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val maxAmount = monthly.values.max()
                    monthly.entries.toList().takeLast(6).forEach { (month, amount) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(monthLabelShort(month), style = Eyebrow.copy(fontSize = 11.sp), modifier = Modifier.width(56.dp))
                            Box(
                                Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(Pill)).background(PaperOuter)
                            ) {
                                Box(
                                    Modifier.fillMaxWidth((amount / maxAmount).toFloat()).fillMaxHeight()
                                        .clip(RoundedCornerShape(Pill)).background(Indigo)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(FinancialAdvisor.fmt(amount), style = NumBold.copy(fontSize = 11.sp), modifier = Modifier.width(70.dp))
                        }
                    }
                }
            }
        }

        item { Text("كل العمليات (${filtered.size})", style = H2, modifier = Modifier.padding(top = 8.dp)) }
        if (filtered.isEmpty()) {
            item { EmptyState("لا توجد عمليات") }
        } else {
            items(filtered.sortedByDescending { it.timestamp }, key = { it.id }) { tx ->
                TransactionCard(tx) { }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, mod: Modifier = Modifier) {
    Column(
        mod.clip(RoundedCornerShape(RadiusMd))
            .background(White)
            .border(1.dp, Line, RoundedCornerShape(RadiusMd))
            .padding(14.dp)
    ) {
        Text(label, style = Eyebrow.copy(fontSize = 11.sp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = H2.copy(fontSize = 17.sp))
    }
}

private fun monthLabelShort(ym: String): String {
    val parts = ym.split("-")
    if (parts.size != 2) return ym
    val names = listOf("ينا", "فبر", "مار", "أبر", "ماي", "يون", "يول", "أغس", "سبت", "أكت", "نوف", "ديس")
    val m = parts[1].toIntOrNull()?.minus(1) ?: return ym
    return "${names.getOrNull(m) ?: "?"} ${parts[0].takeLast(2)}"
}
