package com.mizan.money.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ============ ADVANCED FILTER STATE ============
// Pure in-memory model — every field is optional, an "empty" filter means
// "show everything". Held in MainViewModel (not persisted to disk) so it
// survives tab switches but not an app restart.
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(vm: MainViewModel, onAddTransaction: () -> Unit = {}) {
    val txs by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val recurringItems by vm.recurringItems.collectAsState()
    val rates by vm.exchangeRates.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    // Hoisted into the ViewModel so search/filters survive tab switches.
    val query by vm.txQuery.collectAsState()
    val filter by vm.txFilter.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TransactionEntity?>(null) }
    val ctx = LocalContext.current

    // Multi-select (long-press a row to start; tap toggles once active).
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var showBulkRecategorize by remember { mutableStateOf(false) }
    fun clearSelection() { selectionMode = false; selectedIds = emptySet() }
    fun toggleSelected(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    // The DB list (vm.transactions) has no paging — after a year or two of SMS
    // history it can grow into the thousands, and re-filtering/re-grouping all
    // of it on every keystroke would make the search box feel laggy. The list
    // itself stays fully loaded (other screens rely on the complete history for
    // their totals), but the query text is debounced here so a burst of typing
    // triggers this heavier recomputation once, not once per character.
    var debouncedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query.isEmpty()) { debouncedQuery = query; return@LaunchedEffect }
        delay(180)
        debouncedQuery = query
    }

    val filtered = remember(txs, debouncedQuery, filter, categories) {
        val q = normalizeSearchDigits(debouncedQuery.trim())
        val qNum = q.replace(",", "").replace("٬", "").replace("٫", ".")
        txs.filter { tx ->
            if (!filter.matches(tx)) return@filter false
            if (q.isEmpty()) return@filter true
            (tx.merchant ?: "").contains(q, ignoreCase = true) ||
                tx.category.contains(q, ignoreCase = true) ||
                categoryDisplayName(ctx, tx.category).contains(q, ignoreCase = true) ||
                (tx.bankName ?: "").contains(q, ignoreCase = true) ||
                (qNum.isNotEmpty() && qNum.any { it.isDigit() } && (
                    FinancialAdvisor.fmt(tx.amount).replace(",", "").contains(qNum) ||
                        FinancialAdvisor.fmt(tx.amount).contains(q)
                    ))
        }
    }
    val totals = remember(filtered, rates) {
        FinancialAdvisor.summarize(filtered, 0L, Long.MAX_VALUE, rates)
    }

    val grouped = remember(filtered, rates) {
        filtered.groupBy { tx ->
            val c = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
        }.toList().map { (day, dayTxs) ->
            Triple(day, dayTxs, FinancialAdvisor.summarize(dayTxs, 0L, Long.MAX_VALUE, rates).spent)
        }
    }

    val availableBanks = remember(txs) {
        txs.mapNotNull { it.bankName?.trim()?.takeIf { b -> b.isNotEmpty() } }.toSet().sorted()
    }

    Column(Modifier.fillMaxSize()) {
        // Search bar is pinned above the list (outside the LazyColumn); it is
        // replaced by a contextual selection bar while selectionMode is active.
        if (selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                onCancel = { clearSelection() },
                onRecategorize = { showBulkRecategorize = true },
                onDelete = { confirmBulkDelete = true },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
            )
        } else Row(
            Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(Pill))
                .background(White)
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = InkFaint)
            Spacer(Modifier.width(10.dp))
            TextField(
                value = query,
                onValueChange = { vm.setTxQuery(it) },
                placeholder = { Text(stringResource(R.string.tx_search_hint), style = BodyMuted, maxLines = 1) },
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
                IconButton(onClick = { vm.setTxQuery("") }, modifier = Modifier.size(36.dp)) {
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
                            .size(18.dp).clip(RoundedCornerShape(Pill)).background(Indigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            filter.activeCount.toString(),
                            color = Lime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        // Active filters as removable pills, plus "clear all".
        if (!filter.isEmpty) {
            ActiveFilterChips(
                filter = filter,
                onChange = { vm.setTxFilter(it) },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        ) {
            if (filtered.isEmpty()) {
                item {
                    val noTxs = txs.isEmpty()
                    val filteredOut = !filter.isEmpty
                    TxEmptyState(
                        text = if (noTxs) stringResource(R.string.tx_empty_ever)
                            else if (filteredOut) stringResource(R.string.tx_filter_no_results)
                            else stringResource(R.string.tx_empty_search),
                        ctaLabel = when {
                            noTxs -> stringResource(R.string.home_add_transaction)
                            filteredOut -> stringResource(R.string.tx_filter_clear_all)
                            else -> null
                        },
                        onCta = { if (noTxs) onAddTransaction() else vm.setTxFilter(TxFilter()) }
                    )
                }
            } else {
                item(key = "summary") {
                    TxSummaryCard(totals.spent, totals.income, filtered.size, Modifier.animateItem())
                }
                // One UI list: rows grouped per day under a small date header.
                grouped.forEach { (day, dayTxs, daySpent) ->
                    item(key = "day-$day") {
                        Row(
                            Modifier.animateItem().fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dayHeader(dayTxs.first().timestamp),
                                style = Body.copy(color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f)
                            )
                            if (daySpent > 0.005) {
                                Text(
                                    "-" + fmt(daySpent),
                                    style = Body.copy(color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                    itemsIndexed(dayTxs, key = { _, tx -> tx.id }) { i, tx ->
                        TransactionCard(
                            tx,
                            modifier = Modifier.animateItem(),
                            position = rowPos(i, dayTxs.size),
                            showDate = false,
                            selectionMode = selectionMode,
                            selected = tx.id in selectedIds,
                            onClick = { if (selectionMode) toggleSelected(tx.id) else selected = tx },
                            onLongClick = { if (!selectionMode) selectionMode = true; toggleSelected(tx.id) }
                        )
                    }
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
            startDay = startDay,
            onDismiss = { showFilterSheet = false },
            onApply = { newFilter -> vm.setTxFilter(newFilter); showFilterSheet = false },
        )
    }
    if (confirmBulkDelete) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text(stringResource(R.string.tx_bulk_delete_confirm_title_fmt, count), style = H2) },
            text = { Text(stringResource(R.string.tx_detail_confirm_delete_desc), style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMany(selectedIds)
                    confirmBulkDelete = false
                    clearSelection()
                }) {
                    Text(stringResource(R.string.tx_detail_confirm_delete_yes), style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }) {
                    Text(stringResource(R.string.tx_detail_cancel), style = Body.copy(color = InkSoft))
                }
            }
        )
    }
    if (showBulkRecategorize) {
        BulkRecategorizeSheet(
            count = selectedIds.size,
            categories = categories,
            onDismiss = { showBulkRecategorize = false },
            onConfirm = { category ->
                vm.recategorizeMany(selectedIds, category)
                showBulkRecategorize = false
                clearSelection()
            }
        )
    }
}

// Contextual bar replacing the search box while rows are selected: cancel,
// selected count, recategorize and delete — the same actions TxDetailDialog
// offers per-row, applied to every selected transaction at once.
@Composable
private fun SelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onRecategorize: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Pill))
            .background(White)
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.tx_selection_cancel_desc), Modifier.size(18.dp), tint = InkSoft)
        }
        Text(
            stringResource(R.string.tx_selection_count_fmt, count),
            style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        IconButton(onClick = onRecategorize, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Label, stringResource(R.string.tx_selection_recategorize_desc), Modifier.size(18.dp), tint = Indigo)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.DeleteOutline, stringResource(R.string.tx_selection_delete_desc), Modifier.size(18.dp), tint = Danger)
        }
    }
}

// Bottom sheet with a chip grid of categories, reusing PlanningChip so it
// matches the rest of the app's category pickers.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkRecategorizeSheet(
    count: Int,
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var category by remember { mutableStateOf(categories.firstOrNull() ?: "أخرى") }
    FormSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
        title = { Text(stringResource(R.string.tx_bulk_recategorize_title_fmt, count), style = H2) },
        text = {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { c ->
                    PlanningChip(label = categoryDisplay(c), selected = category == c) { category = c }
                }
            }
        },
        confirmButton = { PlanningSheetConfirm(stringResource(R.string.reminders_save)) { onConfirm(category) } },
        dismissButton = { PlanningSheetCancel(onDismiss) }
    )
}

// Search accepts Arabic-Indic / Persian digits by mapping them to ASCII, the
// same digit mapping sanitizeAmountInput uses (letters are kept here).
private fun normalizeSearchDigits(s: String): String {
    val ar = "٠١٢٣٤٥٦٧٨٩"
    val fa = "۰۱۲۳۴۵۶۷۸۹"
    return s.map { c ->
        val i = ar.indexOf(c).takeIf { it >= 0 } ?: fa.indexOf(c)
        if (i >= 0) '0' + i else c
    }.joinToString("")
}

@Composable
private fun TxSummaryCard(spent: Double, income: Double, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryStat(stringResource(R.string.home_tx_summary_spent), fmt(spent), Ink, Modifier.weight(1f))
        SummaryDivider()
        SummaryStat(stringResource(R.string.home_tx_summary_income), fmt(income), Success, Modifier.weight(1f))
        SummaryDivider()
        SummaryStat(
            stringResource(R.string.home_tx_summary_count),
            if (isArabicUi()) toArabicIndicDigits(count.toString()) else count.toString(),
            Ink,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(32.dp).background(Line))
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Eyebrow.copy(color = InkSoft), maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, style = NumBold.copy(color = color, fontSize = 15.sp), maxLines = 1)
    }
}

@Composable
private fun TxEmptyState(text: String, ctaLabel: String?, onCta: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(text)
        if (ctaLabel != null) {
            Button(
                onClick = onCta,
                shape = RoundedCornerShape(Pill),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Lime),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(ctaLabel, style = Body.copy(color = Lime, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterChips(filter: TxFilter, onChange: (TxFilter) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalContext.current.resources.configuration.locales[0]
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        filter.categories.forEach { c ->
            RemovablePill(categoryDisplay(c)) { onChange(filter.copy(categories = filter.categories - c)) }
        }
        filter.type?.let { t ->
            RemovablePill(
                stringResource(if (t == TxType.EXPENSE) R.string.tx_type_expense else R.string.tx_type_income)
            ) { onChange(filter.copy(type = null)) }
        }
        if (filter.dateFrom != null || filter.dateTo != null) {
            val df = remember(locale) { SimpleDateFormat("d MMM", locale) }
            val from = filter.dateFrom?.let { df.format(java.util.Date(it)) } ?: "…"
            val to = filter.dateTo?.let { df.format(java.util.Date(it)) } ?: "…"
            RemovablePill("$from – $to") { onChange(filter.copy(dateFrom = null, dateTo = null)) }
        }
        if (filter.amountMin != null || filter.amountMax != null) {
            val min = filter.amountMin?.let { fmt(it) } ?: "…"
            val max = filter.amountMax?.let { fmt(it) } ?: "…"
            RemovablePill("$min – $max") { onChange(filter.copy(amountMin = null, amountMax = null)) }
        }
        filter.banks.forEach { b ->
            RemovablePill(b) { onChange(filter.copy(banks = filter.banks - b)) }
        }
        TextButton(
            onClick = { onChange(TxFilter()) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                stringResource(R.string.tx_filter_clear_all),
                style = Body.copy(color = Indigo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
private fun RemovablePill(label: String, onRemove: () -> Unit) {
    Row(
        Modifier.height(34.dp)
            .clip(RoundedCornerShape(Pill))
            .background(IndigoSoft)
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = Body.copy(color = Indigo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.Close, stringResource(R.string.home_filter_remove), Modifier.size(16.dp), tint = Indigo)
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
