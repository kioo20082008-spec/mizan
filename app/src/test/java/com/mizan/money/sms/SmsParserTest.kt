package com.mizan.money.sms

import com.mizan.money.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    @Test
    fun `parses an expense SMS with amount, bank, currency and merchant`() {
        val sms = "عميلنا العزيز، تم خصم مبلغ 125.50 ريال من حسابك في الراجحي لدى ستاربكس فرع العليا"
        val parsed = SmsParser.parse("ALRAJHIBANK", sms, 1_000L)

        assertNotNull(parsed)
        assertEquals(125.50, parsed!!.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals(TxType.EXPENSE, parsed.type)
        assertEquals("مصرف الراجحي", parsed.bankName)
        assertTrue(parsed.merchant?.contains("ستاربكس") == true)
    }

    @Test
    fun `parses an income SMS as INCOME with the right bank and amount`() {
        val sms = "تم إيداع راتب بمبلغ 9500.00 ريال في حسابك لدى بنك الرياض"
        val parsed = SmsParser.parse("RIBLSARI", sms, 2_000L)

        assertNotNull(parsed)
        assertEquals(9500.00, parsed!!.amount, 0.001)
        assertEquals(TxType.INCOME, parsed.type)
        assertEquals("بنك الرياض", parsed.bankName)
    }

    @Test
    fun `detects a foreign currency transaction`() {
        val sms = "Your account was charged 45.00 USD at Amazon"
        val parsed = SmsParser.parse("BANK", sms, 3_000L)

        assertNotNull(parsed)
        assertEquals(45.00, parsed!!.amount, 0.001)
        assertEquals("USD", parsed.currency)
        assertEquals(TxType.EXPENSE, parsed.type)
    }

    @Test
    fun `rejects OTP messages even when they contain a number`() {
        val sms = "رمز التحقق الخاص بك هو 4821، لا تشارك هذا الرمز مع أي شخص"
        assertFalse(SmsParser.looksLikeTransaction(sms))
        assertNull(SmsParser.parse("BANK", sms, 4_000L))
    }

    @Test
    fun `does not treat a balance-inquiry SMS as a transaction`() {
        val sms = "الرصيد المتاح في حسابك 500.00 ريال"
        assertFalse(SmsParser.looksLikeTransaction(sms))
        assertNull(SmsParser.parse("BANK", sms, 5_000L))
    }

    @Test
    fun `rejects a plain message with a number but no financial keyword or bank`() {
        val sms = "اجتماع الساعة 3، الغرفة رقم 205"
        assertFalse(SmsParser.looksLikeTransaction(sms))
    }

    @Test
    fun `hashFor is stable for identical input and differs when any field changes`() {
        val h1 = SmsParser.hashFor("BANK", 1_000L, "same body")
        val h2 = SmsParser.hashFor("BANK", 1_000L, "same body")
        val h3 = SmsParser.hashFor("BANK", 1_001L, "same body")

        assertEquals(h1, h2)
        assertTrue(h1 != h3)
        assertEquals(64, h1.length) // SHA-256 as hex
    }
}
