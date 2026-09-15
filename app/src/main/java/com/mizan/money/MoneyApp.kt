package com.mizan.money

import android.app.Application
import com.mizan.money.data.AppDatabase
import com.mizan.money.data.TransactionRepository
import java.io.File

class MoneyApp : Application() {
    val db by lazy { AppDatabase.get(this) }
    val repository by lazy { TransactionRepository(db.transactionDao(), db.budgetDao()) }

    override fun onCreate() {
        super.onCreate()
        // The SMS-export-for-debugging feature writes financial data to a plaintext
        // file for sharing; don't let a copy linger across app sessions.
        File(cacheDir, "exports").deleteRecursively()
    }
}
