package com.mizan.money.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.mizan.money.MoneyApp
import com.mizan.money.widget.WidgetUpdater
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
                    app.repository.add(parsed.toEntity())
                    WidgetUpdater.refresh(context)
                }
            } catch (e: Exception) {
                // Never let a malformed SMS crash the app in the background.
                Log.e("Mizan", "failed to process an incoming SMS", e)
            } finally { pending.finish() }
        }
    }
}
