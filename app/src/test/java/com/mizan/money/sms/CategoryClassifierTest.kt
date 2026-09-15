package com.mizan.money.sms

import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryClassifierTest {

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
