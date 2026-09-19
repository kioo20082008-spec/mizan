package com.mizan.money.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    private fun sampleData() = BackupData(
        transactions = listOf(
            TransactionEntity(
                id = 7, amount = 125.5, currency = "USD", merchant = "Amazon",
                category = "تسوق", type = TxType.EXPENSE, bankName = "Barq",
                cardLast4 = "1929", rawSms = "charged 125.50 USD", smsHash = "abc",
                timestamp = 1_700_000_000_000L, isManual = false, isSelfTransfer = false,
                isEdited = true, excludeFromDailyAvg = true,
            ),
            TransactionEntity(
                id = 8, amount = 9500.0, currency = "SAR", merchant = null,
                category = "أخرى", type = TxType.INCOME, timestamp = 1_700_000_001_000L,
                smsHash = "def", isManual = true, isReimbursement = true,
            ),
        ),
        budgets = listOf(BudgetEntity("2026-09", TOTAL_BUDGET, 8000.0, true)),
        goals = listOf(GoalEntity(id = 3, name = "رحلة", targetAmount = 5000.0, currentAmount = 1200.0, targetDate = 1_800_000_000_000L, monthlyAmount = 400.0)),
        debts = listOf(DebtEntity(id = 4, name = "تابي", type = DebtType.BNPL, totalAmount = 1200.0, remainingAmount = 600.0, installmentAmount = 200.0, nextDueDate = 1_800_000_000_000L, lender = "Tabby")),
        recurringItems = listOf(RecurringItemEntity(id = 5, merchant = "Netflix", expectedAmount = 35.0, expectedDayOfMonth = 12, category = "اشتراكات", reminderEnabled = true, isFixed = true, lastNotifiedMonthKey = "2026-09")),
        goalContributions = listOf(GoalContributionEntity(id = 6, goalId = 3, amount = 400.0, timestamp = 1_700_000_000_000L)),
    )

    @Test
    fun `round trips every entity type without losing fields`() {
        val original = sampleData()
        val restored = BackupManager.fromJson(BackupManager.toJson(original))

        assertEquals(original.transactions, restored.transactions)
        assertEquals(original.budgets, restored.budgets)
        assertEquals(original.goals, restored.goals)
        assertEquals(original.debts, restored.debts)
        assertEquals(original.recurringItems, restored.recurringItems)
        assertEquals(original.goalContributions, restored.goalContributions)
    }

    @Test
    fun `old backups without new fields still load`() {
        val restored = BackupManager.fromJson(
            """{"app":"mizan","version":1,"goals":[{"id":1,"name":"g","targetAmount":100.0}],
               "recurringItems":[{"id":2,"merchant":"X","expectedAmount":10.0,"expectedDayOfMonth":1,"category":"أخرى"}]}"""
        )
        assertEquals(0.0, restored.goals.single().monthlyAmount, 0.001)
        assertTrue(!restored.recurringItems.single().isFixed)
        assertTrue(restored.goalContributions.isEmpty())
    }

    @Test
    fun `nullable fields round trip as null, not empty strings`() {
        val restored = BackupManager.fromJson(BackupManager.toJson(sampleData()))
        val income = restored.transactions.first { it.id == 8L }
        assertNull(income.merchant)
        assertNull(income.bankName)
        val noDate = BackupManager.fromJson(
            BackupManager.toJson(BackupData(goals = listOf(GoalEntity(name = "g", targetAmount = 1.0))))
        )
        assertNull(noDate.goals.single().targetDate)
    }

    @Test
    fun `rejects json that is not a Mizan backup`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager.fromJson("""{"hello":"world"}""")
        }
    }

    @Test
    fun `rejects invalid json`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager.fromJson("not json at all")
        }
    }

    @Test
    fun `tolerates a backup with missing arrays`() {
        val restored = BackupManager.fromJson("""{"app":"mizan","version":1}""")
        assertTrue(restored.transactions.isEmpty())
        assertTrue(restored.budgets.isEmpty())
    }
}
