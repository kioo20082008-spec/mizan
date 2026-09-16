package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.DebtEntity
import com.mizan.money.data.DebtType
import com.mizan.money.data.GoalEntity
import com.mizan.money.data.TxType
import kotlin.math.roundToInt

// ============ PLANNING — Budget / Goals / Debts under one bottom-nav slot ============
// Folding three related "planning" concepts behind a segmented control (rather
// than three more bottom-nav icons) is the whole anti-clutter strategy here:
// a user who never sets a goal or tracks a debt never sees anything about them
// beyond one inert tab label.
@Composable
fun PlanningScreen(vm: MainViewModel, offset: Int) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, top = 4.dp, bottom = 10.dp)) {
            Text("التخطيط المالي", style = H1)
            Spacer(Modifier.height(10.dp))
            TabSwitcher(listOf("الميزانية", "الأهداف", "الديون"), subTab) { subTab = it }
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
@Composable
private fun GoalsSection(vm: MainViewModel) {
    val goals by vm.goals.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
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
                    Text("أهداف الادخار", style = H2)
                    Text("اجمع مبلغاً لهدف محدد بخطوات بسيطة", style = Eyebrow)
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(RadiusSm)).background(Ink900)
                        .clickable { showAdd = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, "هدف جديد", tint = Lime, modifier = Modifier.size(20.dp)) }
            }
        }
        if (goals.isEmpty()) {
            item { EmptyState("لا توجد أهداف بعد — أضف أول هدف ادخار") }
        } else {
            items(goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    onContribute = { contributingTo = goal },
                    onDelete = { confirmingDelete = goal }
                )
            }
        }
    }

    if (showAdd) {
        AddGoalDialog(onDismiss = { showAdd = false }, onSave = { name, amount, months ->
            vm.addGoal(name, amount, months); showAdd = false
        })
    }
    contributingTo?.let { goal ->
        ContributeDialog(
            title = "أضف مبلغاً لهدف «${goal.name}»",
            onDismiss = { contributingTo = null },
            onSave = { amount -> vm.contributeToGoal(goal, amount); contributingTo = null }
        )
    }
    confirmingDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text("حذف هدف «${goal.name}»؟", style = H2) },
            text = { Text("لا يمكن التراجع عن هذا الإجراء.", style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = { vm.deleteGoal(goal); confirmingDelete = null }) {
                    Text("حذف", style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) { Text("إلغاء", style = Body.copy(color = InkSoft)) }
            }
        )
    }
}

@Composable
private fun GoalCard(goal: GoalEntity, onContribute: () -> Unit, onDelete: () -> Unit) {
    val pct = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat() else 0f
    val reached = goal.currentAmount >= goal.targetAmount
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Savings, if (reached) Success else Indigo, if (reached) Success.copy(alpha = 0.12f) else IndigoSoft, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = H2.copy(fontSize = 14.sp))
                Text(
                    "${FinancialAdvisor.fmt(goal.currentAmount)} / ${FinancialAdvisor.fmt(goal.targetAmount)} ر.س" +
                        (goal.targetDate?.let { " • حتى ${Dates.dayLabel(it)}" } ?: ""),
                    style = Eyebrow.copy(fontSize = 10.sp)
                )
            }
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(RadiusSm)).background(Danger.copy(alpha = 0.08f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Delete, "حذف", tint = Danger, modifier = Modifier.size(16.dp)) }
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
                if (reached) "وصلت لهدفك 🎉" else "${(pct * 100).roundToInt()}٪ من الهدف",
                style = Eyebrow.copy(fontSize = 11.sp, color = if (reached) Success else InkFaint, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onContribute) {
                Text("+ أضف مبلغ", style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold, fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun AddGoalDialog(onDismiss: () -> Unit, onSave: (String, Double, Int?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text("هدف ادخار جديد", style = H2) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("اسم الهدف (مثال: رحلة، سيارة)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = sanitizeAmountInput(it) },
                    label = { Text("المبلغ المستهدف (${currencyLabel("SAR")})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = months, onValueChange = { months = it.filter { c -> c.isDigit() } },
                    label = { Text("خلال كم شهر؟ (اختياري)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toDoubleOrNull()
                if (name.isNotBlank() && a != null && a > 0) onSave(name.trim(), a, months.toIntOrNull())
            }) { Text("إضافة", style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", style = Body.copy(color = InkSoft)) }
        }
    )
}

@Composable
private fun ContributeDialog(title: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text(title, style = H2) },
        text = {
            OutlinedTextField(
                value = amount, onValueChange = { amount = sanitizeAmountInput(it) },
                label = { Text("المبلغ (${currencyLabel("SAR")})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(RadiusSm), textStyle = Body
            )
        },
        confirmButton = {
            TextButton(onClick = { amount.toDoubleOrNull()?.takeIf { it > 0 }?.let(onSave) }) {
                Text("حفظ", style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", style = Body.copy(color = InkSoft)) }
        }
    )
}

// ============ DEBTS / INSTALLMENTS ============
@Composable
private fun DebtsSection(vm: MainViewModel) {
    val debts by vm.debts.collectAsState()
    val txs by vm.transactions.collectAsState()
    val dismissed by vm.dismissedBnplSuggestions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var payingOn by remember { mutableStateOf<DebtEntity?>(null) }
    var confirmingDelete by remember { mutableStateOf<DebtEntity?>(null) }

    // A single, dismissible suggestion — not a running list — so this can only
    // ever add at most one extra card to the screen, however many BNPL merchants
    // show up in the transaction history.
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
                    Text("الديون والأقساط", style = H2)
                    Text("تابع تقسيطك وديونك بمكان واحد", style = Eyebrow)
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(RadiusSm)).background(Ink900)
                        .clickable { showAdd = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, "دين جديد", tint = Lime, modifier = Modifier.size(20.dp)) }
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
                        Text("لاحظنا مشتريات «${suggestion.first}» متكررة", style = Body.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp))
                        Text("تتبعها كقسط؟ (~${FinancialAdvisor.fmt(suggestion.second)} ر.س)", style = Eyebrow.copy(fontSize = 10.sp))
                    }
                    TextButton(onClick = {
                        vm.addDebt(suggestion.first, DebtType.BNPL, suggestion.second * 4, suggestion.second * 4, suggestion.second, 30)
                    }) { Text("تتبع", style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold, fontSize = 12.sp)) }
                    TextButton(onClick = { vm.dismissBnplSuggestion(suggestion.first) }) {
                        Text("تجاهل", style = BodyMuted.copy(color = InkFaint, fontSize = 12.sp))
                    }
                }
            }
        }
        if (debts.isEmpty()) {
            item { EmptyState("لا توجد ديون متتبعة بعد") }
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
            title = "سجّل دفعة لـ «${debt.name}»",
            onDismiss = { payingOn = null },
            onSave = { amount -> vm.logDebtPayment(debt, amount); payingOn = null }
        )
    }
    confirmingDelete?.let { debt ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text("حذف «${debt.name}» من المتابعة؟", style = H2) },
            text = { Text("لا يمكن التراجع عن هذا الإجراء.", style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = { vm.deleteDebt(debt); confirmingDelete = null }) {
                    Text("حذف", style = Body.copy(color = Danger, fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) { Text("إلغاء", style = Body.copy(color = InkSoft)) }
            }
        )
    }
}

private fun debtTypeLabel(type: DebtType): String = when (type) {
    DebtType.LOAN -> "قرض"
    DebtType.BNPL -> "تقسيط (تابي/تمارا)"
    DebtType.CREDIT_CARD -> "بطاقة ائتمان"
    DebtType.OTHER -> "أخرى"
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
            ) { Icon(Icons.Default.Delete, "حذف", tint = Danger, modifier = Modifier.size(16.dp)) }
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
            Text(
                if (settled) "تم السداد بالكامل ✅"
                else "المتبقي ${FinancialAdvisor.fmt(debt.remainingAmount)} من ${FinancialAdvisor.fmt(debt.totalAmount)} ر.س" +
                    (debt.nextDueDate?.let { " • القسط ${Dates.dayLabel(it)}" } ?: ""),
                style = Eyebrow.copy(fontSize = 10.sp, color = if (dueSoon) Danger else InkFaint, fontWeight = if (dueSoon) FontWeight.Bold else FontWeight.Normal)
            )
            Spacer(Modifier.weight(1f))
            if (!settled) {
                TextButton(onClick = onPay) {
                    Text("سجّل دفعة", style = BodyMuted.copy(color = Indigo, fontWeight = FontWeight.Bold, fontSize = 12.sp))
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
        title = { Text("دين أو قسط جديد", style = H2) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("الاسم (مثال: تابي، قرض السيارة)") },
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
                    label = { Text("المبلغ الإجمالي (${currencyLabel("SAR")})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = remaining, onValueChange = { remaining = sanitizeAmountInput(it) },
                    label = { Text("المتبقي حالياً (${currencyLabel("SAR")})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = installment, onValueChange = { installment = sanitizeAmountInput(it) },
                    label = { Text("قيمة القسط (اختياري)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(RadiusSm), textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = days, onValueChange = { days = it.filter { c -> c.isDigit() } },
                    label = { Text("القسط القادم خلال كم يوم؟ (اختياري)") },
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
            }) { Text("إضافة", style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", style = Body.copy(color = InkSoft)) }
        }
    )
}

// Reuses the same "recurring similar-amount merchant" idea as
// FinancialAdvisor.detectSubscriptions, narrowed to BNPL wording and to
// merchants not already tracked as a debt or previously dismissed — so the
// suggestion banner only ever surfaces something genuinely new and actionable.
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
