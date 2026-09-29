package com.mizan.money.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartCategorizationTest {
    private fun tx(
        id: Long, merchant: String, category: String = "أخرى", edited: Boolean = false,
        type: TxType = TxType.EXPENSE, amount: Double = 10.0, bank: String = "مصرف الإنماء",
        ts: Long = 1_000L, raw: String = "شراء", self: Boolean = false
    ) = TransactionEntity(
        id = id, amount = amount, merchant = merchant, category = category, type = type,
        bankName = bank, rawSms = raw, smsHash = "h$id", timestamp = ts,
        isEdited = edited, isSelfTransfer = self
    )

    @Test fun `merchant spellings share one key`() {
        assertEquals(SmartCategorization.merchantKey("MYSR*ananinja.com"), SmartCategorization.merchantKey("MYSR*AnaNinja.com 2"))
    }

    @Test fun `one manual correction is enough to learn a merchant`() {
        val history = listOf(tx(1, "MYSR*ananinja.com", "طعام وشراب", edited = true))
        assertEquals("طعام وشراب", SmartCategorization.learnedCategory(tx(2, "MYSR*ananinja.com"), history))
    }

    @Test fun `a single unedited sample is not enough`() {
        val history = listOf(tx(1, "EHSAN", "تحويلات"))
        assertNull(SmartCategorization.learnedCategory(tx(2, "EHSAN"), history))
    }

    @Test fun `already categorised transactions are left alone`() {
        val history = listOf(tx(1, "EHSAN", "تحويلات", edited = true))
        assertNull(SmartCategorization.learnedCategory(tx(2, "EHSAN", category = "تسوق"), history))
    }

    @Test fun `alinma to barq transfer pair is matched`() {
        val out = tx(1, "BARQ", type = TxType.EXPENSE, amount = 500.0, ts = 10_000L, raw = "حوالة صادرة محلية")
        val inc = tx(2, "Alinma", type = TxType.INCOME, amount = 500.0, bank = "Barq", ts = 40_000L, raw = "Money added")
        assertEquals(listOf(1L to 2L), InternalTransferPairing.findPairs(listOf(out, inc)))
    }

    @Test fun `same bank or different amount or far apart is not paired`() {
        val out = tx(1, "X", amount = 500.0, ts = 0L, raw = "حوالة")
        assertEquals(0, InternalTransferPairing.findPairs(listOf(out, tx(2, "Y", type = TxType.INCOME, amount = 500.0, ts = 1L, raw = "حوالة"))).size)
        assertEquals(0, InternalTransferPairing.findPairs(listOf(out, tx(3, "Y", type = TxType.INCOME, amount = 499.0, bank = "Barq", ts = 1L, raw = "حوالة"))).size)
        assertEquals(0, InternalTransferPairing.findPairs(listOf(out, tx(4, "Y", type = TxType.INCOME, amount = 500.0, bank = "Barq", ts = 3_600_000L, raw = "حوالة"))).size)
    }
}
