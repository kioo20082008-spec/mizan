package com.mizan.money.ui

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

private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

private fun parseDateStart(s: String): Long? {
    if (s.isBlank()) return null
    return try { DATE_FORMAT.parse(s.trim())?.time } catch (e: Exception) { null }
}
private fun parseDateEnd(s: String): Long? {
    val start = parseDateStart(s) ?: return null
    // Inclusive end-of-day so a "to = 2026-09-17" filter still includes
    // transactions that happened later that same day.
    return start + 86_400_000L - 1
}
private fun formatDate(ts: Long): String = DATE_FORMAT.format(java.util.Date(ts))

// ============ ADVANCED FILTER SHEET ============
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdvancedFilterSheet(
    initial: TxFilter,
    categories: List<String>,
    availableBanks: List<String>,
    onDismiss: () -> Unit,
    onApply: (TxFilter) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var dateFromInput by remember { mutableStateOf(initial.dateFrom?.let { formatDate(it) } ?: "") }
    var dateToInput by remember { mutableStateOf(initial.dateTo?.let { formatDate(it) } ?: "") }
    var amountMinInput by remember { mutableStateOf(initial.amountMin?.let { stripTrailingZero(it) } ?: "") }
    var amountMaxInput by remember { mutableStateOf(initial.amountMax?.let { stripTrailingZero(it) } ?: "") }
    var selectedCats by remember { mutableStateOf(initial.categories) }
    var selectedType by remember { mutableStateOf(initial.type) }
    var selectedBanks by remember { mutableStateOf(initial.banks) }

    val dateFromParsed = remember(dateFromInput) { parseDateStart(dateFromInput) }
    val dateToParsed = remember(dateToInput) { parseDateEnd(dateToInput) }
    val dateFromInvalid = dateFromInput.isNotBlank() && dateFromParsed == null
    val dateToInvalid = dateToInput.isNotBlank() && dateToParsed == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White,
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
                Text(
                    stringResource(R.string.tx_filter_clear_all),
                    style = Body.copy(color = InkSoft, fontSize = 12.sp),
                    modifier = Modifier.clickable {
                        dateFromInput = ""; dateToInput = ""
                        amountMinInput = ""; amountMaxInput = ""
                        selectedCats = emptySet(); selectedType = null; selectedBanks = emptySet()
                    }.padding(6.dp)
                )
            }
            Spacer(Modifier.height(18.dp))

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // ===== DATE =====
                SectionLabel(stringResource(R.string.tx_filter_section_date))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateFromInput,
                        onValueChange = { dateFromInput = it },
                        placeholder = { Text(stringResource(R.string.tx_filter_from_hint), style = Eyebrow.copy(fontSize = 12.sp)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = dateFromInvalid,
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body.copy(fontSize = 13.sp)
                    )
                    OutlinedTextField(
                        value = dateToInput,
                        onValueChange = { dateToInput = it },
                        placeholder = { Text(stringResource(R.string.tx_filter_to_hint), style = Eyebrow.copy(fontSize = 12.sp)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = dateToInvalid,
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body.copy(fontSize = 13.sp)
                    )
                }
                if (dateFromInvalid || dateToInvalid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.tx_filter_date_invalid),
                        style = Eyebrow.copy(fontSize = 12.sp, color = Danger)
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeChip(stringResource(R.string.tx_filter_type_all), selectedType == null) { selectedType = null }
                    TypeChip(stringResource(R.string.tx_type_expense), selectedType == TxType.EXPENSE) { selectedType = TxType.EXPENSE }
                    TypeChip(stringResource(R.string.tx_type_income), selectedType == TxType.INCOME) { selectedType = TxType.INCOME }
                }
                Spacer(Modifier.height(20.dp))

                // ===== CATEGORIES =====
                SectionLabel(stringResource(R.string.tx_filter_section_categories))
                Spacer(Modifier.height(8.dp))
                FlowChips(
                    items = categories,
                    selected = selectedCats,
                    labelOf = { categoryDisplay(it) },
                    onToggle = { c ->
                        selectedCats = if (c in selectedCats) selectedCats - c else selectedCats + c
                    }
                )
                Spacer(Modifier.height(20.dp))

                // ===== BANKS =====
                SectionLabel(stringResource(R.string.tx_filter_section_banks))
                Spacer(Modifier.height(8.dp))
                if (availableBanks.isEmpty()) {
                    Text(stringResource(R.string.tx_filter_banks_empty), style = Eyebrow)
                } else {
                    FlowChips(
                        items = availableBanks,
                        selected = selectedBanks,
                        labelOf = { it },
                        onToggle = { b ->
                            selectedBanks = if (b in selectedBanks) selectedBanks - b else selectedBanks + b
                        }
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onApply(
                        TxFilter(
                            dateFrom = dateFromParsed,
                            dateTo = dateToParsed,
                            amountMin = amountMinInput.toDoubleOrNull(),
                            amountMax = amountMaxInput.toDoubleOrNull(),
                            categories = selectedCats,
                            type = selectedType,
                            banks = selectedBanks,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(RadiusSm),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Color.White)
            ) {
                Text(
                    stringResource(R.string.tx_filter_apply),
                    style = Body.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = Eyebrow.copy(color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp))
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(RadiusSm))
            .background(if (selected) Indigo else PaperOuter)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = Body.copy(
                fontSize = 13.sp,
                color = if (selected) Color.White else InkSoft,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}

// Simple flow layout using chunked rows — avoids pulling in FlowRow (which is
// still experimental in this Material3 version). Each chip auto-sizes, rows
// wrap at ~2 per line for Arabic and up to 3-4 for short English labels.
@Composable
private fun FlowChips(
    items: List<String>,
    selected: Set<String>,
    labelOf: @Composable (String) -> String,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item ->
                    val isSel = item in selected
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(RadiusSm))
                            .background(if (isSel) IndigoSoft else PaperOuter)
                            .border(
                                width = if (isSel) 1.dp else 0.dp,
                                color = if (isSel) Indigo else Color.Transparent,
                                shape = RoundedCornerShape(RadiusSm)
                            )
                            .clickable { onToggle(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            labelOf(item),
                            style = Eyebrow.copy(
                                fontSize = 12.sp,
                                color = if (isSel) Indigo else InkSoft,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            ),
                            maxLines = 1
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
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
    var amount by remember(tx.id) { mutableStateOf("%.2f".format(tx.amount)) }
    var merchant by remember(tx.id) { mutableStateOf(tx.merchant ?: "") }
    var category by remember(tx.id) { mutableStateOf(tx.category) }
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
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = merchant, onValueChange = { merchant = it },
                        label = { Text(stringResource(R.string.tx_field_merchant)) },
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
                            Text(stringResource(R.string.tx_self_transfer_title), style = Body.copy(fontWeight = FontWeight.Medium))
                            Text(stringResource(R.string.tx_self_transfer_desc), style = Eyebrow.copy(fontSize = 12.sp))
                        }
                        Switch(
                            checked = isSelfTransfer,
                            onCheckedChange = { checked ->
                                isSelfTransfer = checked
                                if (checked) category = com.mizan.money.data.SELF_TRANSFER_CATEGORY
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(RadiusSm))
                            .background(PaperOuter)
                            .clickable { excludeFromDailyAvg = !excludeFromDailyAvg }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.tx_exclude_daily_title), style = Body.copy(fontWeight = FontWeight.Medium))
                            Text(stringResource(R.string.tx_exclude_daily_desc), style = Eyebrow.copy(fontSize = 12.sp))
                        }
                        Switch(
                            checked = excludeFromDailyAvg,
                            onCheckedChange = { excludeFromDailyAvg = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(RadiusSm))
                            .background(PaperOuter)
                            .clickable { isReimbursement = !isReimbursement }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.tx_reimbursement_title), style = Body.copy(fontWeight = FontWeight.Medium))
                            Text(stringResource(R.string.tx_reimbursement_desc), style = Eyebrow.copy(fontSize = 12.sp))
                        }
                        Switch(
                            checked = isReimbursement,
                            onCheckedChange = { isReimbursement = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
                        )
                    }
                    if (!isSelfTransfer && merchant.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(RadiusSm))
                                .background(PaperOuter)
                                .clickable { billReminder = !billReminder }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.tx_bill_reminder_title), style = Body.copy(fontWeight = FontWeight.Medium))
                                Text(stringResource(R.string.tx_bill_reminder_desc), style = Eyebrow.copy(fontSize = 12.sp))
                            }
                            Switch(
                                checked = billReminder,
                                onCheckedChange = { billReminder = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
                            )
                        }
                    }
                    if (!isSelfTransfer) {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.tx_field_category), style = Eyebrow)
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            categories.chunked(2).forEach { row ->
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
                                                categoryDisplay(c),
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
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.AccountBalance, InkSoft, PaperOuter, size = 32.dp, iconSize = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(tx.bankName ?: stringResource(R.string.tx_unknown_bank), style = Body.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.weight(1f))
                        Text(Dates.dayLabel(tx.timestamp), style = Eyebrow)
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tx.merchant ?: stringResource(R.string.tx_unknown_merchant), style = H2.copy(fontSize = 18.sp))
                        Spacer(Modifier.height(4.dp))
                        Text(categoryDisplay(tx.category), style = BodyMuted)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${if (tx.type == TxType.EXPENSE) "-" else "+"}${FinancialAdvisor.fmt(tx.amount)} ${currencyLabel(tx.currency)}",
                            style = TextStyle(
                                fontSize = 26.sp, fontWeight = FontWeight.Black,
                                color = if (tx.isSelfTransfer) InkFaint else if (tx.type == TxType.EXPENSE) Danger else Success
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
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.tx_original_sms), style = Body.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(8.dp))
                    Text(tx.rawSms, style = BodyMuted, modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusSm))
                        .background(PaperOuter)
                        .padding(12.dp))
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text(stringResource(R.string.tx_detail_delete), style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                    }
                }
            }
        },
        confirmButton = {
            if (editing) {
                TextButton(onClick = {
                    amount.toDoubleOrNull()?.let {
                        onSave(
                            tx.copy(
                                amount = it, merchant = merchant.ifBlank { null }, category = category, type = type,
                                isSelfTransfer = isSelfTransfer, excludeFromDailyAvg = excludeFromDailyAvg,
                                isReimbursement = isReimbursement
                            ),
                            billReminder
                        )
                    }
                }) { Text(stringResource(R.string.tx_detail_save), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)) }
            } else {
                TextButton(onClick = { editing = true }) {
                    Text(stringResource(R.string.tx_detail_edit_action), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
                }
            }
        },
        dismissButton = {
            if (editing) {
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(R.string.tx_detail_cancel), style = Body.copy(color = InkSoft))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.tx_detail_close), style = Body.copy(color = InkSoft))
                }
            }
        }
    )
}

@Composable
internal fun AddDialog(categories: List<String>, onDismiss: () -> Unit, onSave: (Double, String, String, TxType) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.first()) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }

    FormSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text(stringResource(R.string.tx_add_title), style = H2) },
        text = {
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
                    label = { Text(stringResource(R.string.tx_field_amount_fmt, currencyLabel("SAR"))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text(stringResource(R.string.tx_field_merchant)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.tx_field_category), style = Eyebrow)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.chunked(2).forEach { row ->
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
                                        categoryDisplay(c),
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
            }) { Text(stringResource(R.string.tx_detail_save), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tx_detail_cancel), style = Body.copy(color = InkSoft))
            }
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

// "50.00" → "50", "50.50" → "50.50" — so the initial text field values don't
// show trailing zeros for whole-number amounts.
private fun stripTrailingZero(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
