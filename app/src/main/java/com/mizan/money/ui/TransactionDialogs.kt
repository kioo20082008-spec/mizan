package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.data.RecurringItemEntity
import com.mizan.money.data.SELF_TRANSFER_CATEGORY
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

// Same fallback category the rest of the app classifies into (see
// CategoryClassifier / TransactionEntity's default).
private const val OTHER_CATEGORY = "أخرى"

// ---- Date helpers ----------------------------------------------------------
// Material3 date pickers work in UTC-midnight millis; the app stores local
// timestamps. These convert a calendar *date* between the two.
private fun localToUtcDate(ts: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = ts }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun utcDateToLocalStart(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun utcDateToLocalEnd(utc: Long): Long {
    val start = Calendar.getInstance().apply { timeInMillis = utcDateToLocalStart(utc) }
    start.add(Calendar.DAY_OF_MONTH, 1)
    return start.timeInMillis - 1
}

private fun startOfLocalDay(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun formatDay(ts: Long, pattern: String = "d MMM yyyy"): String {
    val locale = LocalContext.current.resources.configuration.locales[0]
    return remember(ts, locale, pattern) { SimpleDateFormat(pattern, locale).format(java.util.Date(ts)) }
}

// ============ ADVANCED FILTER SHEET ============
private enum class DatePreset { THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, ALL, CUSTOM }

private fun presetRange(p: DatePreset, startDay: Int): Pair<Long?, Long?> = when (p) {
    DatePreset.THIS_MONTH -> Dates.monthRange(0, startDay).let { it.first to it.last }
    DatePreset.LAST_MONTH -> Dates.monthRange(-1, startDay).let { it.first to it.last }
    DatePreset.LAST_3_MONTHS -> Dates.monthRange(-2, startDay).first to Dates.monthRange(0, startDay).last
    DatePreset.ALL, DatePreset.CUSTOM -> null to null
}

private fun detectPreset(from: Long?, to: Long?, startDay: Int): DatePreset {
    if (from == null && to == null) return DatePreset.ALL
    return listOf(DatePreset.THIS_MONTH, DatePreset.LAST_MONTH, DatePreset.LAST_3_MONTHS)
        .firstOrNull { presetRange(it, startDay) == (from to to) } ?: DatePreset.CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AdvancedFilterSheet(
    initial: TxFilter,
    categories: List<String>,
    availableBanks: List<String>,
    startDay: Int = 1,
    onDismiss: () -> Unit,
    onApply: (TxFilter) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var dateFrom by remember { mutableStateOf(initial.dateFrom) }
    var dateTo by remember { mutableStateOf(initial.dateTo) }
    var preset by remember { mutableStateOf(detectPreset(initial.dateFrom, initial.dateTo, startDay)) }
    var showRangePicker by remember { mutableStateOf(false) }
    var amountMinInput by remember { mutableStateOf(initial.amountMin?.let { stripTrailingZero(it) } ?: "") }
    var amountMaxInput by remember { mutableStateOf(initial.amountMax?.let { stripTrailingZero(it) } ?: "") }
    var selectedCats by remember { mutableStateOf(initial.categories) }
    var selectedType by remember { mutableStateOf(initial.type) }
    var selectedBanks by remember { mutableStateOf(initial.banks) }

    fun pickPreset(p: DatePreset) {
        preset = p
        val (f, t) = presetRange(p, startDay)
        dateFrom = f; dateTo = t
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White,
        shape = RoundedCornerShape(topStart = RadiusXl, topEnd = RadiusXl),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.Tune, Indigo, IndigoSoft, size = 40.dp, iconSize = 18.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.tx_filter_title), style = H2, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    pickPreset(DatePreset.ALL)
                    amountMinInput = ""; amountMaxInput = ""
                    selectedCats = emptySet(); selectedType = null; selectedBanks = emptySet()
                }) {
                    Text(
                        stringResource(R.string.tx_filter_clear_all),
                        style = Body.copy(color = Indigo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // ===== DATE =====
                SectionLabel(stringResource(R.string.tx_filter_section_date))
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PillChip(stringResource(R.string.home_filter_this_month), preset == DatePreset.THIS_MONTH) { pickPreset(DatePreset.THIS_MONTH) }
                    PillChip(stringResource(R.string.home_filter_last_month), preset == DatePreset.LAST_MONTH) { pickPreset(DatePreset.LAST_MONTH) }
                    PillChip(stringResource(R.string.home_filter_last_3_months), preset == DatePreset.LAST_3_MONTHS) { pickPreset(DatePreset.LAST_3_MONTHS) }
                    PillChip(stringResource(R.string.home_filter_all_time), preset == DatePreset.ALL) { pickPreset(DatePreset.ALL) }
                    PillChip(
                        stringResource(R.string.home_filter_custom),
                        preset == DatePreset.CUSTOM,
                        icon = Icons.Default.DateRange
                    ) { showRangePicker = true }
                }
                val from = dateFrom
                val to = dateTo
                if (preset == DatePreset.CUSTOM && (from != null || to != null)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (from?.let { formatDay(it) } ?: "…") + " – " + (to?.let { formatDay(it) } ?: "…"),
                        style = Body.copy(color = Indigo, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))

                // ===== AMOUNT =====
                SectionLabel(stringResource(R.string.tx_filter_section_amount))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountMinInput,
                        onValueChange = { amountMinInput = sanitizeAmountInput(it) },
                        placeholder = { Text(stringResource(R.string.tx_filter_min_hint), style = Eyebrow.copy(fontSize = 12.sp)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body.copy(fontSize = 13.sp)
                    )
                    OutlinedTextField(
                        value = amountMaxInput,
                        onValueChange = { amountMaxInput = sanitizeAmountInput(it) },
                        placeholder = { Text(stringResource(R.string.tx_filter_max_hint), style = Eyebrow.copy(fontSize = 12.sp)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body.copy(fontSize = 13.sp)
                    )
                }
                Spacer(Modifier.height(20.dp))

                // ===== TYPE =====
                SectionLabel(stringResource(R.string.tx_filter_section_type))
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PillChip(stringResource(R.string.tx_filter_type_all), selectedType == null) { selectedType = null }
                    PillChip(stringResource(R.string.tx_type_expense), selectedType == TxType.EXPENSE) { selectedType = TxType.EXPENSE }
                    PillChip(stringResource(R.string.tx_type_income), selectedType == TxType.INCOME) { selectedType = TxType.INCOME }
                }
                Spacer(Modifier.height(20.dp))

                // ===== CATEGORIES =====
                SectionLabel(stringResource(R.string.tx_filter_section_categories))
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { c ->
                        PillChip(categoryDisplay(c), c in selectedCats) {
                            selectedCats = if (c in selectedCats) selectedCats - c else selectedCats + c
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // ===== BANKS =====
                SectionLabel(stringResource(R.string.tx_filter_section_banks))
                Spacer(Modifier.height(8.dp))
                if (availableBanks.isEmpty()) {
                    Text(stringResource(R.string.tx_filter_banks_empty), style = Eyebrow)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableBanks.forEach { b ->
                            PillChip(b, b in selectedBanks) {
                                selectedBanks = if (b in selectedBanks) selectedBanks - b else selectedBanks + b
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onApply(
                        TxFilter(
                            dateFrom = dateFrom,
                            dateTo = dateTo,
                            amountMin = amountMinInput.toDoubleOrNull(),
                            amountMax = amountMaxInput.toDoubleOrNull(),
                            categories = selectedCats,
                            type = selectedType,
                            banks = selectedBanks,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(Pill),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Lime)
            ) {
                Text(
                    stringResource(R.string.tx_filter_apply),
                    style = Body.copy(color = Lime, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                )
            }
        }
    }

    if (showRangePicker) {
        DateRangePickerDialog(
            initialFrom = dateFrom,
            initialTo = dateTo,
            onDismiss = { showRangePicker = false },
            onConfirm = { f, t ->
                dateFrom = f; dateTo = t
                preset = DatePreset.CUSTOM
                showRangePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    initialFrom: Long?,
    initialTo: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.let { localToUtcDate(it) },
        initialSelectedEndDateMillis = initialTo?.let { localToUtcDate(it) }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis
                    if (start != null) {
                        val end = state.selectedEndDateMillis ?: start
                        onConfirm(utcDateToLocalStart(start), utcDateToLocalEnd(end))
                    }
                },
                enabled = state.selectedStartDateMillis != null
            ) {
                Text(stringResource(R.string.home_ok), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tx_detail_cancel), style = Body.copy(color = InkSoft))
            }
        },
        colors = DatePickerDefaults.colors(containerColor = White)
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            showModeToggle = false,
            colors = DatePickerDefaults.colors(containerColor = White)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val todayUtc = remember { localToUtcDate(System.currentTimeMillis()) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = localToUtcDate(initial),
        selectableDates = object : SelectableDates {
            // No future-dated manual entries.
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis
                    if (picked != null) onConfirm(utcDateToLocalStart(picked))
                },
                enabled = state.selectedDateMillis != null
            ) {
                Text(stringResource(R.string.home_ok), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tx_detail_cancel), style = Body.copy(color = InkSoft))
            }
        },
        colors = DatePickerDefaults.colors(containerColor = White)
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(containerColor = White)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = Eyebrow.copy(color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp))
}

// One UI selection pill: filled accent when selected, soft grey otherwise,
// no borders.
@Composable
private fun PillChip(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(Pill))
            .background(if (selected) Indigo else PaperOuter)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (selected) Lime else InkSoft)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = Body.copy(
                fontSize = 13.sp,
                color = if (selected) Lime else Ink,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            maxLines = 1
        )
    }
}

// Shared by the add and edit sheets: category pills with their icon badge.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
    // Keep a category the transaction already uses selectable even if it was
    // since removed from the user's list.
    val items = if (selected in categories || selected == SELF_TRANSFER_CATEGORY) categories
        else listOf(selected) + categories
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { c ->
            val isSel = c == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(Pill))
                    .background(if (isSel) Indigo else PaperOuter)
                    .clickable { onSelect(c) }
                    .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(
                    catIcon(c),
                    if (isSel) Lime else catColor(c),
                    if (isSel) Lime.copy(alpha = 0.2f) else catColorSoft(c),
                    size = 26.dp,
                    iconSize = 14.dp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    categoryDisplay(c),
                    style = Body.copy(
                        fontSize = 13.sp,
                        color = if (isSel) Lime else Ink,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

// Full-width pill buttons used in the sheets' footers.
@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(Pill),
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Indigo else PaperOuter,
            contentColor = if (primary) Lime else Ink,
            disabledContainerColor = if (primary) Indigo.copy(alpha = 0.35f) else PaperOuter,
            disabledContentColor = if (primary) Lime.copy(alpha = 0.8f) else InkFaint
        )
    ) {
        Text(label, style = Body.copy(color = Color.Unspecified, fontWeight = FontWeight.Bold, fontSize = 15.sp))
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusMd))
            .background(PaperOuter)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Body.copy(fontWeight = FontWeight.Medium))
            Text(desc, style = Eyebrow.copy(fontSize = 12.sp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = oneUiSwitchColors()
        )
    }
}

// ============ TRANSACTION DETAIL DIALOG ============
@Composable
internal fun TxDetailDialog(
    tx: TransactionEntity,
    categories: List<String>,
    recurringItems: List<RecurringItemEntity>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (TransactionEntity, Boolean) -> Unit
) {
    var editing by remember(tx.id) { mutableStateOf(false) }
    var amount by remember(tx.id) { mutableStateOf(stripTrailingZero(tx.amount)) }
    var merchant by remember(tx.id) { mutableStateOf(tx.merchant ?: "") }
    var category by remember(tx.id) { mutableStateOf(tx.category) }
    // The category to go back to when "self transfer" is switched off again.
    var categoryBeforeSelf by remember(tx.id) {
        mutableStateOf(if (tx.category == SELF_TRANSFER_CATEGORY) OTHER_CATEGORY else tx.category)
    }
    var type by remember(tx.id) { mutableStateOf(tx.type) }
    var isSelfTransfer by remember(tx.id) { mutableStateOf(tx.isSelfTransfer) }
    var excludeFromDailyAvg by remember(tx.id) { mutableStateOf(tx.excludeFromDailyAvg) }
    var isReimbursement by remember(tx.id) { mutableStateOf(tx.isReimbursement) }
    var confirmingDelete by remember(tx.id) { mutableStateOf(false) }
    val hasExistingReminder = remember(tx.id, tx.merchant, recurringItems) {
        val m = tx.merchant?.trim()
        m != null && recurringItems.any { it.merchant.equals(m, ignoreCase = true) }
    }
    var billReminder by remember(tx.id, hasExistingReminder) { mutableStateOf(hasExistingReminder) }
    val amountValue = amount.toDoubleOrNull()
    val amountValid = amountValue != null && amountValue > 0.0

    // Row tap and Switch share this, so both paths set the same category.
    val setSelfTransfer: (Boolean) -> Unit = { checked ->
        if (checked) {
            if (category != SELF_TRANSFER_CATEGORY) categoryBeforeSelf = category
            category = SELF_TRANSFER_CATEGORY
        } else if (category == SELF_TRANSFER_CATEGORY) {
            category = categoryBeforeSelf
        }
        isSelfTransfer = checked
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text(stringResource(R.string.tx_detail_confirm_delete_title), style = H2) },
            text = { Text(stringResource(R.string.tx_detail_confirm_delete_desc), style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text(stringResource(R.string.tx_detail_confirm_delete_yes), style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.tx_detail_cancel), style = Body.copy(color = InkSoft))
                }
            }
        )
    }

    FormSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = {
            Text(
                if (editing) stringResource(R.string.tx_detail_edit)
                else stringResource(R.string.tx_detail_title),
                style = H2
            )
        },
        text = {
            if (editing) {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    SegmentedToggle(
                        options = listOf(
                            stringResource(R.string.tx_type_expense) to Danger,
                            stringResource(R.string.tx_type_income) to Success
                        ),
                        selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                        onSelect = { type = if (it == 0) TxType.EXPENSE else TxType.INCOME }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = sanitizeAmountInput(it) },
                        label = { Text(stringResource(R.string.tx_field_amount_fmt, currencyLabel(tx.currency))) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = !amountValid,
                        supportingText = if (!amountValid) {
                            { Text(stringResource(R.string.home_amount_invalid), color = Danger) }
                        } else null,
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = merchant, onValueChange = { merchant = it },
                        label = { Text(stringResource(R.string.tx_field_merchant)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body
                    )
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(
                        stringResource(R.string.tx_self_transfer_title),
                        stringResource(R.string.tx_self_transfer_desc),
                        isSelfTransfer,
                        setSelfTransfer
                    )
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(
                        stringResource(R.string.tx_exclude_daily_title),
                        stringResource(R.string.tx_exclude_daily_desc),
                        excludeFromDailyAvg
                    ) { excludeFromDailyAvg = it }
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(
                        stringResource(R.string.tx_reimbursement_title),
                        stringResource(R.string.tx_reimbursement_desc),
                        isReimbursement
                    ) { isReimbursement = it }
                    if (!isSelfTransfer && merchant.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        ToggleRow(
                            stringResource(R.string.tx_bill_reminder_title),
                            stringResource(R.string.tx_bill_reminder_desc),
                            billReminder
                        ) { billReminder = it }
                    }
                    if (!isSelfTransfer) {
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.tx_field_category), style = Eyebrow)
                        Spacer(Modifier.height(8.dp))
                        CategoryPicker(categories, category) { category = it }
                    }
                }
            } else {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.AccountBalance, InkSoft, PaperOuter, size = 32.dp, iconSize = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (tx.isManual) stringResource(R.string.home_tx_manual)
                            else tx.bankName ?: stringResource(R.string.tx_unknown_bank),
                            style = Body.copy(fontWeight = FontWeight.Medium)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(formatDay(tx.timestamp), style = Eyebrow)
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(catIcon(tx.category), catColor(tx.category), catColorSoft(tx.category), size = 52.dp, iconSize = 24.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(tx.merchant ?: stringResource(R.string.tx_unknown_merchant), style = H2.copy(fontSize = 18.sp))
                        Spacer(Modifier.height(4.dp))
                        Text(categoryDisplay(tx.category), style = BodyMuted)
                        Spacer(Modifier.height(8.dp))
                        // Same colors as the list rows: expenses in ink (the
                        // sign carries direction), income in green.
                        Text(
                            "${if (tx.type == TxType.EXPENSE) "-" else "+"}${fmt(tx.amount)} ${currencyLabel(tx.currency)}",
                            style = TextStyle(
                                fontSize = 26.sp, fontWeight = FontWeight.Black,
                                color = if (tx.isSelfTransfer) InkFaint else if (tx.type == TxType.EXPENSE) Ink else Success
                            )
                        )
                        if (tx.isSelfTransfer) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.tx_self_transfer_note),
                                style = Eyebrow.copy(fontSize = 12.sp),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (tx.excludeFromDailyAvg) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.tx_exclude_daily_note),
                                style = Eyebrow.copy(fontSize = 12.sp),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (tx.isReimbursement) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.tx_reimbursement_note),
                                style = Eyebrow.copy(fontSize = 12.sp),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    // Manual entries have no SMS behind them (rawSms only holds
                    // a placeholder), so the section is hidden for them.
                    if (!tx.isManual && tx.rawSms.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.tx_original_sms), style = Body.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(8.dp))
                        Text(tx.rawSms, style = BodyMuted, modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(RadiusMd))
                            .background(PaperOuter)
                            .padding(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (editing) {
                        PillButton(
                            stringResource(R.string.tx_detail_cancel),
                            onClick = { editing = false },
                            modifier = Modifier.weight(1f)
                        )
                        PillButton(
                            stringResource(R.string.tx_detail_save),
                            onClick = {
                                val v = amount.toDoubleOrNull()
                                if (v != null && v > 0.0) {
                                    onSave(
                                        tx.copy(
                                            amount = v, merchant = merchant.ifBlank { null }, category = category, type = type,
                                            isSelfTransfer = isSelfTransfer, excludeFromDailyAvg = excludeFromDailyAvg,
                                            isReimbursement = isReimbursement
                                        ),
                                        billReminder
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            primary = true,
                            enabled = amountValid
                        )
                    } else {
                        PillButton(
                            stringResource(R.string.tx_detail_close),
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                        PillButton(
                            stringResource(R.string.tx_detail_edit_action),
                            onClick = { editing = true },
                            modifier = Modifier.weight(1f),
                            primary = true
                        )
                    }
                }
                if (!editing) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { confirmingDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Pill)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp), tint = Danger)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tx_detail_delete), style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                    }
                }
            }
        },
        dismissButton = null
    )
}

// ============ ADD TRANSACTION SHEET ============
@Composable
internal fun AddDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (Double, String, String, TxType, Long) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    // An empty category list must not crash the sheet: fall back to "other".
    var category by remember { mutableStateOf(categories.firstOrNull() ?: OTHER_CATEGORY) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var day by remember { mutableStateOf(startOfLocalDay(System.currentTimeMillis())) }
    var showDatePicker by remember { mutableStateOf(false) }
    val amountValue = amount.toDoubleOrNull()
    val amountValid = amountValue != null && amountValue > 0.0
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Give the sheet a moment to lay out before asking for the keyboard.
        delay(350)
        runCatching { focusRequester.requestFocus() }
    }
    val isToday = day == startOfLocalDay(System.currentTimeMillis())

    FormSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text(stringResource(R.string.tx_add_title), style = H2) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                SegmentedToggle(
                    options = listOf(
                        stringResource(R.string.tx_type_expense) to Danger,
                        stringResource(R.string.tx_type_income) to Success
                    ),
                    selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                    onSelect = { type = if (it == 0) TxType.EXPENSE else TxType.INCOME }
                )
                Spacer(Modifier.height(8.dp))
                // Big centered amount, focused on open.
                val amountStyle = Display.copy(textAlign = TextAlign.Center, fontSize = 38.sp)
                TextField(
                    value = amount,
                    onValueChange = { amount = sanitizeAmountInput(it) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    textStyle = amountStyle,
                    placeholder = {
                        Text(
                            if (isArabicUi()) "٠" else "0",
                            style = amountStyle.copy(color = InkFaint),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Text(
                    currencyLabel("SAR"),
                    style = Body.copy(color = InkSoft, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text(stringResource(R.string.tx_field_merchant)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(PaperOuter)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp), tint = Indigo)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.home_tx_date), style = Body.copy(color = InkSoft), modifier = Modifier.weight(1f))
                    Text(
                        if (isToday) stringResource(R.string.dash_today) else formatDay(day),
                        style = Body.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.tx_field_category), style = Eyebrow)
                Spacer(Modifier.height(8.dp))
                CategoryPicker(categories, category) { category = it }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    stringResource(R.string.tx_detail_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                PillButton(
                    stringResource(R.string.tx_detail_save),
                    onClick = {
                        val v = amount.toDoubleOrNull()
                        if (v != null && v > 0.0) {
                            val now = System.currentTimeMillis()
                            val todayStart = startOfLocalDay(now)
                            // Keep today's time of day so a back-dated entry
                            // still sorts naturally within its day.
                            val ts = if (day == todayStart) now else day + (now - todayStart)
                            onSave(v, merchant, category, type, ts)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    primary = true,
                    enabled = amountValid
                )
            }
        },
        dismissButton = null
    )

    if (showDatePicker) {
        SingleDatePickerDialog(
            initial = day,
            onDismiss = { showDatePicker = false },
            onConfirm = { day = it; showDatePicker = false }
        )
    }
}

@Composable
private fun SegmentedToggle(options: List<Pair<String, Color>>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Pill))
            .background(PaperOuter)
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, (label, color) ->
            val isSel = i == selectedIndex
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(Pill))
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

// "50.00" → "50", "50.50" → "50.5" — so the initial text field values don't
// show trailing zeros (ASCII digits, never scientific notation).
private fun stripTrailingZero(v: Double): String =
    String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
