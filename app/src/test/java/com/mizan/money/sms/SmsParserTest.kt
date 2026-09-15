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
        // "من حسابك" appears before "لدى ستاربكس" — regression test for the
        // merchant regex correctly preferring the specific "لدى" anchor over the
        // generic, earlier-occurring "من" (which would otherwise capture
        // "حسابك في الراجحي لدى ستاربكس فرع العليا" instead of just the merchant).
        val sms = "عميلنا العزيز، تم خصم مبلغ 125.50 ريال من حسابك في الراجحي لدى ستاربكس فرع العليا"
        val parsed = SmsParser.parse("alinma", sms, 1_000L)

        assertNotNull(parsed)
        assertEquals(125.50, parsed!!.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals(TxType.EXPENSE, parsed.type)
        assertEquals("مصرف الراجحي", parsed.bankName)
        assertEquals("ستاربكس فرع العليا", parsed.merchant)
    }

    @Test
    fun `falls back to the "من" anchor only when no more specific anchor is present`() {
        val sms = "تم خصم مبلغ 60.00 ريال من محفظتك الرقمية"
        val parsed = SmsParser.parse("alinma", sms, 1_500L)

        assertNotNull(parsed)
        assertEquals("محفظتك الرقمية", parsed!!.merchant)
    }

    @Test
    fun `parses an income SMS as INCOME with the right bank and amount`() {
        val sms = "تم إيداع راتب بمبلغ 9500.00 ريال في حسابك لدى بنك الرياض"
        val parsed = SmsParser.parse("alinma", sms, 2_000L)

        assertNotNull(parsed)
        assertEquals(9500.00, parsed!!.amount, 0.001)
        assertEquals(TxType.INCOME, parsed.type)
        assertEquals("بنك الرياض", parsed.bankName)
    }

    @Test
    fun `detects a foreign currency transaction`() {
        val sms = "Your account was charged 45.00 USD at Amazon"
        val parsed = SmsParser.parse("barq", sms, 3_000L)

        assertNotNull(parsed)
        assertEquals(45.00, parsed!!.amount, 0.001)
        assertEquals("USD", parsed.currency)
        assertEquals(TxType.EXPENSE, parsed.type)
    }

    @Test
    fun `rejects OTP messages even when they contain a number`() {
        val sms = "رمز التحقق الخاص بك هو 4821، لا تشارك هذا الرمز مع أي شخص"
        assertFalse(SmsParser.looksLikeTransaction(sms))
        assertNull(SmsParser.parse("alinma", sms, 4_000L))
    }

    @Test
    fun `does not treat a balance-inquiry SMS as a transaction`() {
        val sms = "الرصيد المتاح في حسابك 500.00 ريال"
        assertFalse(SmsParser.looksLikeTransaction(sms))
        assertNull(SmsParser.parse("alinma", sms, 5_000L))
    }

    @Test
    fun `rejects a plain message with a number but no financial keyword or bank`() {
        val sms = "اجتماع الساعة 3، الغرفة رقم 205"
        assertFalse(SmsParser.looksLikeTransaction(sms))
    }

    @Test
    fun `flags a transfer between the user's own accounts as a self transfer`() {
        val sms = "تم تحويل مبلغ 2000.00 ريال بين حساباتك في مصرف الراجحي"
        val parsed = SmsParser.parse("alinma", sms, 6_000L)

        assertNotNull(parsed)
        assertTrue(parsed!!.isSelfTransfer)
        assertEquals(2000.00, parsed.amount, 0.001)
    }

    @Test
    fun `does not flag a transfer to someone else as a self transfer`() {
        val sms = "تم تحويل مبلغ 500.00 ريال إلى حساب صديقك عبر STC Pay"
        val parsed = SmsParser.parse("barq", sms, 7_000L)

        assertNotNull(parsed)
        assertFalse(parsed!!.isSelfTransfer)
    }

    // Per explicit request: only Alinma and Barq are this app's own bank/wallet
    // — a message from any other sender must never become a transaction, even
    // if its wording would otherwise look exactly like a real one.
    @Test
    fun `rejects a transaction-shaped message from a sender that is not Alinma or Barq`() {
        val sms = "تم خصم مبلغ 250.00 ريال من بطاقتك لدى Tabby"
        assertNull(SmsParser.parse("Tabby", sms, 20_000L))
    }

    @Test
    fun `rejects a bill-reminder-shaped message from a utility company`() {
        val sms = "فاتورتك الشهرية بمبلغ 340.00 ريال مستحقة الدفع"
        assertNull(SmsParser.parse("SaudiEnergy", sms, 20_500L))
    }

    // Regression tests for real Alinma/Barq SMS, which use a structured
    // multi-line "label: value" body instead of one free-flowing sentence.
    @Test
    fun `parses a structured multi-line purchase SMS and finds the merchant on its own line`() {
        val sms = "شراء إنترنت\nمبلغ: 36.81 SAR\nببطاقة مدى: 3581*\nمن حساب: **3000\nمن: Tabby\nفي: 2025-08-06 04:38"
        val parsed = SmsParser.parse("alinma", sms, 8_000L)

        assertNotNull(parsed)
        assertEquals(36.81, parsed!!.amount, 0.001)
        assertEquals(TxType.EXPENSE, parsed.type)
        assertEquals("Tabby", parsed.merchant)
    }

    @Test
    fun `an earlier "من حساب" line does not shadow the real "من" merchant line that follows it`() {
        // Both lines start with "من" — the field-anchored line scan must return
        // the *first line in the message*, not the first "من" occurrence overall,
        // so it doesn't stop at "من حساب" before reaching the real merchant line.
        val sms = "POS Purchases:\nmada card: **1929\nAmount: 32.50 SAR\nWallet Balance: 214.10\nAt: MCDONALDS\nOn: 2025-06-30 01:11"
        val parsed = SmsParser.parse("barq app", sms, 8_500L)

        assertEquals("MCDONALDS", parsed!!.merchant)
    }

    @Test
    fun `recognizes the "ريال سعودى" (Saudi Riyal, two words) currency phrasing`() {
        val sms = "شراء عبر Samsung Wallet\nمبلغ: ريال سعودى 44.37\nبطاقة مدى: 6513*\nحساب: *3000\nمن: Al Nahdi Pharmacy\nفي: 2025-08-22 19:40"
        val parsed = SmsParser.parse("alinma", sms, 9_000L)

        assertNotNull(parsed)
        assertEquals(44.37, parsed!!.amount, 0.001)
    }

    @Test
    fun `uses the SAR-converted amount for a foreign-currency purchase, not the raw foreign figure`() {
        val sms = "International POS Purchases:\nVisa card: **1929\nAmount: 1348 THB (156.58 SAR)\nWallet balance: 3359.78\nAt: AT TWENTY TWO HOUSE\nCountry: Thailand\nOn: 2025-07-21 06:20"
        val parsed = SmsParser.parse("barq app", sms, 9_500L)

        assertNotNull(parsed)
        assertEquals(156.58, parsed!!.amount, 0.001)
        assertEquals("SAR", parsed.currency)
    }

    @Test
    fun `flags a card top-up of the user's own Barq wallet as a self transfer, not real spending`() {
        val sms = "شراء إنترنت\nمبلغ: 1,500 SAR\nببطاقة مدى: 6513*\nمن حساب: **3000\nمن: Barq\nفي: 2025-08-06 13:05"
        val parsed = SmsParser.parse("alinma", sms, 10_000L)

        assertNotNull(parsed)
        assertTrue(parsed!!.isSelfTransfer)
    }

    @Test
    fun `flags an outgoing transfer to the account holder's own name as a self transfer`() {
        val sms = "Incoming local transfer\nAmount: 1076.00 SAR\nFrom: WALEED HAMADALLAH\nBank: INMA BANK\n2025-07-01 00:50"
        val parsed = SmsParser.parse("barq app", sms, 10_500L)

        assertNotNull(parsed)
        assertTrue(parsed!!.isSelfTransfer)
    }

    @Test
    fun `does not flag a transfer from a relative sharing the family name as a self transfer`() {
        val sms = "حوالة داخلية واردة\nبـ  1,400 ريال\nمن عبدالكريم حمدالله\nمن حساب **9000\nلحساب *3000\nفي 06:13 25-12-26"
        val parsed = SmsParser.parse("alinma", sms, 11_000L)

        assertNotNull(parsed)
        assertFalse(parsed!!.isSelfTransfer)
    }

    @Test
    fun `a beneficiary field on a real transfer confirmation is not mistaken for a beneficiary-added notice`() {
        // "المستفيد" (beneficiary) is also the field label on a genuine transfer
        // receipt ("إلى المستفيد: <name>"), so only the more specific "تم إضافة/
        // تفعيل مستفيد" phrasing should be treated as a non-transactional notice.
        val sms = "حوالة صادرة داخلية\nمبلغ: 1,014 ريال\nالمستفيد: عبدالكريم هيثم اسعد حمدالله\nإلى حساب: *9000\nمن حساب: **3000\nفي: 2025-07-01 00:22"
        val parsed = SmsParser.parse("alinma", sms, 11_500L)

        assertNotNull(parsed)
        assertEquals(1014.0, parsed!!.amount, 0.001)
    }

    @Test
    fun `a beneficiary-added notice with no amount is still rejected as non-transactional`() {
        val sms = "تم إضافة مستفيد بنك محلي بنجاح"
        assertFalse(SmsParser.looksLikeTransaction(sms))
        assertNull(SmsParser.parse("alinma", sms, 12_000L))
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
