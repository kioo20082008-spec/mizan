package com.mizan.money.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.mizan.money.MoneyApp
import com.mizan.money.widget.MizanWidget
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
                for (msg in messages) {
                    val sender = msg.originatingAddress ?: ""
                    val body = msg.messageBody ?: continue
                    val ts = msg.timestampMillis
                    val parsed = SmsParser.parse(sender, body, ts) ?: continue
                    val entity = parsed.toEntity()
                    val learned = app.repository.learnedCategoryFor(entity.merchant)
                    app.repository.add(if (learned != null) entity.copy(category = learned) else entity)
                    added = true
                }
                if (added) {
                    try { MizanWidget().updateAll(context) }
                    catch (e: Exception) { Log.e("Mizan", "widget update failed", e) }
                }
            } catch (e: Exception) {
                // Never let a malformed SMS crash the app in the background.
                Log.e("Mizan", "failed to process an incoming SMS", e)
            } finally { pending.finish() }
        }
    }
}
