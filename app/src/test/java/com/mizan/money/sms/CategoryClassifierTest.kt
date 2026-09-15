package com.mizan.money.sms

import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryClassifierTest {

    @Test
    fun `an STC Pay wallet transfer is categorized as a transfer, not a phone bill`() {
        // Regression test: اتصالات's bare "stc" keyword must not shadow تحويلات's
        // more specific "stc pay" match — تحويلات is checked first for this reason.
        val category = CategoryClassifier.classify(null, "تم الدفع عبر STC Pay بمبلغ 100 ريال")
        assertEquals("تحويلات", category)
    }

    @Test
    fun `a plain STC phone charge (no "pay") is still categorized as telecom`() {
        val category = CategoryClassifier.classify(null, "تم خصم رسوم STC الشهرية")
        assertEquals("اتصالات", category)
    }

    @Test
    fun `the English bank-SMS phrase "Total Amount" does not get miscategorized as fuel`() {
        // Regression test: "total" (the fuel brand) was removed from وقود's
        // keyword list because it's a substring of this near-universal phrase.
        val category = CategoryClassifier.classify(null, "Purchase at Jarir Total Amount SAR 45.00")
        assertEquals("تسوق", category)
    }

    @Test
    fun `classifies a bare ATM withdrawal with no merchant as cash withdrawal`() {
        val category = CategoryClassifier.classify(null, "سحب نقدي من الصراف الآلي بمبلغ 200 ريال")
        assertEquals(CASH_WITHDRAWAL_CATEGORY, category)
    }

    @Test
    fun `a known merchant still wins even if the SMS also mentions an ATM`() {
        val category = CategoryClassifier.classify(
            "ستاربكس",
            "شراء بالبطاقة من الصراف الآلي لدى ستاربكس"
        )
        assertEquals("طعام وشراب", category)
    }

    @Test
    fun `falls back to other for unrecognized merchants`() {
        val category = CategoryClassifier.classify("XYZ123", "خصم من حسابك")
        assertEquals("أخرى", category)
    }
}
