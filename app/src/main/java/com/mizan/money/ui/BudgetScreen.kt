package com.mizan.money.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import com.mizan.money.advisor.BudgetPlan
import com.mizan.money.advisor.Commitment
import com.mizan.money.advisor.CommitmentKind
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.TOTAL_BUDGET
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

    fun Double.toBudgetInput() = if (this % 1.0 == 0.0) toInt().toString() else toString()

    var totalInput by remember(monthKey) {
        mutableStateOf(
            budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }
                ?.limitAmount?.toBudgetInput() ?: ""
        )
    }
    var editingTotal by remember(monthKey) { mutableStateOf(false) }
    var catInputs by remember(monthKey) {
        mutableStateOf(
            categories.associateWith { c ->
                budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                    ?.limitAmount?.toBudgetInput() ?: ""
            }
        )
    }
    var editingCategory by remember(monthKey) { mutableStateOf<String?>(null) }
    var addingCategory by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var newCategoryIcon by remember { mutableStateOf<String?>(null) }
    var showIconPicker by remember { mutableStateOf(false) }
    var iconPickerForEdit by remember { mutableStateOf<String?>(null) }
    var showAllCats by rememberSaveable { mutableStateOf(false) }
    var catRollover by remember(monthKey) {
        mutableStateOf(
            categories.associateWith { c ->
                budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                    ?.rolloverEnabled ?: false
            }
        )
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

    LaunchedEffect(budgets, monthKey, categories) {
        if (!editingTotal) {
            totalInput = budgets.firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }
                ?.limitAmount?.toBudgetInput() ?: ""
        }
        catInputs = categories.associateWith { c ->
            if (c == editingCategory) catInputs[c] ?: ""
            else budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                ?.limitAmount?.toBudgetInput() ?: ""
        }
        catRollover = categories.associateWith { c ->
            if (c == editingCategory) catRollover[c] ?: false
            else budgets.firstOrNull { it.monthKey == monthKey && it.category == c }
                ?.rolloverEnabled ?: false
        }
    }

    val spentByCat = remember(summary) { summary.categoryTotals.associate { it.category to it.amount } }
    val hasPrevBudget = budgets.any { it.monthKey == prevMonthKey }
    val hasCurrentBudget = budgets.any { it.monthKey == monthKey }

    val idleCats = categories.filter { c ->
        c != editingCategory &&
            (catInputs[c]?.toDoubleOrNull() ?: 0.0) <= 0.0 &&
            (rolloverByCat[c] ?: 0.0) <= 0.0 &&
            (spentByCat[c] ?: 0.0) <= 0.005
    }
    val activeCats = categories.filterNot { it in idleCats }
    val shownCats = when {
        showAllCats -> categories
        activeCats.isEmpty() -> categories
        else -> activeCats
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(stringResource(R.string.budget_title_fmt, monthName(offset, startDay)), style = H1)
            Text(stringResource(R.string.budget_subtitle), style = Eyebrow)
        }

        if (!hasCurrentBudget && hasPrevBudget) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(IndigoSoft)
                        .clickable {
                            budgets.filter { it.monthKey == prevMonthKey }
                                .forEach { vm.setBudget(monthKey, it.category, it.limitAmount, it.rolloverEnabled) }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp), tint = Indigo)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.budget_copy_prev), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
                }
            }
        }

        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusXl))
                    .background(Ink900)
                    .padding(22.dp)
            ) {
                Column {
                    Text(stringResource(R.string.budget_total_label), style = Eyebrow.copy(color = OnInkSoft))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(FinancialAdvisor.fmt(totalInput.toDoubleOrNull() ?: 0.0), style = Display.copy(fontSize = 32.sp, color = Lime))
                        Spacer(Modifier.width(6.dp))
                        Text(currencyLabel("SAR"), style = Body.copy(color = OnInkSoft, fontWeight = FontWeight.Medium))
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = totalInput,
                        onValueChange = { editingTotal = true; totalInput = sanitizeAmountInput(it) },
                        label = { Text(stringResource(R.string.budget_monthly_limit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body.copy(color = Lime),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lime,
                            unfocusedBorderColor = Lime.copy(alpha = 0.35f),
                            focusedLabelColor = Lime,
                            unfocusedLabelColor = OnInkSoft,
                            cursorColor = Lime,
                            focusedTextColor = Lime,
                            unfocusedTextColor = Lime
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            totalInput.toDoubleOrNull()?.let { vm.setBudget(monthKey, TOTAL_BUDGET, it) }
                            editingTotal = false
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(RadiusSm),
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink900)
                    ) { Text(stringResource(R.string.budget_save), style = Body.copy(color = Ink900, fontWeight = FontWeight.Bold)) }
                }
            }
        }

        item {
            BudgetPlanCard(
                plan = plan,
                income = expectedIncome,
                savingsThisMonth = savingsThisMonth,
                currency = currencyLabel("SAR")
            )
        }

        if (plan.suggestions.isNotEmpty()) {
            item {
                BudgetSuggestionsCard(
                    suggestions = plan.suggestions,
                    currency = currencyLabel("SAR"),
                    onApply = { cat, amount -> vm.setBudget(monthKey, cat, amount) },
                    onApplyAll = { plan.suggestions.forEach { vm.setBudget(monthKey, it.category, it.amount) } }
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text(stringResource(R.string.budget_per_category), style = H2, modifier = Modifier.weight(1f))
                Text(
                    if (addingCategory) stringResource(R.string.budget_cancel_add) else stringResource(R.string.budget_add_category),
                    style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clickable { addingCategory = !addingCategory; newCategoryInput = "" }
                        .padding(6.dp)
                )
            }
        }

        if (addingCategory) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        placeholder = { Text(stringResource(R.string.budget_category_name_hint), style = Eyebrow) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val name = newCategoryInput.trim()
                            if (name.isNotEmpty()) {
                                vm.addCategory(name, newCategoryIcon)
                                showAllCats = true
                                newCategoryInput = ""; newCategoryIcon = null; addingCategory = false
                            }
                        })
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(Pill)).background(PaperOuter)
                            .clickable { showIconPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            CategoryIcons.iconForKey(newCategoryIcon) ?: Icons.Default.Category,
                            stringResource(R.string.budget_pick_icon), tint = Indigo, modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(Pill)).background(Indigo)
                            .clickable {
                                val name = newCategoryInput.trim()
                                if (name.isNotEmpty()) {
                                    vm.addCategory(name, newCategoryIcon)
                                    showAllCats = true
                                    newCategoryInput = ""; newCategoryIcon = null; addingCategory = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Check, stringResource(R.string.budget_add_action), tint = Lime, modifier = Modifier.size(18.dp)) }
                }
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusLg))
                    .background(White)
            ) {
                shownCats.forEachIndexed { index, cat ->
                    val spentInCat = spentByCat[cat] ?: 0.0
                    val limit = catInputs[cat]?.toDoubleOrNull() ?: 0.0
                    val rollover = rolloverByCat[cat] ?: 0.0
                    val effectiveLimit = limit + rollover
                    val pct = if (effectiveLimit > 0) (spentInCat / effectiveLimit).coerceIn(0.0, 1.0).toFloat() else 0f
                    val isOver = effectiveLimit > 0 && spentInCat > effectiveLimit
                    val isEditing = editingCategory == cat

                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { editingCategory = if (isEditing) null else cat }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(catIcon(cat), catColor(cat), catColorSoft(cat), size = 40.dp, iconSize = 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(categoryDisplay(cat), style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                val rolloverSuffix = if (rollover > 0.0) stringResource(R.string.budget_rolled_fmt, FinancialAdvisor.fmt(rollover)) else ""
                                Text(
                                    if (effectiveLimit > 0) {
                                        stringResource(
                                            R.string.budget_amount_fmt,
                                            FinancialAdvisor.fmt(spentInCat),
                                            FinancialAdvisor.fmt(effectiveLimit)
                                        ) + rolloverSuffix
                                    } else stringResource(R.string.budget_no_limit_fmt, FinancialAdvisor.fmt(spentInCat)),
                                    style = Eyebrow.copy(
                                        fontSize = 12.sp,
                                        color = if (isOver) Danger else InkFaint,
                                        fontWeight = if (isOver) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                            if (effectiveLimit > 0) {
                                Text(
                                    stringResource(R.string.budget_percent_fmt, (pct * 100).roundToInt()),
                                    style = Eyebrow.copy(
                                        fontSize = 12.sp,
                                        color = if (isOver) Danger else Indigo,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            if (!isEditing) {
                                Box(
                                    Modifier.size(32.dp).clip(RoundedCornerShape(RadiusSm)).background(catColorSoft(cat))
                                        .clickable { onOpenCategory(cat) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ReceiptLong,
                                        stringResource(R.string.cat_view_transactions_fmt, categoryDisplay(cat)),
                                        tint = catColor(cat), modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Icon(
                                if (isEditing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                stringResource(if (isEditing) R.string.cd_collapse else R.string.cd_expand),
                                Modifier.size(18.dp), tint = InkFaint
                            )
                        }
                        if (effectiveLimit > 0) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier.fillMaxWidth().height(4.dp)
                                    .clip(RoundedCornerShape(Pill))
                                    .background(PaperOuter)
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(pct).fillMaxHeight()
                                        .clip(RoundedCornerShape(Pill))
                                        .background(if (isOver) Danger else catColor(cat))
                                )
                            }
                        }
                        if (isEditing) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(RadiusSm))
                                    .background(PaperOuter)
                                    .clickable {
                                        catRollover = catRollover + (cat to !(catRollover[cat] ?: false))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.budget_rollover_title), style = Body.copy(fontWeight = FontWeight.Medium))
                                    Text(stringResource(R.string.budget_rollover_desc), style = Eyebrow.copy(fontSize = 12.sp))
                                }
                                Switch(
                                    checked = catRollover[cat] ?: false,
                                    onCheckedChange = { v -> catRollover = catRollover + (cat to v) },
                                    colors = oneUiSwitchColors()
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(Pill)).background(PaperOuter)
                                        .clickable { iconPickerForEdit = cat },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        CategoryIcons.iconForKey(CategoryIcons.assigned[cat]) ?: catIcon(cat),
                                        stringResource(R.string.budget_pick_icon), tint = Indigo, modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = catInputs[cat] ?: "",
                                    onValueChange = { v ->
                                        catInputs = catInputs + (cat to sanitizeAmountInput(v))
                                    },
                                    placeholder = { Text("0", style = Eyebrow) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(RadiusSm),
                                    textStyle = Body
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(Pill)).background(Indigo)
                                        .clickable {
                                            val amt = catInputs[cat]?.toDoubleOrNull() ?: 0.0
                                            val roll = catRollover[cat] ?: false
                                            vm.setBudget(monthKey, cat, amt, roll)
                                            editingCategory = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Check, stringResource(R.string.budget_save), tint = Lime, modifier = Modifier.size(18.dp)) }
                                if (cat != "أخرى") {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(RadiusSm)).background(Danger.copy(alpha = 0.1f))
                                            .clickable {
                                                vm.deleteCategory(cat)
                                                editingCategory = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            stringResource(R.string.budget_delete_category_fmt, cat),
                                            tint = Danger, modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (index < shownCats.lastIndex) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                        }
                    }
                }
                if (!showAllCats && shownCats !== categories && idleCats.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { showAllCats = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = InkFaint)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.budget_show_idle_fmt, idleCats.size),
                            style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    if (showIconPicker) {
        CategoryIconPickerDialog(
            selectedKey = newCategoryIcon,
            onDismiss = { showIconPicker = false },
            onPick = { key -> newCategoryIcon = key; showIconPicker = false }
        )
    }
    iconPickerForEdit?.let { cat ->
        CategoryIconPickerDialog(
            selectedKey = CategoryIcons.assigned[cat],
            onDismiss = { iconPickerForEdit = null },
            onPick = { key ->
                vm.setCategoryIcon(cat, key)
                iconPickerForEdit = null
            }
        )
    }
}

@Composable
private fun BudgetPlanCard(
    plan: BudgetPlan,
    income: Double?,
    savingsThisMonth: Double,
    currency: String
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.budget_plan_title), style = H2)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.budget_commitments_desc), style = Eyebrow.copy(fontSize = 12.sp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            PlanStat(
                label = stringResource(R.string.budget_income_label),
                value = if (income != null && income > 0) FinancialAdvisor.fmt(income) + " " + currency else "—",
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            PlanStat(
                label = stringResource(R.string.budget_committed_label),
                value = FinancialAdvisor.fmt(plan.committedTotal) + " " + currency,
                color = Danger,
                modifier = Modifier.weight(1f)
            )
            PlanStat(
                label = stringResource(R.string.budget_free_label),
                value = FinancialAdvisor.fmt(plan.free) + " " + currency,
                color = Success,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))
        if (plan.fixed.isEmpty() && plan.debts.isEmpty() && plan.savings.isEmpty()) {
            Text(stringResource(R.string.budget_commit_empty), style = BodyMuted.copy(fontSize = 12.sp))
        } else {
            CommitmentGroup(
                icon = Icons.Default.Autorenew,
                tint = Indigo,
                title = stringResource(R.string.budget_commit_fixed),
                total = plan.fixedTotal,
                currency = currency,
                items = plan.fixed
            )
            CommitmentGroup(
                icon = Icons.Default.CreditCard,
                tint = Danger,
                title = stringResource(R.string.budget_commit_debts),
                total = plan.debtTotal,
                currency = currency,
                items = plan.debts
            )
            CommitmentGroup(
                icon = Icons.Default.Savings,
                tint = Success,
                title = stringResource(R.string.budget_commit_savings),
                total = plan.savingsTotal,
                currency = currency,
                items = plan.savings
            )
        }
        if (savingsThisMonth != 0.0) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.budget_savings_month_fmt, FinancialAdvisor.fmt(savingsThisMonth)),
                style = Body.copy(fontSize = 12.sp, color = Success, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun PlanStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = Eyebrow.copy(fontSize = 12.sp))
        Spacer(Modifier.height(3.dp))
        Text(value, style = NumBold.copy(fontSize = 13.sp, color = color))
    }
}

@Composable
private fun CommitmentGroup(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    total: Double,
    currency: String,
    items: List<Commitment>
) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(title, style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), modifier = Modifier.weight(1f))
        Text(FinancialAdvisor.fmt(total) + " " + currency, style = NumBold.copy(fontSize = 12.sp, color = tint))
    }
    items.forEach { c ->
        Row(Modifier.fillMaxWidth().padding(start = 21.dp, top = 3.dp, bottom = 3.dp)) {
            Text(c.label, style = BodyMuted.copy(fontSize = 12.sp), modifier = Modifier.weight(1f), maxLines = 1)
            Text(FinancialAdvisor.fmt(c.amount) + " " + currency, style = Eyebrow.copy(fontSize = 12.sp))
        }
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
            IconBadge(Icons.Default.AutoAwesome, Indigo, IndigoSoft, size = 34.dp, iconSize = 16.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.budget_suggestions_title), style = H2)
                Text(stringResource(R.string.budget_suggestions_desc), style = Eyebrow.copy(fontSize = 12.sp))
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = InkFaint,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                suggestions.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(catIcon(s.category), catColor(s.category), catColorSoft(s.category), size = 30.dp, iconSize = 14.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(categoryDisplay(s.category), style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                            Text(FinancialAdvisor.fmt(s.amount) + " " + currency, style = Eyebrow.copy(fontSize = 12.sp))
                        }
                        Text(
                            stringResource(R.string.budget_apply_suggestion),
                            style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(RadiusSm))
                                .background(IndigoSoft)
                                .clickable { onApply(s.category, s.amount) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onApplyAll,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(RadiusSm),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = White)
                ) { Text(stringResource(R.string.budget_apply_all), style = Body.copy(fontWeight = FontWeight.Bold)) }
            }
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
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text(stringResource(R.string.budget_pick_icon), style = H2) },
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())
            ) {
                val options = buildList {
                    add(CategoryIcons.Option("__default__", Icons.Default.Category))
                    addAll(CategoryIcons.options)
                }
                options.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { opt ->
                            val key = opt.key.takeIf { it != "__default__" }
                            val selected = key == selectedKey || (key == null && selectedKey == null)
                            Box(
                                Modifier.weight(1f).aspectRatio(1f)
                                    .clip(RoundedCornerShape(RadiusSm))
                                    .background(if (selected) IndigoSoft else PaperOuter)
                                    .clickable { onPick(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    opt.icon, null, Modifier.size(20.dp),
                                    tint = if (selected) Indigo else InkSoft
                                )
                            }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.goals_cancel), style = Body.copy(color = InkSoft))
            }
        }
    )
}
