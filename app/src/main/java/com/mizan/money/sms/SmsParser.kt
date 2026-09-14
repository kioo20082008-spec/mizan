package com.mizan.money.sms

import com.mizan.money.data.TxType
import java.security.MessageDigest
import java.util.Locale

data class ParsedSms(
    val amount: Double, val currency: String, val merchant: String?,
    val cardLast4: String?, val bankName: String?, val type: TxType,
    val timestamp: Long, val raw: String, val sender: String,
    val isSelfTransfer: Boolean
)

object SmsParser {
    private val expenseWords = listOf(
        "شراء","خصم","تم خصم","سحب","دفع","مدين","مشتريات","نقاط البيع",
        "purchase","debit","withdraw","payment","pos","spent","charged"
    )
    private val incomeWords = listOf(
        "إيداع","ايداع","أضيف","اضيف","راتب","حوالة واردة","استرداد","مرتجع",
        "deposit","credit","salary","refund","received"
    )
    private val otpWords = listOf(
        "otp","رمز التحقق","كود التحقق","رمز التأكيد","verification code",
        "do not share","لا تشارك"
    )
    // Wording banks use when money moves between the same customer's own
    // accounts, as opposed to a transfer to someone else.
    private val selfTransferWords = listOf(
        "بين حساباتك","بين حسابيك","بين حساباتي","من حسابك الى حسابك",
        "من حسابك إلى حسابك","تحويل داخلي","internal transfer","own account"
    )
    private val balanceWord = Regex(
        """(?:رصيد|الرصيد|رصيدك|balance|متاح|available)[^\d]{0,25}[\d,]+(?:\.\d{1,2})?""",
        RegexOption.IGNORE_CASE
    )
    private val amountPatterns = listOf(
        Regex("""(?:مبلغ|بمبلغ|قيمة|المبلغ|amount|amt)\s*[:：]?\s*(?:SAR|SR|ر\.?س|ريال)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:SAR|SR|ر\.?س\.?|ريال|USD|دولار)""", RegexOption.IGNORE_CASE),
        Regex("""(?:SAR|SR|ر\.?س\.?|ريال)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    )
    private val merchantPatterns = listOf(
        Regex("""(?:لدى|عند|من|في متجر|التاجر|merchant|at)\s*[:：]?\s*([^\n\r,،؛|]{2,45})""", RegexOption.IGNORE_CASE)
    )
    private val cardPattern = Regex("""(?:بطاقة|card|حساب|acct|account)\D{0,8}[*xX#]*\s*(\d{4})""", RegexOption.IGNORE_CASE)
    private val banks = mapOf(
        "الراجحي" to "مصرف الراجحي","alrajhi" to "مصرف الراجحي",
        "الأهلي" to "البنك الأهلي","الاهلي" to "البنك الأهلي","snb" to "البنك الأهلي",
        "الرياض" to "بنك الرياض","riyad" to "بنك الرياض",
        "البلاد" to "بنك البلاد","albilad" to "بنك البلاد",
        "الإنماء" to "مصرف الإنماء","alinma" to "مصرف الإنماء",
        "سامبا" to "سامبا","samba" to "سامبا",
        "الجزيرة" to "بنك الجزيرة","aljazira" to "بنك الجزيرة",
        "stc pay" to "STC Pay","urpay" to "UrPay","d360" to "D360"
    )
    private fun normalizeDigits(input: String): String {
        var s = input
        val ar = "٠١٢٣٤٥٦٧٨٩"
        val fa = "۰۱۲۳۴۵۶۷۸۹"
        ar.forEachIndexed { i, c -> s = s.replace(c, ('0' + i)) }
        fa.forEachIndexed { i, c -> s = s.replace(c, ('0' + i)) }
        return s
    }
    fun looksLikeTransaction(body: String): Boolean {
        val n = normalizeDigits(body)
        val low = n.lowercase(Locale.ROOT)
        if (otpWords.any { low.contains(it) }) return false
        val hasAmount = extractAmount(n) != null
        val hasKeyword = (expenseWords + incomeWords).any { low.contains(it) }
        val hasBank = banks.keys.any { low.contains(it) }
        return hasAmount && (hasKeyword || hasBank)
    }
    private fun extractAmount(normalized: String): Double? {
        val cleaned = balanceWord.replace(normalized, " ")
        for (p in amountPatterns) {
            val m = p.find(cleaned) ?: continue
            val raw = m.groupValues[1].replace(",", "").trim()
            val v = raw.toDoubleOrNull()
            if (v != null && v > 0.0) return v
        }
        return null
    }
    fun parse(sender: String, body: String, timestamp: Long): ParsedSms? {
        val n = normalizeDigits(body)
        val low = n.lowercase(Locale.ROOT)
        if (otpWords.any { low.contains(it) }) return null
        val amount = extractAmount(n) ?: return null
        val hasKeyword = (expenseWords + incomeWords).any { low.contains(it) }
        val bankName = banks.entries.firstOrNull { low.contains(it.key) }?.value
        if (!hasKeyword && bankName == null) return null
        val type = when {
            incomeWords.any { low.contains(it) } -> TxType.INCOME
            else -> TxType.EXPENSE
        }
        val merchant = merchantPatterns
            .firstNotNullOfOrNull { it.find(n)?.groupValues?.get(1)?.trim() }
            ?.trim(' ', '.', '-', ':')
            ?.takeIf { it.length in 2..45 }
        val last4 = cardPattern.find(n)?.groupValues?.get(1)
        val currency = when {
            low.contains("usd") || low.contains("دولار") -> "USD"
            low.contains("eur") || low.contains("يورو") -> "EUR"
            low.contains("aed") || low.contains("درهم") -> "AED"
            else -> "SAR"
        }
        val isSelfTransfer = selfTransferWords.any { low.contains(it) }
        return ParsedSms(amount, currency, merchant, last4, bankName, type, timestamp, body, sender, isSelfTransfer)
    }
    fun hashFor(sender: String, timestamp: Long, body: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sender|$timestamp|$body".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
