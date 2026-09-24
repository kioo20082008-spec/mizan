package com.mizan.money.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import com.mizan.money.advisor.BudgetPlan
import com.mizan.money.advisor.Commitment
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.TOTAL_BUDGET
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ============ BUDGET ============
@Composable
fun BudgetScreen(vm: MainViewModel, offset: Int, onOpenCategory: (String) -> Unit = {}) {
    val budgets by vm.budgets.collectAsState()
    val categories by vm.categories.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val monthKey = Dates.monthKey(offset, startDay)
    val txs by vm.transactions.collectAsState()
    val rates by vm.exchangeRates.collectAsState()
    val debts by vm.debts.collectAsState()
    val goals by vm.goals.collectAsState()
    val recurring by vm.recurringItems.collectAsState()
    val contributions by vm.goalContributions.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val categoryIcons by vm.categoryIcons.collectAsState()
    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val summary = remember(txs, offset, startDay, rates) { FinancialAdvisor.summarize(txs, range.first, range.last, rates) }

    val expectedIncome = remember(summary, txs, manualSalary, rates) {
        FinancialAdvisor.planningIncome(summary, txs, manualSalary, rates)
    }
    val plan = remember(txs, expectedIncome, recurring, debts, goals, categories, rates) {
        FinancialAdvisor.plan(txs, expectedIncome ?: 0.0, categories, recurring, debts, goals, rates)
    }
    val savingsThisMonth = remember(contributions, range) {
        contributions.filter { it.timestamp in range.first..range.last }.sumOf { it.amount }
    }

    val monthBudgets = budgets.filter { it.monthKey == monthKey }
    val totalBudget = monthBudgets.firstOrNull { it.category == TOTAL_BUDGET }?.limitAmount ?: 0.0
    val limitByCat = monthBudgets.filter { it.category != TOTAL_BUDGET }.associate { it.category to it.limitAmount }
    val rolloverOnByCat = monthBudgets.filter { it.category != TOTAL_BUDGET }.associate { it.category to it.rolloverEnabled }
    val allocated = categories.sumOf { limitByCat[it] ?: 0.0 }

    var editingTotal by remember { mutableStateOf(false) }
    var editingIncome by remember { mutableStateOf(false) }
    var editingCategory by remember(monthKey) { mutableStateOf<String?>(null) }
    var addingCategory by remember { mutableStateOf(false) }
    var showAllCats by rememberSaveable { mutableStateOf(false) }
    var copiedFeedback by remember(monthKey) { mutableStateOf(false) }
    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(4000)
            copiedFeedback = false
        }
    }

    val prevMonthKey = Dates.monthKey(offset - 1, startDay)
    val prevRange = remember(offset, startDay) { Dates.monthRange(offset - 1, startDay) }
    val prevSpentByCat = remember(txs, offset, startDay, rates) {
        FinancialAdvisor.summarize(txs, prevRange.first, prevRange.last, rates)
            .categoryTotals.associate { it.category to it.amount }
    }
    val rolloverByCat = remember(budgets, prevSpentByCat, monthKey, prevMonthKey, categories) {
        categories.associateWith { c ->
            val cur = budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
            if (cur?.rolloverEnabled != true) 0.0
            else {
                val prev = budgets.firstOrNull { it.monthKey == prevMonthKey && it.category == c }
                if (prev == null) 0.0
                else (prev.limitAmount - (prevSpentByCat[c] ?: 0.0)).coerceAtLeast(0.0)
            }
        }
    }

    val spentByCat = remember(summary) { summary.categoryTotals.associate { it.category to it.amount } }
    val hasPrevBudget = budgets.any { it.monthKey == prevMonthKey }
    val hasCurrentBudget = monthBudgets.isNotEmpty()

    val idleCats = categories.filter { c ->
        (limitByCat[c] ?: 0.0) <= 0.0 &&
            (rolloverByCat[c] ?: 0.0) <= 0.0 &&
            (spentByCat[c] ?: 0.0) <= 0.005
    }
    val activeCats = categories.filterNot { it in idleCats }
    val shownCats = when {
        showAllCats -> categories
        activeCats.isEmpty() -> categories
        else -> activeCats
    }
    val currency = currencyLabel("SAR")

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(monthName(offset, startDay), style = H1)
            Text(stringResource(R.string.budget_subtitle), style = Eyebrow.copy(fontSize = 13.sp))
        }

        if (!hasCurrentBudget && hasPrevBudget) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(Pill))
                        .background(IndigoSoft)
                        .clickable {
                            budgets.filter { it.monthKey == prevMonthKey }
                                .forEach { vm.setBudget(monthKey, it.category, it.limitAmount, it.rolloverEnabled) }
                            copiedFeedback = true
                        }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp), tint = Indigo)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.budget_copy_prev), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
                }
            }
        }
        if (copiedFeedback) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(Pill))
                        .background(Success.copy(alpha = 0.12f))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = Success)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.pl_budget_copied), style = Body.copy(color = Success, fontWeight = FontWeight.Bold))
                }
            }
        }

        item {
            TotalBudgetCard(
                total = totalBudget,
                allocated = allocated,
                currency = currency,
                onEdit = { editingTotal = true }
            )
        }

        item {
            BudgetPlanCard(
                plan = plan,
                income = expectedIncome,
                savingsThisMonth = savingsThisMonth,
                currency = currency,
                onEditIncome = { editingIncome = true }
            )
        }

        if (plan.suggestions.isNotEmpty()) {
            item {
                BudgetSuggestionsCard(
                    suggestions = plan.suggestions,
                    currency = currency,
                    onApply = { cat, amount -> vm.setBudget(monthKey, cat, amount) },
                    onApplyAll = { plan.suggestions.forEach { vm.setBudget(monthKey, it.category, it.amount) } }
                )
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.budget_per_category),
                actionLabel = stringResource(R.string.budget_add_category),
                onAction = { addingCategory = true }
            ) {
                shownCats.forEachIndexed { index, cat ->
                    val spentInCat = spentByCat[cat] ?: 0.0
                    val limit = limitByCat[cat] ?: 0.0
                    val rollover = rolloverByCat[cat] ?: 0.0
                    val effectiveLimit = limit + rollover
                    val pct = if (effectiveLimit > 0) (spentInCat / effectiveLimit).coerceIn(0.0, 1.0).toFloat() else 0f
                    val isOver = effectiveLimit > 0 && spentInCat > effectiveLimit
                    val isLast = index == shownCats.lastIndex && !(showIdleToggle(showAllCats, shownCats, categories, idleCats))
                    // Reading categoryIcons keeps the row icon in sync right
                    // after an icon change (catIcon itself isn't observable).
                    val icon = CategoryIcons.iconForKey(categoryIcons[cat]) ?: catIcon(cat)
                    ListRow(
                        icon = icon,
                        iconTint = catColor(cat),
                        title = categoryDisplay(cat),
                        subtitle = if (effectiveLimit > 0) {
                            stringResource(R.string.pl_budget_spent_of_fmt, fmt(spentInCat), fmt(effectiveLimit)) +
                                (if (rollover > 0.0) "  (+" + fmt(rollover) + ")" else "")
                        } else stringResource(R.string.pl_budget_spent_no_limit_fmt, fmt(spentInCat)),
                        trailing = if (effectiveLimit > 0) stringResource(R.string.budget_percent_fmt, (spentInCat / effectiveLimit * 100).roundToInt()) else null,
                        trailingColor = if (isOver) Danger else Ink,
                        showDivider = !isLast,
                        onClick = { editingCategory = cat },
                        below = if (effectiveLimit > 0) {
                            { PlanningProgressBar(pct, if (isOver) Danger else catColor(cat), height = 5) }
                        } else null
                    )
                }
                if (showIdleToggle(showAllCats, shownCats, categories, idleCats)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { showAllCats = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp), tint = Indigo)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.budget_show_idle_fmt, idleCats.size),
                            style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    if (editingTotal) {
        AmountSheet(
            title = stringResource(R.string.budget_total_label),
            description = null,
            fieldLabel = stringResource(R.string.pl_budget_total_field_fmt, currency),
            initial = totalBudget,
            onDismiss = { editingTotal = false },
            onSave = { v -> vm.setBudget(monthKey, TOTAL_BUDGET, v); editingTotal = false }
        )
    }
    if (editingIncome) {
        AmountSheet(
            title = stringResource(R.string.pl_budget_income_sheet_title),
            description = stringResource(R.string.pl_budget_income_sheet_desc),
            fieldLabel = stringResource(R.string.pl_budget_income_field_fmt, currency),
            initial = manualSalary,
            onDismiss = { editingIncome = false },
            onSave = { v -> vm.setManualSalary(v); editingIncome = false }
        )
    }
    editingCategory?.let { cat ->
        CategoryBudgetSheet(
            category = cat,
            limit = limitByCat[cat] ?: 0.0,
            rolloverOn = rolloverOnByCat[cat] ?: false,
            iconKey = categoryIcons[cat],
            canDelete = cat != "أخرى",
            currency = currency,
            onDismiss = { editingCategory = null },
            onOpenTransactions = { editingCategory = null; onOpenCategory(cat) },
            onSave = { amount, rollover, iconChanged, newIconKey ->
                vm.setBudget(monthKey, cat, amount, rollover)
                if (iconChanged) vm.setCategoryIcon(cat, newIconKey)
                editingCategory = null
            },
            onDelete = {
                vm.deleteCategory(cat)
                editingCategory = null
            }
        )
    }
    if (addingCategory) {
        AddCategorySheet(
            existing = categories,
            onDismiss = { addingCategory = false },
            onAdd = { name, iconKey ->
                vm.addCategory(name, iconKey)
                showAllCats = true
                addingCategory = false
            }
        )
    }
}

private fun showIdleToggle(
    showAllCats: Boolean,
    shownCats: List<String>,
    categories: List<String>,
    idleCats: List<String>
): Boolean = !showAllCats && shownCats !== categories && idleCats.isNotEmpty()

// Big total with a pencil to edit it in a sheet, plus how much of it the
// per-category limits have claimed.
@Composable
private fun TotalBudgetCard(total: Double, allocated: Double, currency: String, onEdit: () -> Unit) {
    SoftCard(Modifier.clip(RoundedCornerShape(RadiusLg)).clickable(onClick = onEdit)) {
        Text(stringResource(R.string.budget_total_label), style = Eyebrow.copy(fontSize = 13.sp))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(fmt(total), style = Display.copy(fontSize = 34.sp, color = if (total > 0) Ink else InkFaint))
                Spacer(Modifier.width(6.dp))
                Text(currency, style = Body.copy(color = InkSoft, fontWeight = FontWeight.Medium), modifier = Modifier.padding(bottom = 6.dp))
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(Pill)).background(IndigoSoft)
            ) {
                Icon(Icons.Default.Edit, stringResource(R.string.pl_budget_edit_total), tint = Indigo, modifier = Modifier.size(20.dp))
            }
        }
        if (total <= 0.0) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pl_budget_total_unset), style = BodyMuted)
        }
        if (total > 0.0 || allocated > 0.0) {
            val over = total > 0.0 && allocated > total + 0.005
            if (total > 0.0) {
                Spacer(Modifier.height(12.dp))
                PlanningProgressBar((allocated / total).toFloat(), if (over) Amber else Indigo, height = 6)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (over) {
                    Icon(Icons.Default.Warning, null, Modifier.size(15.dp), tint = Amber)
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    when {
                        total <= 0.0 -> stringResource(R.string.pl_budget_allocated_no_total_fmt, fmt(allocated))
                        over -> stringResource(R.string.pl_budget_over_allocated_fmt, fmt(allocated), fmt(total), fmt(allocated - total))
                        else -> stringResource(R.string.pl_budget_allocated_fmt, fmt(allocated), fmt(total), fmt(total - allocated))
                    },
                    style = Body.copy(
                        fontSize = 13.sp,
                        color = if (over) Amber else InkSoft,
                        fontWeight = if (over) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
        }
    }
}

// Calm plan card: one stacked bar (fixed expenses / free), then rows.
@Composable
private fun BudgetPlanCard(
    plan: BudgetPlan,
    income: Double?,
    savingsThisMonth: Double,
    currency: String,
    onEditIncome: () -> Unit
) {
    val hasIncome = income != null && income > 0
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.budget_plan_title), style = H2.copy(fontSize = 17.sp))
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.pl_budget_plan_desc), style = Eyebrow.copy(fontSize = 13.sp))
        if (hasIncome) {
            Spacer(Modifier.height(14.dp))
            val committedFrac = (plan.committedTotal / income!!).coerceIn(0.0, 1.0).toFloat()
            Row(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(Pill)).background(Success.copy(alpha = 0.25f))
            ) {
                if (committedFrac > 0f) {
                    Box(Modifier.fillMaxHeight().weight(committedFrac.coerceAtLeast(0.001f)).background(Indigo))
                }
                if (committedFrac < 1f) {
                    Box(Modifier.fillMaxHeight().weight((1f - committedFrac).coerceAtLeast(0.001f)))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PlanRow(
            dot = null,
            label = stringResource(R.string.budget_income_label),
            value = if (hasIncome) fmt(income!!) + " " + currency else stringResource(R.string.pl_budget_add_income),
            valueColor = if (hasIncome) Ink else Indigo,
            trailingIcon = if (hasIncome) Icons.Default.Edit else Icons.Default.Add,
            onClick = onEditIncome
        )
        PlanRow(
            dot = Indigo,
            label = stringResource(R.string.pl_budget_fixed_label),
            value = fmt(plan.committedTotal) + " " + currency,
            valueColor = Ink
        )
        if (hasIncome) {
            PlanRow(
                dot = Success.copy(alpha = 0.5f),
                label = stringResource(R.string.budget_free_label),
                value = fmt(plan.free) + " " + currency,
                valueColor = Success
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        if (plan.fixed.isEmpty() && plan.debts.isEmpty() && plan.savings.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.pl_budget_commit_empty), style = BodyMuted)
        } else {
            CommitmentGroup(
                icon = Icons.Default.Autorenew,
                title = stringResource(R.string.budget_commit_fixed),
                total = plan.fixedTotal,
                currency = currency,
                items = plan.fixed
            )
            CommitmentGroup(
                icon = Icons.Default.CreditCard,
                title = stringResource(R.string.budget_commit_debts),
                total = plan.debtTotal,
                currency = currency,
                items = plan.debts
            )
            CommitmentGroup(
                icon = Icons.Default.Savings,
                title = stringResource(R.string.budget_commit_savings),
                total = plan.savingsTotal,
                currency = currency,
                items = plan.savings
            )
        }
        if (savingsThisMonth != 0.0) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.budget_savings_month_fmt, fmt(savingsThisMonth)),
                style = Body.copy(fontSize = 13.sp, color = Success, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun PlanRow(
    dot: Color?,
    label: String,
    value: String,
    valueColor: Color,
    trailingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusSm))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(Pill)).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = Body.copy(color = InkSoft), modifier = Modifier.weight(1f))
        Text(value, style = NumBold.copy(fontSize = 15.sp, color = valueColor))
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailingIcon, null, Modifier.size(16.dp), tint = Indigo)
        }
    }
}

@Composable
private fun CommitmentGroup(
    icon: ImageVector,
    title: String,
    total: Double,
    currency: String,
    items: List<Commitment>
) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = InkSoft)
        Spacer(Modifier.width(6.dp))
        Text(title, style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), modifier = Modifier.weight(1f))
        Text(fmt(total) + " " + currency, style = NumBold.copy(fontSize = 13.sp))
    }
    items.forEach { c ->
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, top = 3.dp, bottom = 3.dp)) {
            Text(c.label, style = BodyMuted, modifier = Modifier.weight(1f), maxLines = 1)
            Text(fmt(c.amount) + " " + currency, style = BodyMuted)
        }
    }
}

// Single-amount editor used for the total budget and the manual income.
// Saving an empty/zero value clears it (both setters treat <= 0 as "unset").
@Composable
private fun AmountSheet(
    title: String,
    description: String?,
    fieldLabel: String,
    initial: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var input by remember { mutableStateOf(planningEditableAmount(initial)) }
    val parsed = input.toDoubleOrNull()
    val valid = input.isEmpty() || (parsed != null && parsed >= 0)
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(title, style = H2) },
        text = {
            Column {
                if (description != null) {
                    Text(description, style = BodyMuted)
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = sanitizeAmountInput(it) },
                    label = { Text(fieldLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusMd),
                    textStyle = Body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
            }
        },
        confirmButton = {
            PlanningSheetConfirm(stringResource(R.string.pl_save), enabled = valid) {
                onSave(parsed ?: 0.0)
            }
        },
        dismissButton = { PlanningSheetCancel(onDismiss) }
    )
}

// Everything about one category's budget in one sheet. Limit, rollover and
// icon are held locally and written together on save, so toggling rollover
// can never be silently lost by closing an inline editor.
@Composable
private fun CategoryBudgetSheet(
    category: String,
    limit: Double,
    rolloverOn: Boolean,
    iconKey: String?,
    canDelete: Boolean,
    currency: String,
    onDismiss: () -> Unit,
    onOpenTransactions: () -> Unit,
    onSave: (amount: Double, rollover: Boolean, iconChanged: Boolean, iconKey: String?) -> Unit,
    onDelete: () -> Unit
) {
    var input by remember { mutableStateOf(planningEditableAmount(limit)) }
    var rollover by remember { mutableStateOf(rolloverOn) }
    var pickedIcon by remember { mutableStateOf(iconKey) }
    var iconChanged by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val parsed = input.toDoubleOrNull()
    val hasLimit = parsed != null && parsed > 0
    val valid = input.isEmpty() || parsed != null
    val icon = if (iconChanged) CategoryIcons.iconForKey(pickedIcon) ?: Icons.Default.Category
    else CategoryIcons.iconForKey(iconKey) ?: catIcon(category)

    FormSheet(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, catColor(category), catColorSoft(category), size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Text(categoryDisplay(category), style = H2.copy(fontSize = 18.sp), modifier = Modifier.weight(1f))
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.pl_budget_cat_limit_fmt, currency)) },
                    supportingText = { Text(stringResource(R.string.pl_budget_cat_limit_hint), style = Eyebrow.copy(color = InkSoft)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusMd),
                    textStyle = Body.copy(fontSize = 16.sp)
                )
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusMd)).background(PaperOuter)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = hasLimit) { rollover = !rollover }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.budget_rollover_title), style = Body.copy(fontWeight = FontWeight.Medium))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(if (hasLimit) R.string.budget_rollover_desc else R.string.pl_budget_rollover_needs_limit),
                                style = Eyebrow.copy(fontSize = 12.sp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = rollover && hasLimit,
                            onCheckedChange = { rollover = it },
                            enabled = hasLimit,
                            colors = oneUiSwitchColors()
                        )
                    }
                    Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(Line))
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { showPicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.pl_budget_icon_label), style = Body.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
                        Icon(icon, null, Modifier.size(20.dp), tint = catColor(category))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pl_budget_icon_change), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
                    }
                    Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(Line))
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(onClick = onOpenTransactions)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(18.dp), tint = Indigo)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.cat_view_transactions_fmt, categoryDisplay(category)),
                            style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)
                        )
                    }
                }
                if (canDelete) {
                    Spacer(Modifier.height(16.dp))
                    PlanningDeleteButton(stringResource(R.string.pl_budget_delete_cat)) { confirmDelete = true }
                }
            }
        },
        confirmButton = {
            PlanningSheetConfirm(stringResource(R.string.budget_save), enabled = valid) {
                onSave(if (hasLimit) parsed!! else 0.0, rollover && hasLimit, iconChanged, pickedIcon)
            }
        },
        dismissButton = { PlanningSheetCancel(onDismiss) }
    )

    if (showPicker) {
        CategoryIconPickerDialog(
            selectedKey = if (iconChanged) pickedIcon else iconKey,
            onDismiss = { showPicker = false },
            onPick = { key -> pickedIcon = key; iconChanged = true; showPicker = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text(stringResource(R.string.pl_budget_delete_cat_title_fmt, categoryDisplay(category)), style = H2) },
            text = { Text(stringResource(R.string.pl_budget_delete_cat_desc), style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.goals_delete), style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.pl_cancel), style = Body.copy(color = InkSoft))
                }
            }
        )
    }
}

@Composable
private fun AddCategorySheet(
    existing: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty() && trimmed !in existing

    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pl_budget_add_cat_title), style = H2) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(Pill)).background(IndigoSoft)
                ) {
                    Icon(
                        CategoryIcons.iconForKey(iconKey) ?: Icons.Default.Category,
                        stringResource(R.string.budget_pick_icon), tint = Indigo, modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.budget_category_name_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusMd),
                    textStyle = Body
                )
            }
        },
        confirmButton = {
            PlanningSheetConfirm(stringResource(R.string.budget_add_action), enabled = valid) {
                onAdd(trimmed, iconKey)
            }
        },
        dismissButton = { PlanningSheetCancel(onDismiss) }
    )
    if (showPicker) {
        CategoryIconPickerDialog(
            selectedKey = iconKey,
            onDismiss = { showPicker = false },
            onPick = { key -> iconKey = key; showPicker = false }
        )
    }
}

@Composable
private fun BudgetSuggestionsCard(
    suggestions: List<com.mizan.money.advisor.BudgetSuggestion>,
    currency: String,
    onApply: (String, Double) -> Unit,
    onApplyAll: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SoftCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(Icons.Default.AutoAwesome, Indigo, IndigoSoft, size = 36.dp, iconSize = 16.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.budget_suggestions_title), style = H2)
                Text(stringResource(R.string.budget_suggestions_desc), style = Eyebrow.copy(fontSize = 12.sp))
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand),
                tint = InkFaint,
                modifier = Modifier.size(20.dp)
            )
        }
        SuggestionsBody(expanded, suggestions, currency, onApply, onApplyAll)
    }
}

// Own composable so AnimatedVisibility resolves to the plain (non-scoped)
// overload rather than ColumnScope's.
@Composable
private fun SuggestionsBody(
    expanded: Boolean,
    suggestions: List<com.mizan.money.advisor.BudgetSuggestion>,
    currency: String,
    onApply: (String, Double) -> Unit,
    onApplyAll: () -> Unit
) {
    AnimatedVisibility(visible = expanded) {
        Column {
            Spacer(Modifier.height(12.dp))
            suggestions.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(catIcon(s.category), catColor(s.category), catColorSoft(s.category), size = 34.dp, iconSize = 16.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(categoryDisplay(s.category), style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
                        Text(fmt(s.amount) + " " + currency, style = Eyebrow.copy(fontSize = 12.sp))
                    }
                    PlanningSoftPill(stringResource(R.string.budget_apply_suggestion), onClick = { onApply(s.category, s.amount) })
                }
            }
            Spacer(Modifier.height(10.dp))
            PlanningPillButton(
                text = stringResource(R.string.budget_apply_all),
                onClick = onApplyAll,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CategoryIconPickerDialog(
    selectedKey: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit
) {
    FormSheet(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.budget_pick_icon), style = H2, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.pl_close), tint = InkSoft)
                }
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())
            ) {
                val options = buildList {
                    add(CategoryIcons.Option("__default__", Icons.Default.Category))
                    addAll(CategoryIcons.options)
                }
                options.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { opt ->
                            val key = opt.key.takeIf { it != "__default__" }
                            val selected = key == selectedKey
                            Box(
                                Modifier.weight(1f).aspectRatio(1f)
                                    .clip(RoundedCornerShape(Pill))
                                    .background(if (selected) Ink900 else PaperOuter)
                                    .clickable { onPick(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    opt.icon, null, Modifier.size(20.dp),
                                    tint = if (selected) Lime else InkSoft
                                )
                            }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(Pill)) {
                Text(stringResource(R.string.pl_close), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
            }
        }
    )
}
