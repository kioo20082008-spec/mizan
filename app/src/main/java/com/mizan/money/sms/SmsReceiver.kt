package com.mizan.money.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.mizan.money.MoneyApp
import com.mizan.money.data.SELF_TRANSFER_CATEGORY
import com.mizan.money.data.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MoneyApp
                for (msg in messages) {
                    val sender = msg.originatingAddress ?: ""
                    val body = msg.messageBody ?: continue
                    val ts = msg.timestampMillis
                    val parsed = SmsParser.parse(sender, body, ts) ?: continue
                    val hash = SmsParser.hashFor(sender, ts, body)
                    val category = if (parsed.isSelfTransfer) SELF_TRANSFER_CATEGORY
                        else CategoryClassifier.classify(parsed.merchant, body)
                    app.repository.add(TransactionEntity(
                        amount = parsed.amount, currency = parsed.currency,
                        merchant = parsed.merchant ?: parsed.bankName ?: sender,
                        category = category,
                        type = parsed.type, bankName = parsed.bankName, cardLast4 = parsed.cardLast4,
                        rawSms = body, smsHash = hash, timestamp = ts, isManual = false,
                        isSelfTransfer = parsed.isSelfTransfer
                    ))
                }
            } finally { pending.finish() }
        }
    }
}
