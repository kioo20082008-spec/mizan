package com.mizan.money.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.RecurringItemEntity
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ============ ADVANCED FILTER STATE ============
// Pure in-memory model — every field is optional, an "empty" filter means
// "show everything". Lives only while the screen is composed (no persistence):
// the whole point is a quick, one-off narrowing of the list.
data class TxFilter(
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val amountMin: Double? = null,
    val amountMax: Double? = null,
    val categories: Set<String> = emptySet(),
    val type: TxType? = null,
    val banks: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = dateFrom == null && dateTo == null &&
            amountMin == null && amountMax == null &&
            categories.isEmpty() && type == null && banks.isEmpty()

    val activeCount: Int
        get() = listOf(
            dateFrom != null || dateTo != null,
            amountMin != null || amountMax != null,
            categories.isNotEmpty(),
            type != null,
            banks.isNotEmpty(),
        ).count { it }

    fun matches(tx: TransactionEntity): Boolean {
        if (dateFrom != null && tx.timestamp < dateFrom) return false
        if (dateTo != null && tx.timestamp > dateTo) return false
        if (amountMin != null && tx.amount < amountMin) return false
        if (amountMax != null && tx.amount > amountMax) return false
        if (categories.isNotEmpty() && tx.category !in categories) return false
        if (type != null && tx.type != type) return false
        if (banks.isNotEmpty() && (tx.bankName ?: "") !in banks) return false
        return true
    }
}

// ============ TRANSACTIONS ============
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: MainViewModel, initialCategory: String? = null) {
    val txs by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val recurringItems by vm.recurringItems.collectAsState()
    var query by remember { mutableStateOf("") }
    // A category shortcut (Home/Planning) opens the list pre-filtered through the
    // regular filter, so it shows up as a removable "active filter" chip instead
    // of a raw category key typed into the search box.
    var filter by remember(initialCategory) {
        mutableStateOf(TxFilter(categories = setOfNotNull(initialCategory)))
    }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TransactionEntity?>(null) }

    val filtered = remember(txs, query, filter) {
        txs.filter { tx ->
            if (!filter.matches(tx)) return@filter false
            if (query.isBlank()) return@filter true
            (tx.merchant ?: "").contains(query, ignoreCase = true) ||
                tx.category.contains(query, ignoreCase = true) ||
                FinancialAdvisor.fmt(tx.amount).contains(query)
        }
    }

    val grouped = remember(filtered) {
        filtered.groupBy { tx ->
            val c = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
        }.toList()
    }

    val availableBanks = remember(txs) {
        txs.mapNotNull { it.bankName?.trim()?.takeIf { b -> b.isNotEmpty() } }.toSet().sorted()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(Pill))
                    .background(White)
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = InkFaint)
                Spacer(Modifier.width(10.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.tx_search_hint), style = BodyMuted) },
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
                        Icon(Icons.Default.Close, stringResource(R.string.tx_clear_search), Modifier.size(16.dp), tint = InkFaint)
                    }
                }
                // Filter icon + active-count badge
                Box(modifier = Modifier.size(44.dp)) {
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            stringResource(R.string.tx_filter_icon),
                            tint = if (filter.isEmpty) InkSoft else Indigo
                        )
                    }
                    if (!filter.isEmpty) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)
                                .size(18.dp).clip(RoundedCornerShape(Pill)).background(Danger),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                filter.activeCount.toString(),
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        // Active-filter summary row
        if (!filter.isEmpty) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(Pill))
                        .background(IndigoSoft)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FilterAlt, null, Modifier.size(14.dp), tint = Indigo)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.tx_filter_active_fmt, filter.activeCount),
                        style = Body.copy(color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.tx_filter_clear_all),
                        style = Body.copy(color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.clickable { filter = TxFilter() }.padding(6.dp)
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    if (txs.isEmpty()) stringResource(R.string.tx_empty_ever)
                    else if (!filter.isEmpty) stringResource(R.string.tx_filter_no_results)
                    else stringResource(R.string.tx_empty_search)
                )
            }
        } else {
            // One UI list: rows grouped per day under a small date header.
            grouped.forEach { (day, dayTxs) ->
                item(key = "day-$day") {
                    Text(
                        dayHeader(dayTxs.first().timestamp),
                        style = Body.copy(color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.animateItem().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)
                    )
                }
                itemsIndexed(dayTxs, key = { _, tx -> tx.id }) { i, tx ->
                    TransactionCard(
                        tx,
                        modifier = Modifier.animateItem(),
                        position = rowPos(i, dayTxs.size),
                        showDate = false,
                        onClick = { selected = tx }
                    )
                }
            }
        }
    }

    selected?.let { current ->
        TxDetailDialog(
            tx = current,
            categories = categories,
            recurringItems = recurringItems,
            onDismiss = { selected = null },
            onDelete = { vm.delete(current); selected = null },
            onSave = { updated, billReminder -> vm.update(updated); vm.setBillReminder(updated, billReminder); selected = null }
        )
    }
    if (showFilterSheet) {
        AdvancedFilterSheet(
            initial = filter,
            categories = categories,
            availableBanks = availableBanks,
            onDismiss = { showFilterSheet = false },
            onApply = { newFilter -> filter = newFilter; showFilterSheet = false },
        )
    }
}

// "Today" / "Yesterday" / "24 September" (month name localized, Latin digits
// to match the rest of the app's numbers).
@Composable
private fun dayHeader(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun Calendar.sameDay(o: Calendar) =
        get(Calendar.YEAR) == o.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == o.get(Calendar.DAY_OF_YEAR)
    val locale = LocalContext.current.resources.configuration.locales[0]
    return when {
        c.sameDay(today) -> stringResource(R.string.dash_today)
        c.sameDay(yesterday) -> stringResource(R.string.day_yesterday)
        else -> {
            val month = SimpleDateFormat("MMMM", locale).format(c.time)
            val base = "${c.get(Calendar.DAY_OF_MONTH)} $month"
            if (c.get(Calendar.YEAR) == today.get(Calendar.YEAR)) base else "$base ${c.get(Calendar.YEAR)}"
        }
    }
}
