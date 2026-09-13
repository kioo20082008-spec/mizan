package com.mizan.money.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mizan.money.MoneyApp
import com.mizan.money.data.*
import com.mizan.money.sms.InboxScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MoneyApp).repository
    val transactions = repo.allTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets = repo.budgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun scanInbox() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val found = withContext(Dispatchers.IO) { InboxScanner.readTransactions(ctx, sinceDays = 120) }
            repo.addAll(found)
        }
    }
    fun addManual(amount: Double, merchant: String, category: String, type: TxType) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repo.add(TransactionEntity(
                amount = amount, merchant = merchant.ifBlank { null }, category = category,
                type = type, rawSms = "إدخال يدوي",
                smsHash = "manual-$now-${(0..99999).random()}",
                timestamp = now, isManual = true))
        }
    }
    fun delete(tx: TransactionEntity) = viewModelScope.launch { repo.delete(tx) }
    fun setBudget(monthKey: String, category: String, amount: Double) =
        viewModelScope.launch { repo.setBudget(monthKey, category, amount) }
}

object Dates {
    fun monthKey(offset: Int = 0): String {
        val c = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }
    fun monthRange(offset: Int = 0): LongRange {
        val c = Calendar.getInstance().apply {
            add(Calendar.MONTH, offset); set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return start until c.timeInMillis
    }
    fun dayLabel(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return "%04d/%02d/%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}
