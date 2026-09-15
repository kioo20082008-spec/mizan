package com.mizan.money.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mizan.money.data.*
import com.mizan.money.sms.InboxScanner
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
        }
    }
    fun update(tx: TransactionEntity) = viewModelScope.launch { repo.update(tx) }
    fun delete(tx: TransactionEntity) = viewModelScope.launch { repo.delete(tx) }
    fun setBudget(monthKey: String, category: String, amount: Double) =
        viewModelScope.launch { repo.setBudget(monthKey, category, amount) }

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
}
