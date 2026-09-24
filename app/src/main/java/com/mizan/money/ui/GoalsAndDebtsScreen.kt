package com.mizan.money.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.data.DebtEntity
import com.mizan.money.data.DebtType
import com.mizan.money.data.GoalEntity
import com.mizan.money.data.TxType
import kotlin.math.roundToInt

// ============ PLANNING ============
@Composable
fun PlanningScreen(vm: MainViewModel, offset: Int, onOpenCategory: (String) -> Unit = {}) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 10.dp)) {
            TabSwitcher(
                listOf(
                    stringResource(R.string.planning_tab_budget),
                    stringResource(R.string.planning_tab_goals),
                    stringResource(R.string.planning_tab_commitments),
                ),
                subTab
            ) { subTab = it }
        }
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = subTab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState > initialState
                    val drift = if (forward) 1 else -1
                    (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { h -> drift * h / 24 } +
                        fadeIn(tween(320, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { h -> -drift * h / 24 } +
                                fadeOut(tween(220, easing = FastOutSlowInEasing))
                        )
                },
                label = "planningTab"
            ) { s ->
                when (s) {
                    0 -> BudgetScreen(vm, offset, onOpenCategory)
                    1 -> GoalsSection(vm)
                    else -> CommitmentsSection(vm)
                }
            }
        }
    }
}

// Bills and debts are set up once and rarely touched, so they share one
// scrolling "commitments" page with a section each.
@Composable
private fun CommitmentsSection(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        RemindersBlock(vm)
        DebtsBlock(vm)
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

@Composable
private fun GoalsSection(vm: MainViewModel) {
    val goals by vm.goals.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var suggestedName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    // Goal + whether the sheet opens in withdraw mode.
    var amountFor by remember { mutableStateOf<Pair<GoalEntity, Boolean>?>(null) }
    var confirmingDelete by remember { mutableStateOf<GoalEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PlanningSectionHeader(
                title = stringResource(R.string.goals_title),
                subtitle = stringResource(R.string.goals_subtitle),
                actionLabel = stringResource(R.string.goals_add),
                actionIcon = Icons.Default.Add,
                onAction = { suggestedName = ""; showAdd = true }
            )
        }
        if (goals.isEmpty()) {
            item {
                GoalsEmptyState(onCreate = { name -> suggestedName = name; showAdd = true })
            }
        } else {
            items(goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    onOpen = { editing = goal },
                    onDeposit = { amountFor = goal to false },
                    onWithdraw = { amountFor = goal to true }
                )
            }
        }
    }

    if (showAdd) {
        GoalEditorDialog(
            initial = null,
            initialName = suggestedName,
            onDismiss = { showAdd = false },
            onSave = { name, amount, targetDate, monthly ->
                vm.addGoal(name, amount, targetDate, monthly); showAdd = false
            }
        )
    }
    editing?.let { goal ->
        GoalEditorDialog(
            initial = goal,
            onDismiss = { editing = null },
            onDelete = { editing = null; confirmingDelete = goal },
            onSave = { name, amount, targetDate, monthly ->
                vm.editGoal(goal, name, amount, targetDate, monthly); editing = null
            }
        )
    }
    amountFor?.let { (goal, withdraw) ->
        GoalAmountDialog(
            goal = goal,
            initialWithdraw = withdraw,
            onDismiss = { amountFor = null },
            onDeposit = { amount -> vm.contributeToGoal(goal, amount); amountFor = null },
            onWithdraw = { amount -> vm.withdrawFromGoal(goal, amount); amountFor = null }
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
        IconBadge(Icons.Default.Savings, Indigo, IndigoSoft, size = 52.dp, iconSize = 24.dp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.goals_empty_title), style = H2)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.goals_empty_desc), style = BodyMuted)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.goals_empty_suggestions), style = Eyebrow)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.goals_suggestion_emergency,
                R.string.goals_suggestion_trip,
                R.string.goals_suggestion_car,
                R.string.goals_suggestion_phone,
            ).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { res ->
                        val label = stringResource(res)
                        PlanningChip(label = label, selected = false, modifier = Modifier.weight(1f)) { onCreate(label) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// The whole card opens the edit sheet (where delete also lives); the only
// inline actions are deposit and — whenever there is a balance, including
// after the goal is reached — withdraw.
@Composable
private fun GoalCard(
    goal: GoalEntity,
    onOpen: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit
) {
    val currency = currencyLabel("SAR")
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
    val pct = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat() else 0f
    val reached = goal.targetAmount > 0 && goal.currentAmount >= goal.targetAmount
    val deadline = goal.targetDate
    val monthsLeft = monthsBetweenNow(deadline)

    SoftCard(Modifier.clip(RoundedCornerShape(RadiusLg)).clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Savings, if (reached) Success else Indigo, if (reached) Success.copy(alpha = 0.12f) else IndigoSoft, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = H2.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val untilSuffix = deadline?.let { stringResource(R.string.goals_target_until_fmt, Dates.dayLabel(it)) } ?: ""
                Text(
                    stringResource(
                        R.string.goals_amount_fmt,
                        fmt(goal.currentAmount),
                        fmt(goal.targetAmount),
                        untilSuffix
                    ),
                    style = Eyebrow.copy(fontSize = 13.sp)
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.goals_edit), Modifier.size(20.dp), tint = InkFaint)
        }
        Spacer(Modifier.height(12.dp))
        PlanningProgressBar(pct, if (reached) Success else Indigo, height = 8)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (reached) stringResource(R.string.goals_reached)
                else stringResource(R.string.goals_progress_fmt, (pct * 100).roundToInt()),
                style = Eyebrow.copy(fontSize = 12.sp, color = if (reached) Success else InkSoft, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            if (!reached) {
                Text(
                    stringResource(R.string.goals_remaining_fmt, fmt(remaining), currency),
                    style = Eyebrow.copy(fontSize = 12.sp, color = InkSoft, fontWeight = FontWeight.Bold)
                )
            }
        }
        if (!reached) {
            Spacer(Modifier.height(6.dp))
            val overdue = deadline != null && (monthsLeft == null || monthsLeft <= 0)
            val plan = when {
                goal.monthlyAmount > 0 -> stringResource(R.string.goals_from_budget_fmt, fmt(goal.monthlyAmount))
                deadline == null -> stringResource(R.string.goals_no_deadline_hint)
                overdue -> stringResource(R.string.goals_overdue_hint)
                else -> {
                    val m = monthsLeft ?: 1
                    stringResource(
                        R.string.goals_plan_fmt,
                        fmt(remaining / m),
                        currency,
                        Dates.dayLabel(deadline)
                    )
                }
            }
            Text(
                plan,
                style = Eyebrow.copy(
                    fontSize = 12.sp,
                    color = if (goal.monthlyAmount > 0) Success else if (overdue) Amber else InkSoft,
                    fontWeight = if (goal.monthlyAmount > 0 || overdue) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlanningPillButton(
                text = stringResource(R.string.goals_deposit),
                onClick = onDeposit,
                icon = Icons.Default.Add,
                compact = true
            )
            if (goal.currentAmount > 0.0) {
                Spacer(Modifier.width(8.dp))
                PlanningSoftPill(text = stringResource(R.string.goals_withdraw), onClick = onWithdraw)
            }
        }
    }
}

@Composable
private fun GoalEditorDialog(
    initial: GoalEntity?,
    initialName: String = "",
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (String, Double, Long?, Double) -> Unit
) {
    val currency = currencyLabel("SAR")
    var name by remember { mutableStateOf(initial?.name ?: initialName) }
    var amount by remember { mutableStateOf(initial?.let { planningEditableAmount(it.targetAmount) } ?: "") }
    var monthly by remember { mutableStateOf(initial?.let { planningEditableAmount(it.monthlyAmount) } ?: "") }
    var deadline by remember { mutableStateOf(initial?.targetDate) }
    // Which deadline chip is active: 3/6/12 months, 0 = none, -1 = picked date.
    var choice by remember { mutableIntStateOf(if (initial?.targetDate == null) 0 else -1) }
    var showPicker by remember { mutableStateOf(false) }

    val targetAmount = amount.toDoubleOrNull()
    val amountInvalid = amount.isNotEmpty() && (targetAmount == null || targetAmount <= 0)
    val valid = name.isNotBlank() && targetAmount != null && targetAmount > 0
    val editing = initial != null
    val saved = initial?.currentAmount ?: 0.0
    val monthsLeft = monthsBetweenNow(deadline)?.coerceAtLeast(1)
    // Live "you need X a month" preview; also what gets saved as the monthly
    // commitment when the monthly field is left empty.
    val needed: Double? = if (deadline != null && targetAmount != null && targetAmount > saved && monthsLeft != null) {
        (targetAmount - saved) / monthsLeft
    } else null
    val typedMonthly = monthly.toDoubleOrNull()?.takeIf { it > 0 }

    FormSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
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
                    shape = RoundedCornerShape(RadiusMd), textStyle = Body
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
                    shape = RoundedCornerShape(RadiusMd), textStyle = Body
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.goals_deadline_label), style = Eyebrow.copy(fontSize = 13.sp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 6, 12).forEach { m ->
                        PlanningChip(
                            label = stringResource(R.string.goals_months_quick_fmt, m),
                            selected = choice == m,
                            modifier = Modifier.weight(1f)
                        ) { choice = m; deadline = monthsToTargetDate(m) }
                    }
                    PlanningChip(
                        label = stringResource(R.string.goals_no_deadline_chip),
                        selected = choice == 0,
                        modifier = Modifier.weight(1.4f)
                    ) { choice = 0; deadline = null }
                }
                Spacer(Modifier.height(8.dp))
                PlanningChip(
                    label = if (choice == -1 && deadline != null) stringResource(R.string.goals_deadline_fmt, Dates.dayLabel(deadline!!))
                    else stringResource(R.string.pl_goal_pick_date),
                    selected = choice == -1,
                    modifier = Modifier.fillMaxWidth()
                ) { showPicker = true }
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusMd)).background(PaperOuter)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (needed != null) {
                        Text(
                            stringResource(R.string.pl_goal_need_monthly_fmt, fmt(needed), currency),
                            style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        deadline?.let { stringResource(R.string.goals_deadline_fmt, Dates.dayLabel(it)) }
                            ?: stringResource(R.string.goals_deadline_none),
                        style = Eyebrow.copy(fontSize = 12.sp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = monthly, onValueChange = { monthly = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.goals_monthly_saving_label, currency)) },
                    supportingText = {
                        Text(
                            if (needed != null) stringResource(R.string.pl_goal_monthly_auto_fmt, fmt(needed))
                            else stringResource(R.string.goals_monthly_saving_hint),
                            style = Eyebrow.copy(color = InkSoft)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusMd), textStyle = Body
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    PlanningDeleteButton(stringResource(R.string.pl_goal_delete), onDelete)
                }
            }
        },
        confirmButton = {
            PlanningSheetConfirm(
                stringResource(if (editing) R.string.goals_edit_action else R.string.goals_add_action),
                enabled = valid
            ) {
                val t = targetAmount ?: return@PlanningSheetConfirm
                // Empty monthly field → save the computed amount (0 when there
                // is no deadline to compute from).
                onSave(name.trim(), t, deadline, typedMonthly ?: needed ?: 0.0)
            }
        },
        dismissButton = { PlanningSheetCancel(onDismiss) }
    )

    if (showPicker) {
        PlanningDatePickerDialog(
            initial = deadline,
            onDismiss = { showPicker = false },
            onPick = { deadline = it; choice = -1 }
        )
    }
}

@Composable
private fun GoalAmountDialog(
    goal: GoalEntity,
    initialWithdraw: Boolean,
    onDismiss: () -> Unit,
    onDeposit: (Double) -> Unit,
    onWithdraw: (Double) -> Unit
) {
    val currency = currencyLabel("SAR")
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
    var amount by remember { mutableStateOf("") }
    var withdraw by remember { mutableStateOf(initialWithdraw && goal.currentAmount > 0.0) }
    val parsed = amount.toDoubleOrNull()
    val exceedsBalance = withdraw && parsed != null && parsed > goal.currentAmount
    val invalid = amount.isNotEmpty() && (parsed == null || parsed <= 0 || exceedsBalance)
    val valid = parsed != null && parsed > 0 && !exceedsBalance

    val quick = mutableListOf<Pair<String, Double>>()
    listOf(100.0, 500.0, 1000.0).forEach {
        quick.add(stringResource(R.string.goals_quick_add_fmt, fmt(it)) to it)
    }
    if (!withdraw && remaining > 0) {
        quick.add(stringResource(R.string.goals_quick_remaining) to remaining)
    }
    if (withdraw && goal.currentAmount > 0) {
        quick.add(stringResource(R.string.goals_quick_remaining) to goal.currentAmount)
    }

    FormSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
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
                if (goal.currentAmount > 0.0) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Pill)).background(PaperOuter).padding(4.dp)
                    ) {
                        ModeTab(stringResource(R.string.goals_deposit), !withdraw, Modifier.weight(1f)) { withdraw = false }
                        ModeTab(stringResource(R.string.goals_withdraw), withdraw, Modifier.weight(1f)) { withdraw = true }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    stringResource(R.string.goals_current_balance_fmt, fmt(goal.currentAmount), currency),
                    style = Eyebrow.copy(fontSize = 13.sp)
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
                    shape = RoundedCornerShape(RadiusMd), textStyle = Body
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, value) ->
                                PlanningChip(label = label, selected = false, modifier = Modifier.weight(1f)) {
                                    amount = planningEditableAmount(value)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            PlanningSheetConfirm(
                stringResource(if (withdraw) R.string.goals_withdraw_action else R.string.goals_save),
                enabled = valid
            ) {
                val v = parsed ?: return@PlanningSheetConfirm
                if (withdraw) onWithdraw(v) else onDeposit(v)
            }
        },
        dismissButton = { PlanningSheetCancel(onDismiss) }
    )
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(Pill)).background(if (selected) White else PaperOuter)
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = Body.copy(fontSize = 14.sp, color = if (selected) Ink else InkSoft, fontWeight = FontWeight.Bold))
    }
}

// ============ DEBTS ============
@Composable
private fun DebtsBlock(vm: MainViewModel) {
    val debts by vm.debts.collectAsState()
    val txs by vm.transactions.collectAsState()
    val dismissed by vm.dismissedBnplSuggestions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DebtEntity?>(null) }
    var payingOn by remember { mutableStateOf<DebtEntity?>(null) }
    var confirmingDelete by remember { mutableStateOf<DebtEntity?>(null) }

    val suggestion = remember(txs, debts, dismissed) {
        detectUntrackedBnpl(txs, debts.map { it.name.lowercase().trim() }.toSet(), dismissed)
            .firstOrNull()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanningSectionHeader(
            title = stringResource(R.string.pl_section_debts),
            actionLabel = if (debts.isEmpty()) null else stringResource(R.string.pl_add),
            actionIcon = Icons.Default.Add,
            onAction = { showAdd = true }
        )
        if (suggestion != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusLg)).background(IndigoSoft)
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Default.Lightbulb, Indigo, White, size = 36.dp, iconSize = 16.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.debts_bnpl_suggest_fmt, suggestion.first),
                        style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )
                    Text(
                        stringResource(R.string.debts_bnpl_track_hint_fmt, fmt(suggestion.second)),
                        style = Eyebrow.copy(fontSize = 13.sp)
                    )
                }
                TextButton(onClick = {
                    vm.addDebt(suggestion.first, DebtType.BNPL, suggestion.second * 4, suggestion.second * 4, suggestion.second, 30)
                }, shape = RoundedCornerShape(Pill)) {
                    Text(stringResource(R.string.debts_bnpl_track), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold, fontSize = 14.sp))
                }
                TextButton(onClick = { vm.dismissBnplSuggestion(suggestion.first) }, shape = RoundedCornerShape(Pill)) {
                    Text(stringResource(R.string.debts_bnpl_dismiss), style = Body.copy(color = InkSoft, fontSize = 14.sp))
                }
            }
        }
        if (debts.isEmpty()) {
            PlanningEmptyCard(
                icon = Icons.Default.CreditCard,
                text = stringResource(R.string.pl_debts_empty_desc),
                actionLabel = stringResource(R.string.pl_debt_add),
                actionIcon = Icons.Default.Add,
                onAction = { showAdd = true }
            )
        } else {
            DebtSummaryCard(debts)
            debts.sortedByDescending { it.remainingAmount }.forEach { debt ->
                key(debt.id) {
                    DebtCard(debt = debt, onPay = { payingOn = debt }, onEdit = { editing = debt }, onDelete = { confirmingDelete = debt })
                }
            }
        }
    }

    if (showAdd) {
        DebtEditorDialog(initial = null, onDismiss = { showAdd = false }, onSave = { vm.saveDebt(it); showAdd = false })
    }
    editing?.let { debt ->
        DebtEditorDialog(initial = debt, onDismiss = { editing = null }, onSave = { vm.saveDebt(it); editing = null })
    }
    payingOn?.let { debt ->
        DebtPaymentDialog(
            debt = debt,
            onDismiss = { payingOn = null },
            onSave = { amount, date -> vm.logDebtPayment(debt, amount, date); payingOn = null }
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

private fun debtTypeIcon(type: DebtType): ImageVector = when (type) {
    DebtType.LOAN -> Icons.Default.AccountBalance
    DebtType.BNPL -> Icons.Default.ShoppingBag
    DebtType.CREDIT_CARD -> Icons.Default.CreditCard
    DebtType.OTHER -> Icons.Default.Payments
}

@Composable
private fun DebtSummaryCard(debts: List<DebtEntity>) {
    val currency = currencyLabel("SAR")
    val active = debts.filter { it.remainingAmount > 0.0 }
    val totalRemaining = active.sumOf { it.remainingAmount }
    val monthly = active.filter { it.installmentAmount > 0.0 }.sumOf { it.installmentAmount }
    SoftCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            SummaryStat(stringResource(R.string.debts_summary_remaining), fmt(totalRemaining) + " " + currency, Modifier.weight(1.2f))
            SummaryStat(stringResource(R.string.debts_summary_monthly), fmt(monthly) + " " + currency, Modifier.weight(1.2f))
            SummaryStat(stringResource(R.string.pl_debt_active_label), localDigits(active.size), Modifier.weight(0.7f))
        }
        if (monthly > 0) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Autorenew, null, Modifier.size(14.dp), tint = Indigo)
                Spacer(Modifier.width(5.dp))
                Text(
                    stringResource(R.string.debts_from_budget_fmt, fmt(monthly)),
                    style = Eyebrow.copy(fontSize = 12.sp, color = Indigo, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = Eyebrow.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(value, style = NumBold.copy(fontSize = 15.sp), maxLines = 1)
    }
}

// Collapsed: name, remaining, progress and a one-tap "pay" pill. Tapping the
// header expands details plus edit/delete.
@Composable
private fun DebtCard(debt: DebtEntity, onPay: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val currency = currencyLabel("SAR")
    val paid = (debt.totalAmount - debt.remainingAmount).coerceAtLeast(0.0)
    val pct = if (debt.totalAmount > 0) (paid / debt.totalAmount).coerceIn(0.0, 1.0).toFloat() else 0f
    val dueSoon = debt.nextDueDate != null && debt.nextDueDate - System.currentTimeMillis() in 0..(3L * 86_400_000L)
    val settled = debt.remainingAmount <= 0.0
    var expanded by remember { mutableStateOf(false) }
    val tint = if (settled) Success else Indigo
    SoftCard {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(debtTypeIcon(debt.type), tint, tint.copy(alpha = 0.12f), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(debt.name, style = H2.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (dueSoon && !settled) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(Pill)).background(Amber))
                    }
                }
                Text(debtTypeLabel(debt.type), style = Eyebrow.copy(fontSize = 12.sp))
            }
            Text(
                if (settled) stringResource(R.string.debts_settled)
                else fmt(debt.remainingAmount) + " " + currency,
                style = NumBold.copy(fontSize = 14.sp, color = if (settled) Success else Ink)
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                stringResource(if (expanded) R.string.debts_details_hide else R.string.debts_details_show),
                Modifier.size(20.dp), tint = InkFaint
            )
        }
        Spacer(Modifier.height(12.dp))
        PlanningProgressBar(pct, tint, height = 8)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val termText = if (debt.termMonths > 0)
                stringResource(R.string.debts_months_progress_fmt, debt.paidMonths, debt.termMonths)
            else stringResource(R.string.pl_debt_no_term)
            val dueSuffix = if (settled) "" else
                debt.nextDueDate?.let { stringResource(R.string.debts_installment_suffix_fmt, Dates.dayLabel(it)) } ?: ""
            Text(
                termText + dueSuffix,
                style = Eyebrow.copy(
                    fontSize = 12.sp,
                    color = if (dueSoon && !settled) Amber else InkSoft,
                    fontWeight = if (dueSoon && !settled) FontWeight.Bold else FontWeight.Normal
                ),
                modifier = Modifier.weight(1f)
            )
            if (!settled) {
                Spacer(Modifier.width(8.dp))
                PlanningPillButton(text = stringResource(R.string.pl_debt_pay), onClick = onPay, compact = true)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (settled) stringResource(R.string.debts_settled)
                else stringResource(
                    R.string.debts_remaining_fmt,
                    fmt(debt.remainingAmount),
                    fmt(debt.totalAmount)
                ),
                style = Eyebrow.copy(fontSize = 13.sp)
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanningSoftPill(stringResource(R.string.debts_edit), onEdit)
                PlanningSoftPill(stringResource(R.string.debts_delete), onDelete, color = Danger, bg = Danger.copy(alpha = 0.08f))
            }
        }
    }
}

@Composable
private fun DebtEditorDialog(
    initial: DebtEntity?,
    onDismiss: () -> Unit,
    onSave: (DebtEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var nameTouched by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(initial?.type ?: DebtType.OTHER) }
    var total by remember { mutableStateOf(initial?.totalAmount?.let { planningEditableAmount(it) } ?: "") }
    var termMonths by remember { mutableStateOf(initial?.termMonths?.takeIf { it > 0 }?.toString() ?: "") }
    var paidMonths by remember { mutableStateOf(initial?.paidMonths?.takeIf { it > 0 }?.toString() ?: "") }
    var dueDate by remember { mutableStateOf(initial?.nextDueDate) }
    val types = listOf(DebtType.LOAN, DebtType.BNPL, DebtType.CREDIT_CARD, DebtType.OTHER)

    val totalVal = total.toDoubleOrNull()
    val monthsVal = termMonths.toIntOrNull() ?: 0
    val paidRaw = paidMonths.toIntOrNull() ?: 0
    val paidVal = if (monthsVal > 0) paidRaw.coerceIn(0, monthsVal) else paidRaw
    val installment = if (monthsVal > 0 && totalVal != null) totalVal / monthsVal else (initial?.installmentAmount ?: 0.0)
    val remaining = when {
        monthsVal > 0 && totalVal != null -> (totalVal - installment * paidVal).coerceIn(0.0, totalVal)
        initial != null && totalVal != null -> initial.remainingAmount.coerceAtMost(totalVal)
        else -> totalVal ?: 0.0
    }

    val nameError = nameTouched && name.isBlank()
    val totalError = total.isNotEmpty() && (totalVal == null || totalVal <= 0)
    val paidError = monthsVal > 0 && paidRaw > monthsVal
    val valid = name.isNotBlank() && totalVal != null && totalVal > 0 && !paidError

    FormSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
        title = { Text(stringResource(if (initial == null) R.string.debts_add_dialog_title else R.string.debts_edit_dialog_title), style = H2) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; nameTouched = true },
                    label = { Text(stringResource(R.string.debts_name_hint)) },
                    isError = nameError,
                    supportingText = {
                        if (nameError) Text(stringResource(R.string.pl_debt_name_error), style = Eyebrow.copy(color = Danger))
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusMd), textStyle = Body
                )
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { t ->
                                PlanningChip(label = debtTypeLabel(t), selected = type == t, modifier = Modifier.weight(1f)) { type = t }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = total, onValueChange = { total = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.debts_total_hint_fmt, currencyLabel("SAR"))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = totalError,
                    supportingText = {
                        if (totalError) Text(stringResource(R.string.goals_amount_error), style = Eyebrow.copy(color = Danger))
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusMd), textStyle = Body
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = termMonths, onValueChange = { termMonths = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text(stringResource(R.string.debts_months_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(RadiusMd), textStyle = Body
                    )
                    OutlinedTextField(
                        value = paidMonths, onValueChange = { paidMonths = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text(stringResource(R.string.debts_paid_months_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = paidError,
                        supportingText = {
                            if (paidError) Text(stringResource(R.string.pl_debt_paid_error), style = Eyebrow.copy(color = Danger))
                        },
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(RadiusMd), textStyle = Body
                    )
                }
                if (monthsVal > 0 && totalVal != null && totalVal > 0) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusMd)).background(PaperOuter).padding(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.debts_installment_preview_fmt, fmt(installment)),
                            style = Body.copy(fontSize = 14.sp, color = Indigo, fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.debts_remaining_preview_fmt, fmt(remaining)),
                            style = Eyebrow.copy(fontSize = 13.sp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                PlanningDateField(
                    label = stringResource(R.string.debts_due_date_hint),
                    value = dueDate,
                    emptyText = stringResource(R.string.pl_debt_date_unset),
                    onPick = { dueDate = it }
                )
            }
        },
        confirmButton = {
            PlanningSheetConfirm(
                stringResource(if (initial == null) R.string.debts_add_action else R.string.debts_save_action),
                enabled = valid
            ) {
                val t = totalVal ?: return@PlanningSheetConfirm
                if (!valid) return@PlanningSheetConfirm
                val base = initial ?: DebtEntity(name = "", totalAmount = 0.0, remainingAmount = 0.0)
                onSave(
                    base.copy(
                        name = name.trim(),
                        type = type,
                        totalAmount = t,
                        remainingAmount = remaining,
                        installmentAmount = installment,
                        termMonths = monthsVal,
                        paidMonths = paidVal,
                        nextDueDate = dueDate
                    )
                )
            }
        },
        dismissButton = { PlanningSheetCancel(onDismiss) }
    )
}

@Composable
private fun DebtPaymentDialog(
    debt: DebtEntity,
    onDismiss: () -> Unit,
    onSave: (Double, Long) -> Unit
) {
    val remaining = debt.remainingAmount.coerceAtLeast(0.0)
    val defaultAmount = if (debt.installmentAmount > 0.0) debt.installmentAmount.coerceAtMost(remaining) else 0.0
    var amount by remember { mutableStateOf(planningEditableAmount(defaultAmount)) }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    val parsed = amount.toDoubleOrNull()
    // Small tolerance so a rounded installment equal to the remainder passes.
    val exceeds = parsed != null && parsed > remaining + 0.005
    val invalid = amount.isNotEmpty() && (parsed == null || parsed <= 0 || exceeds)
    val valid = parsed != null && parsed > 0 && !exceeds
    FormSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
        title = { Text(stringResource(R.string.debts_pay_dialog_title_fmt, debt.name), style = H2) },
        text = {
            Column {
                Text(
                    stringResource(R.string.debts_remaining_fmt, fmt(remaining), fmt(debt.totalAmount)),
                    style = Eyebrow.copy(fontSize = 13.sp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.debts_pay_amount_fmt, currencyLabel("SAR"))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = invalid,
                    supportingText = {
                        if (invalid) {
                            Text(
                                if (exceeds) stringResource(R.string.pl_debt_pay_exceeds_fmt, fmt(remaining))
                                else stringResource(R.string.goals_amount_error),
                                style = Eyebrow.copy(color = Danger)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusMd), textStyle = Body
                )
                Spacer(Modifier.height(4.dp))
                PlanningChip(
                    label = stringResource(R.string.goals_quick_remaining),
                    selected = parsed != null && kotlin.math.abs(parsed - remaining) < 0.005
                ) { amount = planningEditableAmount(remaining) }
                Spacer(Modifier.height(14.dp))
                PlanningDateField(
                    label = stringResource(R.string.debts_payment_date_hint),
                    value = date,
                    emptyText = stringResource(R.string.pl_debt_date_unset),
                    onPick = { date = it }
                )
            }
        },
        confirmButton = {
            PlanningSheetConfirm(stringResource(R.string.debts_log_payment), enabled = valid) {
                val a = parsed ?: return@PlanningSheetConfirm
                if (valid) onSave(a.coerceAtMost(remaining), date)
            }
        },
        dismissButton = { PlanningSheetCancel(onDismiss) }
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
