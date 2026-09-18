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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.DebtEntity
import com.mizan.money.data.DebtType
import com.mizan.money.data.GoalEntity
import com.mizan.money.data.TxType
import kotlin.math.roundToInt

// ============ PLANNING ============
@Composable
fun PlanningScreen(vm: MainViewModel, offset: Int) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 10.dp)) {
            Text(stringResource(R.string.planning_title), style = H1)
            Spacer(Modifier.height(10.dp))
            TabSwitcher(
                listOf(
                    stringResource(R.string.planning_tab_budget),
                    stringResource(R.string.planning_tab_goals),
                    stringResource(R.string.planning_tab_debts),
                ),
                subTab
            ) { subTab = it }
        }
        Box(Modifier.weight(1f)) {
            when (subTab) {
                0 -> BudgetScreen(vm, offset)
                1 -> GoalsSection(vm)
                else -> DebtsSection(vm)
            }
        }
    }
}

// ============ SAVINGS GOALS ============
// Goals store an absolute targetDate, but the UI thinks in "how many months".
// One shared constant + helper keeps the create chips and the edit form honest.
private const val MONTH_MS = 30L * 86_400_000L

private fun monthsBetweenNow(targetDate: Long?): Int? {
    if (targetDate == null) return null
    val diff = targetDate - System.currentTimeMillis()
    if (diff <= 0L) return 0
    return ((diff + MONTH_MS - 1) / MONTH_MS).toInt()
}

private fun monthsToTargetDate(months: Int): Long = System.currentTimeMillis() + months * MONTH_MS

private fun editableAmount(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
private fun GoalsSection(vm: MainViewModel) {
    val goals by vm.goals.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var suggestedName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    var contributingTo by remember { mutableStateOf<GoalEntity?>(null) }
    var confirmingDelete by remember { mutableStateOf<GoalEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.goals_title), style = H2)
                    Text(stringResource(R.string.goals_subtitle), style = Eyebrow)
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(RadiusSm)).background(Ink900)
                        .clickable { suggestedName = ""; showAdd = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, stringResource(R.string.goals_add), tint = Lime, modifier = Modifier.size(20.dp)) }
            }
        }
        if (goals.isEmpty()) {
            item {
                GoalsEmptyState(onCreate = { name -> suggestedName = name; showAdd = true })
            }
        } else {
            items(goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    onContribute = { contributingTo = goal },
                    onEdit = { editing = goal },
                    onDelete = { confirmingDelete = goal }
                )
            }
        }
    }

    if (showAdd) {
        GoalEditorDialog(
            initial = null,
            initialName = suggestedName,
            onDismiss = { showAdd = false },
            onSave = { name, amount, targetDate ->
                vm.addGoal(name, amount, targetDate); showAdd = false
            }
        )
    }
    editing?.let { goal ->
        GoalEditorDialog(
            initial = goal,
            onDismiss = { editing = null },
            onSave = { name, amount, targetDate ->
                vm.editGoal(goal, name, amount, targetDate); editing = null
            }
        )
    }
    contributingTo?.let { goal ->
        GoalAmountDialog(
            goal = goal,
            onDismiss = { contributingTo = null },
            onDeposit = { amount -> vm.contributeToGoal(goal, amount); contributingTo = null },
            onWithdraw = { amount -> vm.withdrawFromGoal(goal, amount); contributingTo = null }
        )
    }
    confirmingDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text(stringResource(R.string.goals_delete_confirm_title_fmt, goal.name), style = H2) },
            text = { Text(stringResource(R.string.goals_delete_confirm_desc), style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = { vm.deleteGoal(goal); confirmingDelete = null }) {
                    Text(stringResource(R.string.goals_delete), style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text(stringResource(R.string.goals_cancel), style = Body.copy(color = InkSoft))
                }
            }
        )
    }
}

@Composable
private fun GoalsEmptyState(onCreate: (String) -> Unit) {
    SoftCard {
        IconBadge(Icons.Default.Savings, Indigo, IndigoSoft, size = 52.dp, iconSize = 24.dp, radius = RadiusMd)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.goals_empty_title), style = H2)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.goals_empty_desc), style = BodyMuted)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.goals_empty_suggestions), style = Eyebrow)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                R.string.goals_suggestion_emergency,
                R.string.goals_suggestion_trip,
                R.string.goals_suggestion_car,
                R.string.goals_suggestion_phone,
            ).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { res ->
                        val label = stringResource(res)
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(RadiusSm)).background(PaperOuter)
                                .clickable { onCreate(label) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, style = Eyebrow.copy(fontSize = 11.sp, color = InkSoft)) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: GoalEntity,
    onContribute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val currency = currencyLabel("SAR")
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
    val pct = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat() else 0f
    val reached = goal.currentAmount >= goal.targetAmount
    val deadline = goal.targetDate
    val monthsLeft = monthsBetweenNow(deadline)

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Savings, if (reached) Success else Indigo, if (reached) Success.copy(alpha = 0.12f) else IndigoSoft, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = H2.copy(fontSize = 14.sp))
                val untilSuffix = deadline?.let { stringResource(R.string.goals_target_until_fmt, Dates.dayLabel(it)) } ?: ""
                Text(
                    stringResource(
                        R.string.goals_amount_fmt,
                        FinancialAdvisor.fmt(goal.currentAmount),
                        FinancialAdvisor.fmt(goal.targetAmount),
                        untilSuffix
                    ),
                    style = Eyebrow.copy(fontSize = 10.sp)
                )
            }
            IconAction(Icons.Default.Edit, stringResource(R.string.goals_edit), Indigo, IndigoSoft, onEdit)
            Spacer(Modifier.width(6.dp))
            IconAction(Icons.Default.Delete, stringResource(R.string.goals_delete), Danger, Danger.copy(alpha = 0.08f), onDelete)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(Pill)).background(PaperOuter)) {
            Box(
                Modifier.fillMaxWidth(pct).fillMaxHeight().clip(RoundedCornerShape(Pill))
                    .background(if (reached) Success else Indigo)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (reached) stringResource(R.string.goals_reached)
                else stringResource(R.string.goals_progress_fmt, (pct * 100).roundToInt()),
                style = Eyebrow.copy(fontSize = 11.sp, color = if (reached) Success else InkFaint, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            if (!reached) {
                Text(
                    stringResource(R.string.goals_remaining_fmt, FinancialAdvisor.fmt(remaining), currency),
                    style = Eyebrow.copy(fontSize = 11.sp, color = InkSoft, fontWeight = FontWeight.Bold)
                )
            }
        }
        if (!reached) {
            Spacer(Modifier.height(6.dp))
            val overdue = deadline != null && (monthsLeft == null || monthsLeft <= 0)
            val plan = when {
                deadline == null -> stringResource(R.string.goals_no_deadline_hint)
                overdue -> stringResource(R.string.goals_overdue_hint)
                else -> {
                    val m = monthsLeft ?: 1
                    stringResource(
                        R.string.goals_plan_fmt,
                        FinancialAdvisor.fmt(remaining / m),
                        currency,
                        Dates.dayLabel(deadline)
                    )
                }
            }
            Text(
                plan,
                style = Eyebrow.copy(
                    fontSize = 10.sp,
                    color = if (overdue) Amber else InkFaint,
                    fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (reached) PaperOuter else Indigo)
                .clickable(enabled = !reached, onClick = onContribute)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (reached) stringResource(R.string.goals_reached) else stringResource(R.string.goals_add_amount),
                style = Body.copy(color = if (reached) InkFaint else White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            )
        }
    }
}

@Composable
private fun GoalEditorDialog(
    initial: GoalEntity?,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (String, Double, Long?) -> Unit
) {
    val currency = currencyLabel("SAR")
    var name by remember { mutableStateOf(initial?.name ?: initialName) }
    var amount by remember { mutableStateOf(initial?.let { editableAmount(it.targetAmount) } ?: "") }
    var months by remember {
        mutableStateOf(monthsBetweenNow(initial?.targetDate)?.takeIf { it > 0 }?.toString() ?: "")
    }
    val targetAmount = amount.toDoubleOrNull()
    val amountInvalid = amount.isNotEmpty() && (targetAmount == null || targetAmount <= 0)
    val monthsInt = months.toIntOrNull()?.takeIf { it > 0 }
    val computedDate = monthsInt?.let { monthsToTargetDate(it) }
    val valid = name.isNotBlank() && targetAmount != null && targetAmount > 0
    val editing = initial != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = {
            Text(
                stringResource(if (editing) R.string.goals_edit_dialog_title else R.string.goals_add_dialog_title),
                style = H2
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.goals_name_hint)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.goals_amount_hint_fmt, currency)) },
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
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.goals_deadline_label), style = Eyebrow)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 6, 12).forEach { m ->
                        ChoiceChip(
                            label = stringResource(R.string.goals_months_quick_fmt, m),
                            selected = months == m.toString(),
                            modifier = Modifier.weight(1f)
                        ) { months = m.toString() }
                    }
                }
                Spacer(Modifier.height(6.dp))
                ChoiceChip(
                    label = stringResource(R.string.goals_no_deadline_chip),
                    selected = months.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { months = "" }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = months, onValueChange = { months = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.goals_months_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            if (computedDate != null) stringResource(R.string.goals_deadline_fmt, Dates.dayLabel(computedDate))
                            else stringResource(R.string.goals_deadline_none),
                            style = Eyebrow.copy(color = InkFaint)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(name.trim(), targetAmount!!, computedDate) }
            ) {
                Text(
                    stringResource(if (editing) R.string.goals_edit_action else R.string.goals_add_action),
                    style = Body.copy(color = if (valid) Indigo else InkFaint, fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.goals_cancel), style = Body.copy(color = InkSoft)) }
        }
    )
}

@Composable
private fun GoalAmountDialog(
    goal: GoalEntity,
    onDismiss: () -> Unit,
    onDeposit: (Double) -> Unit,
    onWithdraw: (Double) -> Unit
) {
    val currency = currencyLabel("SAR")
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
    var amount by remember { mutableStateOf("") }
    var withdraw by remember { mutableStateOf(false) }
    val parsed = amount.toDoubleOrNull()
    val exceedsBalance = withdraw && parsed != null && parsed > goal.currentAmount
    val invalid = amount.isNotEmpty() && (parsed == null || parsed <= 0 || exceedsBalance)
    val valid = parsed != null && parsed > 0 && !exceedsBalance

    val quick = mutableListOf<Pair<String, Double>>()
    listOf(100.0, 500.0, 1000.0).forEach {
        quick.add(stringResource(R.string.goals_quick_add_fmt, editableAmount(it)) to it)
    }
    if (!withdraw && remaining > 0) {
        quick.add(stringResource(R.string.goals_quick_remaining) to remaining)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = {
            Text(
                stringResource(
                    if (withdraw) R.string.goals_withdraw_title_fmt else R.string.goals_contribute_title_fmt,
                    goal.name
                ),
                style = H2
            )
        },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusSm)).background(PaperOuter).padding(4.dp)
                ) {
                    ModeTab(stringResource(R.string.goals_deposit), !withdraw, Modifier.weight(1f)) { withdraw = false }
                    ModeTab(stringResource(R.string.goals_withdraw), withdraw, Modifier.weight(1f)) { withdraw = true }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.goals_current_balance_fmt, FinancialAdvisor.fmt(goal.currentAmount), currency),
                    style = Eyebrow.copy(fontSize = 11.sp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.goals_amount_label_fmt, currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = invalid,
                    supportingText = {
                        if (invalid) {
                            Text(
                                stringResource(if (exceedsBalance) R.string.goals_withdraw_exceeds_error else R.string.goals_amount_error),
                                style = Eyebrow.copy(color = Danger)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quick.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (label, value) ->
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(RadiusSm)).background(IndigoSoft)
                                        .clickable { amount = editableAmount(value) }.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, style = Eyebrow.copy(fontSize = 11.sp, color = Indigo, fontWeight = FontWeight.Bold))
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val v = parsed ?: return@TextButton
                    if (withdraw) onWithdraw(v) else onDeposit(v)
                }
            ) {
                Text(
                    stringResource(if (withdraw) R.string.goals_withdraw_action else R.string.goals_save),
                    style = Body.copy(color = if (valid) Indigo else InkFaint, fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.goals_cancel), style = Body.copy(color = InkSoft)) }
        }
    )
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(RadiusSm)).background(if (selected) White else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = Eyebrow.copy(fontSize = 12.sp, color = if (selected) Indigo else InkSoft, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(RadiusSm))
            .background(if (selected) IndigoSoft else PaperOuter)
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = Eyebrow.copy(
                fontSize = 11.sp,
                color = if (selected) Indigo else InkSoft,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}

@Composable
private fun IconAction(icon: ImageVector, desc: String, tint: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(RadiusSm)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun ContributeDialog(title: String, amountLabel: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text(title, style = H2) },
        text = {
            OutlinedTextField(
                value = amount, onValueChange = { amount = sanitizeAmountInput(it) },
                label = { Text(amountLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(RadiusSm), textStyle = Body
            )
        },
        confirmButton = {
            TextButton(onClick = { amount.toDoubleOrNull()?.takeIf { it > 0 }?.let(onSave) }) {
                Text(stringResource(R.string.goals_save), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.goals_cancel), style = Body.copy(color = InkSoft)) }
        }
    )
}

// ============ DEBTS ============
@Composable
private fun DebtsSection(vm: MainViewModel) {
    val debts by vm.debts.collectAsState()
    val txs by vm.transactions.collectAsState()
    val dismissed by vm.dismissedBnplSuggestions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var payingOn by remember { mutableStateOf<DebtEntity?>(null) }
    var confirmingDelete by remember { mutableStateOf<DebtEntity?>(null) }

    val suggestion = remember(txs, debts, dismissed) {
        detectUntrackedBnpl(txs, debts.map { it.name.lowercase().trim() }.toSet(), dismissed)
            .firstOrNull()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.debts_title), style = H2)
                    Text(stringResource(R.string.debts_subtitle), style = Eyebrow)
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(RadiusSm)).background(Ink900)
                        .clickable { showAdd = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, stringResource(R.string.debts_add), tint = Lime, modifier = Modifier.size(20.dp)) }
            }
        }
        if (suggestion != null) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusMd)).background(IndigoSoft)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Default.Lightbulb, Indigo, White, size = 36.dp, iconSize = 16.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.debts_bnpl_suggest_fmt, suggestion.first),
                            style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        )
                        Text(
                            stringResource(R.string.debts_bnpl_track_hint_fmt, FinancialAdvisor.fmt(suggestion.second)),
                            style = Eyebrow.copy(fontSize = 10.sp)
                        )
                    }
                    TextButton(onClick = {
                        vm.addDebt(suggestion.first, DebtType.BNPL, suggestion.second * 4, suggestion.second * 4, suggestion.second, 30)
                    }) {
                        Text(stringResource(R.string.debts_bnpl_track), style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                    }
                    TextButton(onClick = { vm.dismissBnplSuggestion(suggestion.first) }) {
                        Text(stringResource(R.string.debts_bnpl_dismiss), style = BodyMuted.copy(color = InkFaint, fontSize = 12.sp))
                    }
                }
            }
        }
        if (debts.isEmpty()) {
            item { EmptyState(stringResource(R.string.debts_empty)) }
        } else {
            items(debts, key = { it.id }) { debt ->
                DebtCard(debt = debt, onPay = { payingOn = debt }, onDelete = { confirmingDelete = debt })
            }
        }
    }

    if (showAdd) {
        AddDebtDialog(onDismiss = { showAdd = false }, onSave = { name, type, total, remaining, installment, days ->
            vm.addDebt(name, type, total, remaining, installment, days); showAdd = false
        })
    }
    payingOn?.let { debt ->
        ContributeDialog(
            title = stringResource(R.string.debts_pay_dialog_title_fmt, debt.name),
            amountLabel = stringResource(R.string.debts_pay_amount_fmt, currencyLabel("SAR")),
            onDismiss = { payingOn = null },
            onSave = { amount -> vm.logDebtPayment(debt, amount); payingOn = null }
        )
    }
    confirmingDelete?.let { debt ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text(stringResource(R.string.debts_delete_confirm_title_fmt, debt.name), style = H2) },
            text = { Text(stringResource(R.string.debts_delete_confirm_desc), style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = { vm.deleteDebt(debt); confirmingDelete = null }) {
                    Text(stringResource(R.string.debts_delete), style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text(stringResource(R.string.debts_cancel), style = Body.copy(color = InkSoft))
                }
            }
        )
    }
}

@Composable
private fun debtTypeLabel(type: DebtType): String = when (type) {
    DebtType.LOAN -> stringResource(R.string.debts_type_loan)
    DebtType.BNPL -> stringResource(R.string.debts_type_bnpl)
    DebtType.CREDIT_CARD -> stringResource(R.string.debts_type_credit)
    DebtType.OTHER -> stringResource(R.string.debts_type_other)
}

@Composable
private fun DebtCard(debt: DebtEntity, onPay: () -> Unit, onDelete: () -> Unit) {
    val paid = (debt.totalAmount - debt.remainingAmount).coerceAtLeast(0.0)
    val pct = if (debt.totalAmount > 0) (paid / debt.totalAmount).coerceIn(0.0, 1.0).toFloat() else 0f
    val dueSoon = debt.nextDueDate != null && debt.nextDueDate - System.currentTimeMillis() in 0..(3L * 86_400_000L)
    val settled = debt.remainingAmount <= 0.0
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.CreditCard, if (settled) Success else Amber, if (settled) Success.copy(alpha = 0.12f) else Amber.copy(alpha = 0.12f), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(debt.name, style = H2.copy(fontSize = 14.sp))
                    if (dueSoon && !settled) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(Pill)).background(Danger))
                    }
                }
                Text(debtTypeLabel(debt.type), style = Eyebrow.copy(fontSize = 10.sp))
            }
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(RadiusSm)).background(Danger.copy(alpha = 0.08f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Delete, stringResource(R.string.debts_delete), tint = Danger, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(Pill)).background(PaperOuter)) {
            Box(
                Modifier.fillMaxWidth(pct).fillMaxHeight().clip(RoundedCornerShape(Pill))
                    .background(if (settled) Success else Amber)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val dueSuffix = debt.nextDueDate?.let { stringResource(R.string.debts_installment_suffix_fmt, Dates.dayLabel(it)) } ?: ""
            Text(
                if (settled) stringResource(R.string.debts_settled)
                else stringResource(
                    R.string.debts_remaining_fmt,
                    FinancialAdvisor.fmt(debt.remainingAmount),
                    FinancialAdvisor.fmt(debt.totalAmount),
                    dueSuffix
                ),
                style = Eyebrow.copy(
                    fontSize = 10.sp,
                    color = if (dueSoon) Danger else InkFaint,
                    fontWeight = if (dueSoon) FontWeight.Bold else FontWeight.Normal
                )
            )
            Spacer(Modifier.weight(1f))
            if (!settled) {
                TextButton(onClick = onPay) {
                    Text(stringResource(R.string.debts_log_payment), style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                }
            }
        }
    }
}

@Composable
private fun AddDebtDialog(
    onDismiss: () -> Unit,
    onSave: (String, DebtType, Double, Double, Double, Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(DebtType.OTHER) }
    var total by remember { mutableStateOf("") }
    var remaining by remember { mutableStateOf("") }
    var installment by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    val types = listOf(DebtType.LOAN, DebtType.BNPL, DebtType.CREDIT_CARD, DebtType.OTHER)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text(stringResource(R.string.debts_add_dialog_title), style = H2) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.debts_name_hint)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    types.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { t ->
                                Box(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(RadiusSm))
                                        .background(if (type == t) IndigoSoft else PaperOuter)
                                        .clickable { type = t }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        debtTypeLabel(t),
                                        style = Eyebrow.copy(fontSize = 10.sp, color = if (type == t) Indigo else InkSoft, fontWeight = if (type == t) FontWeight.Bold else FontWeight.Normal)
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = total, onValueChange = { v -> total = sanitizeAmountInput(v); if (remaining.isBlank()) remaining = total },
                    label = { Text(stringResource(R.string.debts_total_hint_fmt, currencyLabel("SAR"))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = remaining, onValueChange = { remaining = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.debts_remaining_hint_fmt, currencyLabel("SAR"))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = installment, onValueChange = { installment = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.debts_installment_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = days, onValueChange = { days = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.debts_days_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = total.toDoubleOrNull()
                val r = remaining.toDoubleOrNull() ?: t
                if (name.isNotBlank() && t != null && t > 0 && r != null) {
                    onSave(name.trim(), type, t, r, installment.toDoubleOrNull() ?: 0.0, days.toIntOrNull())
                }
            }) { Text(stringResource(R.string.debts_add_action), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.debts_cancel), style = Body.copy(color = InkSoft)) }
        }
    )
}

private fun detectUntrackedBnpl(
    txs: List<com.mizan.money.data.TransactionEntity>,
    trackedNames: Set<String>,
    dismissed: Set<String>
): List<Pair<String, Double>> {
    val bnplWords = listOf("tabby", "tamara", "تابي", "تمارا")
    val recent = txs.filter {
        it.type == TxType.EXPENSE && it.merchant != null && it.currency == "SAR" && !it.isSelfTransfer &&
            bnplWords.any { w -> it.merchant!!.lowercase().contains(w) || it.rawSms.lowercase().contains(w) }
    }
    val grouped = recent.groupBy { it.merchant!!.lowercase().trim() }
    return grouped.mapNotNull { (name, list) ->
        if (name in trackedNames || name in dismissed || list.size < 2) return@mapNotNull null
        name.replaceFirstChar { it.uppercase() } to list.map { it.amount }.average()
    }
}
