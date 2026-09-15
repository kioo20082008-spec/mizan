package com.mizan.money.sms

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.mizan.money.data.TransactionEntity
import java.util.concurrent.TimeUnit

object InboxScanner {
    private val URI_INBOX: Uri = Uri.parse("content://sms/inbox")
    fun readTransactions(context: Context, sinceDays: Int = 120): List<TransactionEntity> {
        val out = mutableListOf<TransactionEntity>()
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
                    val parsed = SmsParser.parse(sender, body, date) ?: continue
                    out += parsed.toEntity()
                } catch (e: Exception) {
                    // Skip this one malformed row rather than losing the whole scan.
                }
            }
        }
        return out
    }
}
