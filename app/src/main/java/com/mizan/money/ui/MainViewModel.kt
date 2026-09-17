package com.mizan.money.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mizan.money.R
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.data.*
import com.mizan.money.notify.NotificationHelper
import com.mizan.money.sms.InboxScanner
import com.mizan.money.ui.theme.localizedContext
import com.mizan.money.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// The repository is constructor-injected (see MainViewModel.factory()) instead of
// cast out of Application inside the class, so this can be constructed with a
// fake repository in tests or previews.
class MainViewModel(app: Application, private val repo: TransactionRepository) : AndroidViewModel(app) {
    val transactions = repo.allTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets = repo.budgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goals = repo.goals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val debts = repo.debts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recurringItems = repo.recurringItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val prefs = app.getSharedPreferences("mizan_prefs", Context.MODE_PRIVATE)
    // Persisted (not just remembered in Compose state) so the expensive 120-day
    // inbox scan runs once ever, not on every cold app launch — new SMS after
    // that are picked up live by SmsReceiver instead.
    fun hasCompletedInitialScan(): Boolean = prefs.getBoolean("initial_scan_done", false)
    fun markInitialScanDone() = prefs.edit().putBoolean("initial_scan_done", true).apply()

    // Lets a user whose "month" doesn't start on the 1st (e.g. salary lands on
    // the 29th) have every screen's monthly totals follow that cycle instead of
    // the calendar month. Clamped to 1..28 so every calendar month can host it.
    private val _monthStartDay = MutableStateFlow(prefs.getInt("month_start_day", 1))
    val monthStartDay: StateFlow<Int> = _monthStartDay
    fun setMonthStartDay(day: Int) {
        val clamped = day.coerceIn(1, 28)
        prefs.edit().putInt("month_start_day", clamped).apply()
        _monthStartDay.value = clamped
    }

    // The account holder's own name, used by SmsParser to recognize transfers
    // between the user's own accounts at different banks (e.g. Alinma <-> Barq)
    // by counterparty name. Not hardcoded in SmsParser itself — see MoneyApp,
    // which loads this same pref key before any SMS is ever parsed.
    private val _ownerName = MutableStateFlow(prefs.getString("owner_name", "") ?: "")
    val ownerName: StateFlow<String> = _ownerName
    fun setOwnerName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) prefs.edit().remove("owner_name").apply()
        else prefs.edit().putString("owner_name", trimmed).apply()
        _ownerName.value = trimmed
        com.mizan.money.sms.SmsParser.ownerNameTokens =
            trimmed.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    // A user-entered salary figure, used as an override for the auto-detected
    // one in the financial advisor (recurring-deposit detection needs 2+ months
    // of history and can be wrong/slow to pick up a new salary).
    private val _manualSalary = MutableStateFlow(prefs.getString("manual_salary", null)?.toDoubleOrNull() ?: 0.0)
    val manualSalary: StateFlow<Double> = _manualSalary
    fun setManualSalary(amount: Double) {
        val v = amount.coerceAtLeast(0.0)
        if (v <= 0.0) prefs.edit().remove("manual_salary").apply()
        else prefs.edit().putString("manual_salary", v.toString()).apply()
        _manualSalary.value = v
    }

    // User-editable fixed SAR conversion rates for foreign-currency
    // transactions (see ExchangeRates). Persisted so the widget and the
    // background worker can read the same values straight from prefs.
    private val _exchangeRates = MutableStateFlow(ExchangeRates.load(prefs))
    val exchangeRates: StateFlow<Map<String, Double>> = _exchangeRates
    fun setExchangeRate(code: String, rate: Double) {
        ExchangeRates.save(prefs, code, rate)
        _exchangeRates.value = ExchangeRates.load(prefs)
    }

    // Master switch for both notification types (budget alerts, bill reminders),
    // surfaced as a single toggle in Settings — NotificationHelper checks this
    // same key before showing anything, including from the background worker.
    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("notifications_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        _notificationsEnabled.value = enabled
    }

    // User-managed category list — seeded from CategoryClassifier's defaults,
    // then freely add/delete from Settings. Stored as a delimited string
    // (order matters for display) rather than one bool per default category,
    // since a custom addition needs to persist the same way a kept default does.
    private fun loadCategories(): List<String> =
        prefs.getString("categories", null)
            ?.split(CATEGORY_DELIM)?.filter { it.isNotBlank() }
            ?: com.mizan.money.sms.CategoryClassifier.categories
    private val _categories = MutableStateFlow(loadCategories())
    val categories: StateFlow<List<String>> = _categories
    private fun saveCategories(list: List<String>) {
        prefs.edit().putString("categories", list.joinToString(CATEGORY_DELIM)).apply()
        _categories.value = list
    }
    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || _categories.value.contains(trimmed)) return
        saveCategories(_categories.value + trimmed)
    }
    fun deleteCategory(name: String) {
        if (name == "أخرى") return // always keep a fallback category to classify into
        saveCategories(_categories.value.filter { it != name })
    }

    fun scanInbox() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val ctx = getApplication<Application>()
                val result = withContext(Dispatchers.IO) { InboxScanner.readTransactions(ctx, sinceDays = 120) }
                repo.reconcile(result.transactions, result.scannedHashes)
                // Only recorded after a successful reconcile: if the SMS query
                // fails on this device, the next launch must retry rather than
                // treating a failed scan as "already scanned".
                markInitialScanDone()
                WidgetUpdater.refresh(getApplication())
                checkBudgetThreshold()
            } catch (e: Exception) {
                // Reading the SMS provider can fail in device-specific ways (some
                // OEM builds reject the query even with READ_SMS granted). An
                // uncaught exception here would otherwise crash the whole app on
                // launch, so degrade to "no transactions found" instead.
                android.util.Log.e("Mizan", "inbox scan failed", e)
            } finally {
                _isScanning.value = false
            }
        }
    }
    fun addManual(amount: Double, merchant: String, category: String, type: TxType) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repo.add(TransactionEntity(
                amount = amount, merchant = merchant.ifBlank { null }, category = category,
                type = type, rawSms = "إدخال يدوي",
                smsHash = "manual-${java.util.UUID.randomUUID()}",
                timestamp = now, isManual = true))
            WidgetUpdater.refresh(getApplication())
            checkBudgetThreshold()
        }
    }
    fun update(tx: TransactionEntity) = viewModelScope.launch { repo.update(tx); checkBudgetThreshold(); WidgetUpdater.refresh(getApplication()) }
    fun delete(tx: TransactionEntity) = viewModelScope.launch { repo.delete(tx); WidgetUpdater.refresh(getApplication()) }
    fun setBudget(
        monthKey: String,
        category: String,
        amount: Double,
        rolloverEnabled: Boolean? = null
    ) = viewModelScope.launch {
        repo.setBudget(monthKey, category, amount, rolloverEnabled)
    }

    // Fires a local notification the first time this month's spend crosses 80%
    // then 100% of the effective budget (income-based, or the manually-set total
    // — same resolution BudgetStatusCard/AdvisorScreen already use). The
    // per-month "already notified at X%" marker is keyed by monthKey so it
    // naturally resets itself once a new month starts, without any cleanup code.
    private suspend fun checkBudgetThreshold() {
        val startDay = _monthStartDay.value
        val range = Dates.monthRange(0, startDay)
        // One-shot DB reads instead of `transactions.value`/`budgets.value`:
        // both are WhileSubscribed StateFlows, so reading .value immediately
        // after an insert can return a stale list and fire (or skip) the alert
        // for the wrong total.
        val txs = repo.allTransactionsOnce()
        val allBudgets = repo.budgetsOnce()
        val rates = _exchangeRates.value
        val summary = FinancialAdvisor.summarize(txs, range.first, range.last, rates)
        val monthKey = Dates.monthKey(0, startDay)
        val manualBudget = allBudgets
            .firstOrNull { it.monthKey == monthKey && it.category == TOTAL_BUDGET }
            ?.limitAmount?.takeIf { it > 0 }
        val budget = manualBudget
            ?: (FinancialAdvisor.planningIncome(summary, txs, _manualSalary.value, rates) ?: 0.0)
        if (budget <= 0) return
        val pct = summary.spent / budget
        val prefKey = "budget_notified_pct_$monthKey"
        val lastNotified = prefs.getFloat(prefKey, 0f)
        val threshold = when {
            pct >= 1.0 && lastNotified < 1.0f -> 1.0f
            pct >= 0.8 && lastNotified < 0.8f -> 0.8f
            else -> null
        } ?: return
        prefs.edit().putFloat(prefKey, threshold).apply()
        val app = getApplication<Application>()
        val lctx = localizedContext(app)
        val title = lctx.getString(
            if (threshold >= 1.0f) R.string.notif_budget_over_title else R.string.notif_budget_near_title
        )
        val body = lctx.getString(
            R.string.notif_budget_body_fmt,
            FinancialAdvisor.fmt(summary.spent),
            FinancialAdvisor.fmt(budget),
            (pct * 100).toInt(),
        )
        NotificationHelper.notifyBudget(app, 1001, title, body)
    }

    // ---- Savings goals ----
    fun addGoal(name: String, targetAmount: Double, months: Int?) = viewModelScope.launch {
        val targetDate = months?.takeIf { it > 0 }?.let {
            System.currentTimeMillis() + it.toLong() * 30L * 86_400_000L
        }
        repo.addGoal(GoalEntity(name = name, targetAmount = targetAmount, targetDate = targetDate))
    }
    fun contributeToGoal(goal: GoalEntity, amount: Double) = viewModelScope.launch {
        repo.updateGoal(goal.copy(currentAmount = goal.currentAmount + amount))
    }
    fun deleteGoal(goal: GoalEntity) = viewModelScope.launch { repo.deleteGoal(goal) }

    // ---- Debts / installments ----
    fun addDebt(name: String, type: DebtType, totalAmount: Double, remainingAmount: Double, installmentAmount: Double, daysUntilNext: Int?) =
        viewModelScope.launch {
            val nextDue = daysUntilNext?.takeIf { it > 0 }?.let {
                System.currentTimeMillis() + it.toLong() * 86_400_000L
            }
            repo.addDebt(DebtEntity(
                name = name, type = type, totalAmount = totalAmount,
                remainingAmount = remainingAmount, installmentAmount = installmentAmount, nextDueDate = nextDue
            ))
        }
    fun logDebtPayment(debt: DebtEntity, amount: Double) = viewModelScope.launch {
        val newRemaining = (debt.remainingAmount - amount).coerceAtLeast(0.0)
        // Rolls the next due date forward by ~a month on payment, rather than
        // leaving a now-stale date — good enough for a monthly installment
        // without needing a full recurrence-rule engine.
        val newDue = debt.nextDueDate?.let { it + 30L * 86_400_000L }
        repo.updateDebt(debt.copy(remainingAmount = newRemaining, nextDueDate = newDue))
    }
    fun deleteDebt(debt: DebtEntity) = viewModelScope.launch { repo.deleteDebt(debt) }

    // Merchant names (lowercased) the user dismissed from the "track this as a
    // debt?" suggestion banner — persisted so a dismissal survives app restarts
    // instead of the same suggestion reappearing every time the screen reopens.
    private val _dismissedBnpl = MutableStateFlow(
        prefs.getStringSet("dismissed_bnpl_suggestions", emptySet()) ?: emptySet()
    )
    val dismissedBnplSuggestions: StateFlow<Set<String>> = _dismissedBnpl
    fun dismissBnplSuggestion(merchantLower: String) {
        val updated = _dismissedBnpl.value + merchantLower.lowercase().trim()
        prefs.edit().putStringSet("dismissed_bnpl_suggestions", updated).apply()
        _dismissedBnpl.value = updated
    }

    // ---- Bill reminders (toggled from a transaction's own detail view) ----
    fun setBillReminder(tx: TransactionEntity, enabled: Boolean) {
        val merchant = tx.merchant?.trim()?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            val existing = recurringItems.value.firstOrNull { it.merchant.equals(merchant, ignoreCase = true) }
            if (enabled) {
                repo.upsertRecurringItem(
                    RecurringItemEntity(
                        id = existing?.id ?: 0,
                        merchant = merchant,
                        expectedAmount = tx.amount,
                        expectedDayOfMonth = Dates.dayOfMonth(tx.timestamp),
                        category = tx.category,
                        reminderEnabled = true
                    )
                )
            } else if (existing != null) {
                repo.deleteRecurringItem(existing)
            }
        }
    }

    companion object {
        private const val CATEGORY_DELIM = "|||"
        fun factory(app: Application, repo: TransactionRepository) = viewModelFactory {
            initializer { MainViewModel(app, repo) }
        }
    }
}

object Dates {
    // With the default startDay=1 this reduces to plain calendar months (the
    // "today < startDay" branch never triggers since a day-of-month is always
    // >= 1), so existing callers/behavior are unchanged.
    //
    // Month arithmetic only ever runs on a calendar parked at DAY_OF_MONTH=1 —
    // adding a month to e.g. "Jan 30" would silently roll over into March in a
    // non-leap February (Calendar normalizes the overflow instead of clamping),
    // corrupting the cycle end date. The real startDay is set only as the very
    // last step, on a throwaway clone, once no more month arithmetic will run.
    fun monthRange(offset: Int = 0, startDay: Int = 1): LongRange {
        val todayDom = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val base = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        if (todayDom < startDay) base.add(Calendar.MONTH, -1)
        base.add(Calendar.MONTH, offset)

        val startCal = base.clone() as Calendar
        startCal.set(Calendar.DAY_OF_MONTH, startDay.coerceAtMost(startCal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        val start = startCal.timeInMillis

        val endBase = base.clone() as Calendar
        endBase.add(Calendar.MONTH, 1) // still parked at day=1, so this is always safe
        endBase.set(Calendar.DAY_OF_MONTH, startDay.coerceAtMost(endBase.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return start until endBase.timeInMillis
    }
    fun monthKey(offset: Int = 0, startDay: Int = 1): String {
        val c = Calendar.getInstance().apply { timeInMillis = monthRange(offset, startDay).first }
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }
    fun dayLabel(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return "%04d/%02d/%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
    fun dayOfMonth(ts: Long): Int = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_MONTH)
}
