package com.mizan.money.sms

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.mizan.money.data.TransactionEntity
import java.util.concurrent.TimeUnit

// `scannedHashes` covers every SMS looked at in this scan, transactional or
// not — the repository needs that full set (not just `transactions`) to tell
// "never was a transaction" apart from "used to parse as one, and shouldn't
// anymore" so it can clean up a stale row left by an old parser bug.
data class ScanResult(val transactions: List<TransactionEntity>, val scannedHashes: Set<String>)

object InboxScanner {
    private val URI_INBOX: Uri = Uri.parse("content://sms/inbox")
    fun readTransactions(context: Context, sinceDays: Int = 120): ScanResult {
        val out = mutableListOf<TransactionEntity>()
        val seenHashes = mutableSetOf<String>()
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(sinceDays.toLong())
        val resolver: ContentResolver = context.contentResolver
        val cursor = try {
            resolver.query(URI_INBOX,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC"
            )
        } catch (e: Exception) {
            // Some OEM builds reject this query even with READ_SMS granted.
            Log.e("Mizan", "SMS inbox query failed", e)
            null
        }
        cursor?.use { c ->
            val iAddr = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndex(Telephony.Sms.BODY)
            val iDate = c.getColumnIndex(Telephony.Sms.DATE)
            if (iAddr < 0 || iBody < 0 || iDate < 0) return@use
            while (c.moveToNext()) {
                try {
                    val sender = c.getString(iAddr) ?: ""
                    val body = c.getString(iBody) ?: continue
                    val date = c.getLong(iDate)
                    seenHashes += SmsParser.hashFor(sender, date, body)
                    val parsed = SmsParser.parse(sender, body, date) ?: continue
                    out += parsed.toEntity()
                } catch (e: Exception) {
                    // Skip this one malformed row rather than losing the whole scan.
                    Log.w("Mizan", "skipped one malformed SMS row during scan", e)
                }
            }
        }
        return ScanResult(out, seenHashes)
    }
}
