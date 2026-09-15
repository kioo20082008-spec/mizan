package com.mizan.money.sms

import com.mizan.money.data.SELF_TRANSFER_CATEGORY
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import java.security.MessageDigest
import java.util.Locale

data class ParsedSms(
    val amount: Double, val currency: String, val merchant: String?,
    val cardLast4: String?, val bankName: String?, val type: TxType,
    val timestamp: Long, val raw: String, val sender: String,
    val isSelfTransfer: Boolean
)

// Shared by InboxScanner (full inbox scan) and SmsReceiver (live incoming SMS) so
// the category/merchant/hash decisions can't drift out of sync between the two.
fun ParsedSms.toEntity(): TransactionEntity {
    val category = if (isSelfTransfer) SELF_TRANSFER_CATEGORY
        else CategoryClassifier.classify(merchant, raw)
    return TransactionEntity(
        amount = amount, currency = currency,
        merchant = merchant ?: bankName ?: sender,
        category = category,
        type = type, bankName = bankName, cardLast4 = cardLast4,
        rawSms = raw, smsHash = SmsParser.hashFor(sender, timestamp, raw),
        timestamp = timestamp, isManual = false, isSelfTransfer = isSelfTransfer
    )
}

object SmsParser {
    // Restricted, per explicit request, to only the two banks/wallets this
    // app's user actually holds accounts with. Any other sender — Tabby,
    // Tamara, a utility company's own bill-reminder SMS, etc — is rejected
    // outright regardless of what its text looks like, since those senders
    // can mention an amount and a purchase/payment word too without ever
    // being the bank's own confirmation that money actually moved.
    private val allowedSenders = listOf("alinma", "الإنماء", "barq", "برق")
    private fun isAllowedSender(sender: String): Boolean {
        val s = sender.lowercase(Locale.ROOT)
        return allowedSenders.any { s.contains(it) }
    }
    // Free-text expense/income keywords (single-sentence SMS from banks like
    // Rajhi), plus the header phrases used by banks that instead send a
    // structured multi-line "label: value" SMS (e.g. Alinma, Barq): each field
    // — amount, counterparty, date — is on its own line, so these headers
    // ("Credit Transfer Internal", "حوالة صادرة محلية", ...) are the only
    // signal of transaction type since the body itself has no verb sentence.
    private val expenseWords = listOf(
        "شراء","خصم","تم خصم","سحب","دفع","مدين","مشتريات","نقاط البيع",
        "purchase","debit","withdraw","payment","pos","spent","charged",
        "debit transfer","outgoing local transfer","barq wallet transfer",
        "حوالة صادرة","خصم نهائي","سداد","إشعار خصم","الجهة","الخدمة"
    )
    // Bare English "deposit" deliberately excluded: Barq's own internal
    // holding-account name for wallet top-ups is literally "BARQ SAFE AND
    // DEPOSIT CLIENT MONEY", so it would misclassify an *outgoing* transfer to
    // that account (a self-transfer, type EXPENSE) as INCOME purely because
    // its own recipient name happens to contain the word "deposit". "credit"/
    // "money added"/Arabic "إيداع" already cover every real deposit template
    // seen, without that collision risk.
    private val incomeWords = listOf(
        "إيداع","ايداع","أضيف","اضيف","راتب","حوالة واردة","استرداد","مرتجع",
        "credit","salary","refund","received",
        "credit transfer","incoming local transfer","حوالة داخلية واردة",
        "money added","تم قيد مبلغ","reversal","reverse transaction",
        "reversed local transfer","حوالة عكسية"
    )
    // Messages that never represent settled money movement: OTPs, beneficiary
    // management, pending/failed purchase attempts, and marketing notices.
    // Checked before anything else so none of the expense/income keywords
    // above — which can appear incidentally in these too — cause a false hit.
    private val nonTransactionalWords = listOf(
        "otp","رمز التحقق","كود التحقق","رمز التأكيد","verification code",
        "do not share","لا تشارك","one time password","رمز التفعيل","استخدم رمز",
        // "تم إضافة/تفعيل مستفيد" (beneficiary added/activated) — narrower than a
        // bare "مستفيد", which also appears as the normal field label on a
        // legitimate transfer confirmation ("إلى المستفيد: <name>").
        "تم إضافة مستفيد","تم تفعيل مستفيد","new benef",
        "rejected transaction","ya hala","welcome back",
        "logged in","عزيزي العميل","عميلنا العزيز","dear customer","dear barq customer",
        "hi waleed","هلا وليد","حجز مبلغ","رصيد البطاقة","لإتمام عملية الشراء",
        "الرمز السري","الرقم السري","رمز شراء","qattah","successfully added to",
        "نفيدكم","الاستعلام عن","تم تغيير الرقم","تم تغير الرقم"
    )
    // Wording banks use when money moves between the same customer's own
    // accounts, as opposed to a transfer to someone else.
    private val selfTransferWords = listOf(
        "بين حساباتك","بين حسابيك","بين حساباتي","من حسابك الى حسابك",
        "من حسابك إلى حسابك","تحويل داخلي","internal transfer","own account"
    )
    // The account holder's own name, as it appears as the counterparty on
    // transfers between their own accounts at different banks/wallets (e.g.
    // Alinma <-> Barq). Requires both first and last name so a relative
    // sharing the family name (e.g. a brother) isn't mistaken for the owner.
    // Barq's own internal holding account name for wallet top-ups is a
    // reliable self-transfer signal on its own, regardless of bank — as is a
    // bank purchase whose merchant is bare "Barq" (a card top-up of the
    // user's own Barq wallet, not an actual purchase from a third party).
    private fun isSelfAccountName(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT).trim()
        if (n.contains("barq safe and deposit client money") || n == "barq") return true
        val hasFirst = n.contains("waleed") || n.contains("وليد")
        val hasLast = n.contains("hamadallah") || n.contains("حمدالله") || n.contains("حمدال")
        return hasFirst && hasLast
    }
    private val balanceWord = Regex(
        """(?:رصيد|الرصيد|رصيدك|balance|متاح|available)[^\d]{0,80}[\d,]+(?:\.\d{1,2})?""",
        RegexOption.IGNORE_CASE
    )
    // A foreign-currency purchase's own SAR-converted equivalent, e.g.
    // "Amount: 1348 THB (156.58 SAR)" — checked first and, when present,
    // wins over every other amount pattern below. Otherwise the raw foreign
    // number (1348) would be captured and misfiled as if it were SAR.
    private val parenSarPattern = Regex("""\(\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*SAR\s*\)""", RegexOption.IGNORE_CASE)
    private val amountPatterns = listOf(
        Regex("""(?:مبلغ|بمبلغ|قيمة|المبلغ|amount|amt)\s*[:：]?\s*(?:ريال\s+سعود[ىي]|SAR|SR|ر\.?س|ريال)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:SAR|SR|ر\.?س\.?|ريال|USD|دولار)""", RegexOption.IGNORE_CASE),
        Regex("""(?:ريال\s+سعود[ىي]|SAR|SR|ر\.?س\.?|ريال)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    )
    // "من" ("from") is far too generic to trust as a merchant anchor — it almost
    // always precedes "حسابك"/"بطاقتك" etc. rather than the actual merchant, and
    // since Regex.find() always returns the leftmost match, putting it in the same
    // alternation as the specific anchors below would win purely on position. So it
    // only gets tried as a last resort, after every more specific anchor has failed.
    private val merchantPatternsPrimary = listOf(
        Regex("""(?:لدى|عند|في متجر|التاجر|merchant|at)\s*[:：]?\s*([^\n\r,،؛|]{2,45})""", RegexOption.IGNORE_CASE)
    )
    private val merchantPatternsFallback = listOf(
        Regex("""(?:من)\s*[:：]?\s*([^\n\r,،؛|]{2,45})""", RegexOption.IGNORE_CASE)
    )
    // Structured "label: value" SMS put each field on its own line, so a
    // bank's *first* matching line — in document order — is trusted: e.g.
    // Alinma's "من: Tabby" (merchant) reliably comes before an unrelated
    // "من حساب: **3000" (source account) elsewhere in the same message, but
    // a single whole-text regex can't tell those two "من" occurrences apart.
    // The negative lookahead keeps a bare "من"/"لـ" from also matching "من
    // حساب"/"لـ حساب"/"...بطاقة" ("from/to account"/"...card") lines, which
    // are a *different* field and would otherwise win first simply for
    // appearing earlier. "لـ" is the recipient label on Alinma's plain
    // "حوالة صادرة محلية" template (e.g. "لـ BARQ SAFE AND DEPOSIT CLIENT
    // MONEY") — without it, that transfer's merchant/self-transfer status
    // could never be determined at all.
    private val merchantLineRegex = Regex(
        """^(?:من البائع|إلى المستفيد|المستفيد|من(?!\s*(?:حساب|بطاقة))|لـ(?!\s*(?:حساب|بطاقة))|from|to|at|الجهة)\s*[:：]?\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private fun extractMerchantFromLines(body: String): String? {
        for (rawLine in body.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val m = merchantLineRegex.find(line) ?: continue
            val v = m.groupValues[1].trim()
            if (v.isNotEmpty()) return v
        }
        return null
    }
    private val cardPattern = Regex("""(?:بطاقة|card|حساب|acct|account)\D{0,8}[*xX#]*\s*(\d{4})""", RegexOption.IGNORE_CASE)
    private val banks = mapOf(
        "الراجحي" to "مصرف الراجحي","alrajhi" to "مصرف الراجحي",
        "الأهلي" to "البنك الأهلي","الاهلي" to "البنك الأهلي","snb" to "البنك الأهلي",
        "الرياض" to "بنك الرياض","riyad" to "بنك الرياض",
        "البلاد" to "بنك البلاد","albilad" to "بنك البلاد",
        "الإنماء" to "مصرف الإنماء","alinma" to "مصرف الإنماء",
        "برق" to "Barq","barq" to "Barq",
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
        if (nonTransactionalWords.any { low.contains(it) }) return false
        val hasAmount = extractAmount(n) != null
        val hasKeyword = (expenseWords + incomeWords).any { low.contains(it) }
        val hasBank = banks.keys.any { low.contains(it) }
        return hasAmount && (hasKeyword || hasBank)
    }
    // Returns the amount and whether it's already a SAR-converted figure
    // (from a parenthesized foreign-currency conversion), so callers can
    // force the transaction's currency to SAR instead of trusting whatever
    // foreign-currency word appears elsewhere in the message.
    private fun extractAmount(normalized: String): Pair<Double, Boolean>? {
        val cleaned = balanceWord.replace(normalized, " ")
        parenSarPattern.find(cleaned)?.let { m ->
            val v = m.groupValues[1].replace(",", "").toDoubleOrNull()
            if (v != null && v > 0.0) return v to true
        }
        for (p in amountPatterns) {
            val m = p.find(cleaned) ?: continue
            val raw = m.groupValues[1].replace(",", "").trim()
            val v = raw.toDoubleOrNull()
            if (v != null && v > 0.0) return v to false
        }
        return null
    }
    fun parse(sender: String, body: String, timestamp: Long): ParsedSms? {
        if (!isAllowedSender(sender)) return null
        val n = normalizeDigits(body)
        val low = n.lowercase(Locale.ROOT)
        if (nonTransactionalWords.any { low.contains(it) }) return null
        val (amount, isSarConverted) = extractAmount(n) ?: return null
        val hasKeyword = (expenseWords + incomeWords).any { low.contains(it) }
        val bankName = banks.entries.firstOrNull { low.contains(it.key) || sender.lowercase(Locale.ROOT).contains(it.key) }?.value
        if (!hasKeyword && bankName == null) return null
        val type = when {
            incomeWords.any { low.contains(it) } -> TxType.INCOME
            else -> TxType.EXPENSE
        }
        val merchant = (
            extractMerchantFromLines(n)
                ?: merchantPatternsPrimary.firstNotNullOfOrNull { it.find(n)?.groupValues?.get(1)?.trim() }
                ?: merchantPatternsFallback.firstNotNullOfOrNull { it.find(n)?.groupValues?.get(1)?.trim() }
            )
            ?.trim(' ', '.', '-', ':')
            ?.takeIf { it.length in 2..45 }
        val last4 = cardPattern.find(n)?.groupValues?.get(1)
        val currency = when {
            isSarConverted -> "SAR"
            low.contains("usd") || low.contains("دولار") -> "USD"
            low.contains("eur") || low.contains("يورو") -> "EUR"
            low.contains("aed") || low.contains("درهم") -> "AED"
            else -> "SAR"
        }
        val isSelfTransfer = selfTransferWords.any { low.contains(it) } ||
            (merchant != null && isSelfAccountName(merchant))
        return ParsedSms(amount, currency, merchant, last4, bankName, type, timestamp, body, sender, isSelfTransfer)
    }
    fun hashFor(sender: String, timestamp: Long, body: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sender|$timestamp|$body".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
