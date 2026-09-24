package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.RecurringItemEntity

// ============ BILL REMINDERS ============
@Composable
fun RemindersSection(vm: MainViewModel) {
    val reminders by vm.recurringItems.collectAsState()
    val categories by vm.categories.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringItemEntity?>(null) }
    var confirmingDelete by remember { mutableStateOf<RecurringItemEntity?>(null) }

    val sorted = remember(reminders) { reminders.sortedBy { it.expectedDayOfMonth } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reminders_title), style = H2)
                    Text(stringResource(R.string.reminders_count_fmt, reminders.size), style = Eyebrow)
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(RadiusSm)).background(Ink900)
                        .clickable { showAdd = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, stringResource(R.string.reminders_add_title), tint = Lime, modifier = Modifier.size(20.dp)) }
            }
        }
        if (reminders.isEmpty()) {
            item { EmptyState(stringResource(R.string.reminders_empty_title)) }
        } else {
            items(sorted, key = { it.id }) { item ->
                ReminderCard(
                    item = item,
                    onEdit = { editing = item },
                    onDelete = { confirmingDelete = item },
                    onToggle = { checked -> vm.updateRecurringItem(item.copy(reminderEnabled = checked)) },
                    onToggleFixed = { checked -> vm.updateRecurringItem(item.copy(isFixed = checked)) }
                )
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

@Composable
private fun ReminderCard(
    item: RecurringItemEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onToggleFixed: (Boolean) -> Unit
) {
    val currency = currencyLabel("SAR")
    SoftCard(Modifier.clickable { onEdit() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(catIcon(item.category), catColor(item.category), catColorSoft(item.category), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.merchant, style = H2.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.reminders_day_fmt, item.expectedDayOfMonth) + " • " + categoryDisplay(item.category),
                    style = Eyebrow.copy(fontSize = 12.sp)
                )
            }
            IconAction(Icons.Default.Edit, stringResource(R.string.reminders_edit_title), Indigo, IndigoSoft, onEdit)
            Spacer(Modifier.width(6.dp))
            IconAction(Icons.Default.Delete, stringResource(R.string.reminders_delete), Danger, Danger.copy(alpha = 0.08f), onDelete)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                FinancialAdvisor.fmt(item.expectedAmount) + " " + currency,
                style = NumBold.copy(fontSize = 15.sp)
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = item.reminderEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Autorenew, null, Modifier.size(15.dp),
                tint = if (item.isFixed) Indigo else InkFaint
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.reminders_fixed_label),
                style = Eyebrow.copy(
                    fontSize = 12.sp,
                    color = if (item.isFixed) Indigo else InkFaint,
                    fontWeight = if (item.isFixed) FontWeight.Bold else FontWeight.Normal
                ),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = item.isFixed,
                onCheckedChange = onToggleFixed,
                colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
            )
        }
    }
}

@Composable
private fun ReminderEditorDialog(
    initial: RecurringItemEntity?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, Double, Int, String, Boolean, Boolean) -> Unit
) {
    val currency = currencyLabel("SAR")
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var amount by remember { mutableStateOf(initial?.let { editableAmount(it.expectedAmount) } ?: "") }
    var day by remember { mutableStateOf(initial?.expectedDayOfMonth?.toString() ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: categories.firstOrNull() ?: "أخرى") }
    var enabled by remember { mutableStateOf(initial?.reminderEnabled ?: true) }
    var fixed by remember { mutableStateOf(initial?.isFixed ?: false) }

    val parsedAmount = amount.toDoubleOrNull()
    val parsedDay = day.toIntOrNull()
    val amountInvalid = amount.isNotEmpty() && (parsedAmount == null || parsedAmount <= 0)
    val dayInvalid = day.isNotEmpty() && (parsedDay == null || parsedDay !in 1..28)
    val valid = merchant.isNotBlank() && parsedAmount != null && parsedAmount > 0 && parsedDay != null && parsedDay in 1..28
    val editing = initial != null

    FormSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
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
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
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
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = day, onValueChange = { day = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.reminders_field_day)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = dayInvalid,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.reminders_field_category), style = Eyebrow)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { c ->
                                Box(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(RadiusSm))
                                        .background(if (category == c) IndigoSoft else PaperOuter)
                                        .clickable { category = c }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        categoryDisplay(c),
                                        style = Eyebrow.copy(
                                            fontSize = 12.sp,
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
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusSm))
                        .background(PaperOuter)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.reminders_enabled_label), style = Body, modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusSm))
                        .background(PaperOuter)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.reminders_fixed_label), style = Body)
                        Text(stringResource(R.string.budget_commitments_desc), style = Eyebrow.copy(fontSize = 12.sp))
                    }
                    Switch(
                        checked = fixed,
                        onCheckedChange = { fixed = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(merchant.trim(), parsedAmount!!, parsedDay!!, category, enabled, fixed) }
            ) {
                Text(
                    stringResource(R.string.reminders_save),
                    style = Body.copy(color = if (valid) Indigo else InkFaint, fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reminders_cancel), style = Body.copy(color = InkSoft))
            }
        }
    )
}

private fun editableAmount(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
private fun IconAction(icon: ImageVector, desc: String, tint: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(RadiusSm)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(16.dp)) }
}
