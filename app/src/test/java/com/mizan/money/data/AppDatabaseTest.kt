package com.mizan.money.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Opens a real, in-memory Room database under Robolectric so the generated DAO
// SQL (not a hand-written fake) is exercised on CI via `testDebugUnitTest`.
// The plain Application is intentional: the manifest's MoneyApp.onCreate calls
// WorkManager, which is not initialized in a JVM unit test and would abort setup.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `transaction insert round-trips amount and reimbursement flag`() = runBlocking {
        val txDao = db.transactionDao()
        txDao.insert(
            TransactionEntity(
                amount = 42.5,
                timestamp = 1_700_000_000_000L,
                category = "طعام",
                smsHash = "h1"
            )
        )
        txDao.insert(
            TransactionEntity(
                amount = 10.0,
                timestamp = 1_700_000_100_000L,
                category = "أخرى",
                smsHash = "h2",
                isReimbursement = true
            )
        )

        val all = txDao.getAllOnce()
        assertEquals(2, all.size)

        val first = all.firstOrNull { it.smsHash == "h1" }
        assertNotNull(first)
        assertEquals(42.5, first!!.amount, 0.001)

        val reimbursed = all.firstOrNull { it.smsHash == "h2" }
        assertNotNull(reimbursed)
        assertTrue(reimbursed!!.isReimbursement)
    }

    @Test
    fun `budget upsert then find returns limit and rollover flag`() = runBlocking {
        val budgetDao = db.budgetDao()
        val budget = BudgetEntity(
            monthKey = "2024-01",
            category = "طعام",
            limitAmount = 500.0,
            rolloverEnabled = true
        )
        budgetDao.upsert(budget)

        val stored = budgetDao.find("2024-01", "طعام")
        assertNotNull(stored)
        assertEquals(500.0, stored!!.limitAmount, 0.001)
        assertTrue(stored.rolloverEnabled)

        budgetDao.upsert(budget.copy(limitAmount = 750.0))
        val updated = budgetDao.find("2024-01", "طعام")
        assertNotNull(updated)
        assertEquals(750.0, updated!!.limitAmount, 0.001)
    }
}
