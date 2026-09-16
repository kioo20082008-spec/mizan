package com.mizan.money

import android.app.Application
import android.content.Context
import com.mizan.money.data.AppDatabase
import com.mizan.money.data.TransactionRepository
import com.mizan.money.sms.SmsParser
import java.io.File

class MoneyApp : Application() {
    val db by lazy { AppDatabase.get(this) }
    val repository by lazy { TransactionRepository(this, db.transactionDao(), db.budgetDao()) }

    override fun onCreate() {
        super.onCreate()
        // The SMS-export-for-debugging feature writes financial data to a plaintext
        // file for sharing; don't let a copy linger across app sessions.
        File(cacheDir, "exports").deleteRecursively()
        // SmsReceiver can run before MainViewModel is ever constructed (a fresh
        // process started in the background just to handle an incoming SMS
        // broadcast), so the owner name used for self-transfer detection has to
        // be loaded here rather than relying on the ViewModel to set it.
        val prefs = getSharedPreferences("mizan_prefs", Context.MODE_PRIVATE)
        SmsParser.ownerNameTokens = prefs.getString("owner_name", null)
            ?.lowercase()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
    }
}
