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
                var added = false
                // A long (e.g. Arabic/UCS-2) SMS arrives as several PDU parts in one
                // broadcast. Parsing each part alone loses fields that landed in a
                // later part (the merchant line), so join the parts per sender first.
                val grouped = messages.groupBy { it.originatingAddress ?: "" }
                for ((sender, parts) in grouped) {
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    if (body.isEmpty()) continue
                    val ts = parts.first().timestampMillis
                    val parsed = SmsParser.parse(sender, body, ts) ?: continue
                    app.repository.add(parsed.toEntity())
                    added = true
                }
                // Refresh the widget once per broadcast, not once per message.
                if (added) WidgetUpdater.refresh(context)
            } catch (e: Exception) {
                // Never let a malformed SMS crash the app in the background.
                Log.e("Mizan", "failed to process an incoming SMS", e)
            } finally { pending.finish() }
        }
    }
}
