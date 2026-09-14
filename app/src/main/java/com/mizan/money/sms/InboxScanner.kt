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
        resolver.query(URI_INBOX,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(iAddr) ?: ""
                val body = c.getString(iBody) ?: continue
                val date = c.getLong(iDate)
                val parsed = SmsParser.parse(sender, body, date) ?: continue
                val hash = SmsParser.hashFor(sender, date, body)
                out += TransactionEntity(
                    amount = parsed.amount, currency = parsed.currency,
                    merchant = parsed.merchant ?: parsed.bankName ?: sender,
                    category = CategoryClassifier.classify(parsed.merchant, body),
                    type = parsed.type, bankName = parsed.bankName, cardLast4 = parsed.cardLast4,
                    rawSms = body, smsHash = hash, timestamp = date, isManual = false
                )
            }
        }
        return out
    }
}
