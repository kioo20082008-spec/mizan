package com.mizan.money

import android.app.Application
import com.mizan.money.data.AppDatabase
import com.mizan.money.data.TransactionRepository

class MoneyApp : Application() {
    val db by lazy { AppDatabase.get(this) }
    val repository by lazy { TransactionRepository(db.transactionDao(), db.budgetDao()) }
}
