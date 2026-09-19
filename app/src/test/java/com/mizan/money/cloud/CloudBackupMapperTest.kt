package com.mizan.money.cloud

import com.mizan.money.data.BudgetEntity
import com.mizan.money.data.DebtEntity
import com.mizan.money.data.DebtType
import com.mizan.money.data.GoalContributionEntity
import com.mizan.money.data.GoalEntity
import com.mizan.money.data.RecurringItemEntity
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupMapperTest {

    private val transaction = TransactionEntity(
        id = 7, amount = 125.5, currency = "USD", merchant = "Amazon",
        category = "تسوق", type = TxType.EXPENSE, bankName = "Barq",
        cardLast4 = "1929", rawSms = "charged 125.50 USD", smsHash = "abc",
        timestamp = 1_700_000_000_000L, isManual = false, isSelfTransfer = true,
        isEdited = true, excludeFromDailyAvg = true, isReimbursement = true,
    )
    private val budget = BudgetEntity("2026-09", "__TOTAL__", 8000.0, true)
    private val goal = GoalEntity(
        id = 3, name = "رحلة", targetAmount = 5000.0, currentAmount = 1200.0,
        targetDate = 1_800_000_000_000L, monthlyAmount = 400.0,
        createdAt = 1_700_000_000_000L, isArchived = true,
    )
    private val contribution = GoalContributionEntity(6, goalId = 3, amount = 400.0, timestamp = 1_700_000_000_000L)
    private val debt = DebtEntity(
        id = 4, name = "تابي", type = DebtType.BNPL, totalAmount = 1200.0,
        remainingAmount = 600.0, installmentAmount = 200.0,
        nextDueDate = 1_800_000_000_000L, lender = "Tabby",
        termMonths = 6, paidMonths = 2, createdAt = 1_700_000_000_000L, isArchived = true,
    )
    private val recurring = RecurringItemEntity(
        id = 5, merchant = "Netflix", expectedAmount = 35.0, expectedDayOfMonth = 12,
        category = "اشتراكات", reminderEnabled = false, isFixed = true,
        lastNotifiedMonthKey = "2026-09", createdAt = 1_700_000_000_000L,
    )

    @Test
    fun `every entity survives a map round trip`() {
        assertEquals(transaction, CloudBackupMapper.transactionFrom(CloudBackupMapper.transaction(transaction)))
        assertEquals(budget, CloudBackupMapper.budgetFrom(CloudBackupMapper.budget(budget)))
        assertEquals(goal, CloudBackupMapper.goalFrom(CloudBackupMapper.goal(goal)))
        assertEquals(contribution, CloudBackupMapper.contributionFrom(CloudBackupMapper.contribution(contribution)))
        assertEquals(debt, CloudBackupMapper.debtFrom(CloudBackupMapper.debt(debt)))
        assertEquals(recurring, CloudBackupMapper.recurringItemFrom(CloudBackupMapper.recurringItem(recurring)))
    }

    @Test
    fun `nullable fields stay null through the round trip`() {
        val plain = TransactionEntity(id = 1, amount = 10.0, smsHash = "h", timestamp = 1L)
        val back = CloudBackupMapper.transactionFrom(CloudBackupMapper.transaction(plain))
        assertNull(back.merchant)
        assertNull(back.bankName)
        assertNull(back.cardLast4)
        assertNull(CloudBackupMapper.goalFrom(CloudBackupMapper.goal(GoalEntity(name = "g", targetAmount = 1.0))).targetDate)
        assertNull(CloudBackupMapper.debtFrom(CloudBackupMapper.debt(DebtEntity(name = "d", totalAmount = 1.0, remainingAmount = 1.0))).nextDueDate)
    }

    @Test
    fun `reads Firestore numeric coercion (ints as longs)`() {
        // Firestore returns whole numbers as Long and the rest as Double; the
        // mapper must accept either without losing data.
        val raw = CloudBackupMapper.transaction(transaction).toMutableMap()
        raw["id"] = 7L
        raw["amount"] = 125L
        raw["timestamp"] = 1_700_000_000_000L
        val back = CloudBackupMapper.transactionFrom(raw)
        assertEquals(7L, back.id)
        assertEquals(125.0, back.amount, 0.0)
        assertEquals(1_700_000_000_000L, back.timestamp)
    }

    @Test
    fun `missing or unknown fields fall back to safe defaults`() {
        val empty = CloudBackupMapper.transactionFrom(emptyMap())
        assertEquals(0.0, empty.amount, 0.0)
        assertEquals("SAR", empty.currency)
        assertEquals(TxType.EXPENSE, empty.type)

        val bogus = CloudBackupMapper.debtFrom(mapOf("type" to "NOT_A_TYPE", "termMonths" to 0L))
        assertEquals(DebtType.OTHER, bogus.type)

        val noKey = CloudBackupMapper.recurringItemFrom(emptyMap())
        assertTrue(noKey.reminderEnabled)
    }

    @Test
    fun `budget document id sanitizes slashes that Firestore rejects`() {
        val id = CloudBackupMapper.budgetDocId(BudgetEntity("2026-09", "food/دليفري", 1.0))
        assertTrue(id.none { it == '/' })
        assertTrue(id.contains("2026-09"))
    }
}
