package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.data.RecurringItemEntity
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import kotlin.math.abs

// ============ BILL REMINDERS ============
// Rendered as the "monthly bills" section of the Planning > Commitments page
// (one scrolling page together with the debts section).
@Composable
fun RemindersBlock(vm: MainViewModel) {
    val reminders by vm.recurringItems.collectAsState()
    val categories by vm.categories.collectAsState()
    val txs by vm.transactions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringItemEntity?>(null) }
    var confirmingDelete by remember { mutableStateOf<RecurringItemEntity?>(null) }
    // Due dates are calendar-month based (not the budget cycle), so "paid"
    // looks at this calendar month's transactions.
    val monthStart = remember { Dates.monthRange(0, 1).first }
    val paidIds = remember(reminders, txs) {
        val monthTx = txs.filter {
            it.timestamp >= monthStart && it.type == TxType.EXPENSE && !it.isSelfTransfer && it.merchant != null
        }
        reminders.filter { r -> monthTx.any { tx -> matchesBill(r, tx) } }.map { it.id }.toSet()
    }
    val sorted = remember(reminders) {
        reminders.map { it to planningDaysUntilDue(it.expectedDayOfMonth) }.sortedBy { it.second }
    }
    val monthlyTotal = reminders.filter { it.reminderEnabled }.sumOf { it.expectedAmount }
    val currency = currencyLabel("SAR")

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanningSectionHeader(
            title = stringResource(R.string.pl_section_bills),
            subtitle = if (reminders.isEmpty()) null else
                planningPlural(R.plurals.pl_bills_count, reminders.size) + " · " +
                    stringResource(R.string.pl_bills_total_fmt, fmt(monthlyTotal), currency),
            actionLabel = if (reminders.isEmpty()) null else stringResource(R.string.pl_add),
            actionIcon = Icons.Default.Add,
            onAction = { showAdd = true }
        )
        if (reminders.isEmpty()) {
            PlanningEmptyCard(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                text = stringResource(R.string.pl_bills_empty_desc),
                actionLabel = stringResource(R.string.pl_bills_add),
                actionIcon = Icons.Default.Add,
                onAction = { showAdd = true }
            )
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusLg)).background(White)) {
                sorted.forEachIndexed { i, (item, daysUntil) ->
                    ReminderRow(
                        item = item,
                        daysUntil = daysUntil,
                        paid = item.id in paidIds,
                        showDivider = i < sorted.lastIndex,
                        onEdit = { editing = item },
                        onToggle = { checked -> vm.updateRecurringItem(item.copy(reminderEnabled = checked)) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        ReminderEditorDialog(
            initial = null,
            categories = categories,
            onDismiss = { showAdd = false },
            onSave = { merchant, amount, day, category, enabled, fixed ->
                vm.addRecurringItem(merchant, amount, day, category, enabled, fixed); showAdd = false
            }
        )
    }
    editing?.let { item ->
        ReminderEditorDialog(
            initial = item,
            categories = categories,
            onDismiss = { editing = null },
            onDelete = { editing = null; confirmingDelete = item },
            onSave = { merchant, amount, day, category, enabled, fixed ->
                vm.updateRecurringItem(
                    item.copy(
                        merchant = merchant,
                        expectedAmount = amount,
                        expectedDayOfMonth = day,
                        category = category,
                        reminderEnabled = enabled,
                        isFixed = fixed
                    )
                )
                editing = null
            }
        )
    }
    confirmingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text(stringResource(R.string.reminders_delete_confirm, item.merchant), style = H2) },
            text = { Text(stringResource(R.string.reminders_delete_confirm_desc), style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = { vm.deleteRecurringItem(item); confirmingDelete = null }) {
                    Text(stringResource(R.string.reminders_delete), style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text(stringResource(R.string.reminders_cancel), style = Body.copy(color = InkSoft))
                }
            }
        )
    }
}

// A posted expense counts as this bill's payment when the merchant names
// overlap (either contains the other, case-insensitive) and the amount is
// within ±20% of the expected amount.
private fun matchesBill(r: RecurringItemEntity, tx: TransactionEntity): Boolean {
    val a = r.merchant.trim().lowercase()
    val b = tx.merchant?.trim()?.lowercase().orEmpty()
    if (a.isEmpty() || b.isEmpty()) return false
    if (!a.contains(b) && !b.contains(a)) return false
    val expected = r.expectedAmount
    if (expected <= 0.0) return true
    return abs(tx.amount - expected) <= expected * 0.2
}

@Composable
private fun dueDayLabel(day: Int): String =
    if (day >= LAST_DAY_OF_MONTH) stringResource(R.string.pl_last_day)
    else stringResource(R.string.pl_day_fmt, localDigits(day))

// One UI grouped row: tap to edit (fixed-deduction toggle and delete live in
// the edit sheet), the switch turns the reminder on/off in place.
@Composable
private fun ReminderRow(
    item: RecurringItemEntity,
    daysUntil: Int,
    paid: Boolean,
    showDivider: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val currency = currencyLabel("SAR")
    Column(Modifier.fillMaxWidth().clickable { onEdit() }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(catIcon(item.category), catColor(item.category), catColorSoft(item.category), size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.merchant,
                    style = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    fmt(item.expectedAmount) + " " + currency + " · " + dueDayLabel(item.expectedDayOfMonth),
                    style = Body.copy(fontSize = 13.sp, color = InkSoft),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        paid -> {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(13.dp), tint = Success)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.pl_paid),
                                style = Body.copy(fontSize = 12.sp, color = Success, fontWeight = FontWeight.Bold)
                            )
                        }
                        item.reminderEnabled -> Text(
                            if (daysUntil == 0) stringResource(R.string.pl_today)
                            else planningPlural(R.plurals.pl_in_days, daysUntil),
                            style = Body.copy(
                                fontSize = 12.sp,
                                color = if (daysUntil <= 3) Amber else InkSoft,
                                fontWeight = if (daysUntil <= 3) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                    if (item.isFixed) {
                        if (paid || item.reminderEnabled) {
                            Text(" · ", style = Body.copy(fontSize = 12.sp, color = InkFaint))
                        }
                        Text(
                            stringResource(R.string.reminders_fixed_short),
                            style = Body.copy(fontSize = 12.sp, color = Indigo, fontWeight = FontWeight.Medium),
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).height(28.dp).background(Line))
            Spacer(Modifier.width(10.dp))
            Switch(checked = item.reminderEnabled, onCheckedChange = onToggle, colors = oneUiSwitchColors())
        }
        if (showDivider) {
            Box(Modifier.padding(start = 72.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(Line))
        }
    }
}

@Composable
private fun ReminderEditorDialog(
    initial: RecurringItemEntity?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (String, Double, Int, String, Boolean, Boolean) -> Unit
) {
    val currency = currencyLabel("SAR")
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var amount by remember { mutableStateOf(initial?.let { planningEditableAmount(it.expectedAmount) } ?: "") }
    var day by remember { mutableStateOf(initial?.expectedDayOfMonth?.coerceIn(1, LAST_DAY_OF_MONTH)) }
    var category by remember { mutableStateOf(initial?.category ?: categories.firstOrNull() ?: "أخرى") }
    var enabled by remember { mutableStateOf(initial?.reminderEnabled ?: true) }
    var fixed by remember { mutableStateOf(initial?.isFixed ?: false) }

    val parsedAmount = amount.toDoubleOrNull()
    val amountInvalid = amount.isNotEmpty() && (parsedAmount == null || parsedAmount <= 0)
    val valid = merchant.isNotBlank() && parsedAmount != null && parsedAmount > 0 && day != null
    val editing = initial != null
    // Keep the item's own category selectable even if it was since removed.
    val catOptions = remember(categories, category) {
        if (category in categories) categories else listOf(category) + categories
    }

    FormSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
        title = {
            Text(
                stringResource(if (editing) R.string.reminders_edit_title else R.string.reminders_add_title),
                style = H2
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text(stringResource(R.string.reminders_field_merchant)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusMd), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.reminders_field_amount, currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountInvalid,
                    supportingText = {
                        if (amountInvalid) {
                            Text(stringResource(R.string.goals_amount_error), style = Eyebrow.copy(color = Danger))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusMd), textStyle = Body
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.pl_due_day_label), style = Eyebrow.copy(fontSize = 13.sp))
                Spacer(Modifier.height(8.dp))
                DueDayPicker(selected = day, onSelect = { day = it })
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.reminders_field_category), style = Eyebrow.copy(fontSize = 13.sp))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    catOptions.forEach { c ->
                        PlanningChip(label = categoryDisplay(c), selected = category == c) { category = c }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusMd)).background(PaperOuter)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { enabled = !enabled }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.reminders_enabled_label), style = Body, modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = oneUiSwitchColors())
                    }
                    Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(Line))
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { fixed = !fixed }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.reminders_fixed_label), style = Body)
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(R.string.pl_fixed_desc), style = Eyebrow.copy(fontSize = 12.sp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = fixed, onCheckedChange = { fixed = it }, colors = oneUiSwitchColors())
                    }
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(16.dp))
                    PlanningDeleteButton(stringResource(R.string.pl_bill_delete), onDelete)
                }
            }
        },
        confirmButton = {
            PlanningSheetConfirm(stringResource(R.string.reminders_save), enabled = valid) {
                val a = parsedAmount ?: return@PlanningSheetConfirm
                val d = day ?: return@PlanningSheetConfirm
                onSave(merchant.trim(), a, d, category, enabled, fixed)
            }
        },
        dismissButton = { PlanningSheetCancel(onDismiss) }
    )
}

// 7-column grid of days 1..31; the last row's spare cells hold a wide
// "last day of month" option. Day 31 and "last day" are stored the same way
// (31, clamped to each month's length), so they share one selected state.
@Composable
private fun DueDayPicker(selected: Int?, onSelect: (Int) -> Unit) {
    val cols = 7
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..28).chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d -> DayCell(d, selected == d, Modifier.weight(1f)) { onSelect(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (29..30).forEach { d -> DayCell(d, selected == d, Modifier.weight(1f)) { onSelect(d) } }
            DayCell(31, false, Modifier.weight(1f)) { onSelect(LAST_DAY_OF_MONTH) }
            val lastSel = selected == LAST_DAY_OF_MONTH
            Box(
                Modifier.weight(4f).height(40.dp)
                    .clip(RoundedCornerShape(Pill))
                    .background(if (lastSel) Ink900 else PaperOuter)
                    .clickable { onSelect(LAST_DAY_OF_MONTH) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.pl_last_day),
                    style = Body.copy(
                        fontSize = 13.sp,
                        color = if (lastSel) Lime else InkSoft,
                        fontWeight = if (lastSel) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp)
            .clip(RoundedCornerShape(Pill))
            .background(if (selected) Ink900 else PaperOuter)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            localDigits(day),
            style = Body.copy(
                fontSize = 14.sp,
                color = if (selected) Lime else Ink,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}
