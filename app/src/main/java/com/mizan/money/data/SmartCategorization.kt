package com.mizan.money.data

import kotlin.math.abs

// Pure logic (no Android/Room) so it stays unit-testable.
object SmartCategorization {
    private const val OTHER = "أخرى"

    // Groups the many spellings of one merchant ("MYSR*ananinja.com",
    // "MYSR*AnaNinja.com 2") under one key: lowercase, punctuation stripped,
    // pure-number tokens dropped, first two tokens kept.
    fun merchantKey(merchant: String?): String? {
        val s = merchant?.lowercase()?.replace(Regex("[^\\p{L}\\p{N}]+"), " ")?.trim() ?: return null
        val key = s.split(" ").filter { it.length > 1 && !it.all(Char::isDigit) }.take(2).joinToString(" ")
        return key.takeIf { it.length >= 3 }
    }

    // A merchant the parser could only file under "أخرى" gets the category the
    // user's own history says it belongs to. A hand-corrected transaction counts
    // 3x; the winner needs weight >= 2 (one correction, or two agreeing SMS) and
    // more than half of the merchant's total weight.
    fun learnedCategory(tx: TransactionEntity, history: List<TransactionEntity>): String? {
        if (tx.category != OTHER || tx.isSelfTransfer || tx.isManual) return null
        val key = merchantKey(tx.merchant) ?: return null
        val weights = HashMap<String, Int>()
        for (h in history) {
            if (h.id == tx.id && tx.id != 0L) continue
            if (h.category == OTHER || h.isSelfTransfer) continue
            if (merchantKey(h.merchant) != key) continue
            weights[h.category] = (weights[h.category] ?: 0) + if (h.isEdited) 3 else 1
        }
        val total = weights.values.sum()
        val best = weights.maxByOrNull { it.value } ?: return null
        return best.key.takeIf { best.value >= 2 && best.value * 2 > total }
    }

    fun applyLearned(tx: TransactionEntity, history: List<TransactionEntity>): TransactionEntity =
        learnedCategory(tx, history)?.let { tx.copy(category = it) } ?: tx
}

// Money that leaves one of the user's accounts and arrives in another (Alinma
// <-> Barq) shows up as an EXPENSE SMS from one bank and an INCOME SMS from the
// other. Neither is real spending or income, so matching pairs are flagged as
// self transfers without needing the owner's name.
object InternalTransferPairing {
    private const val WINDOW_MS = 15 * 60_000L
    private val transferWords = listOf("حوالة", "تحويل", "transfer", "money added", "credit")

    private fun looksLikeTransfer(t: TransactionEntity): Boolean {
        val low = t.rawSms.lowercase()
        return transferWords.any { low.contains(it) }
    }

    // Returns the ids of both legs of every matched pair (each row used once).
    fun findPairs(all: List<TransactionEntity>): List<Pair<Long, Long>> {
        val candidates = all.filter { !it.isManual && !it.isSelfTransfer && !it.isEdited && it.bankName != null }
        val outgoing = candidates.filter { it.type == TxType.EXPENSE }.sortedBy { it.timestamp }
        val incoming = candidates.filter { it.type == TxType.INCOME }
        val used = HashSet<Long>()
        val pairs = mutableListOf<Pair<Long, Long>>()
        for (e in outgoing) {
            val match = incoming.firstOrNull { i ->
                i.id !in used &&
                    i.bankName != e.bankName &&
                    i.currency == e.currency &&
                    abs(i.amount - e.amount) < 0.005 &&
                    abs(i.timestamp - e.timestamp) <= WINDOW_MS &&
                    (looksLikeTransfer(e) || looksLikeTransfer(i))
            } ?: continue
            used += match.id
            pairs += e.id to match.id
        }
        return pairs
    }
}
