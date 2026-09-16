package com.mizan.money.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mizan.money.MainActivity
import com.mizan.money.R

// Two channels — one per notification "reason" — so a user who only cares about
// budget alerts can silence bill reminders from system settings without losing
// the other, instead of an all-or-nothing single channel.
object NotificationHelper {
    const val CHANNEL_BUDGET = "budget_alerts"
    const val CHANNEL_BILLS = "bill_reminders"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BUDGET, "تنبيهات الميزانية", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "تنبيه عند اقترابك أو تجاوزك حد ميزانيتك الشهرية"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BILLS, "تذكير الفواتير", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "تذكير قبل موعد فاتورة أو اشتراك متكرر بأيام قليلة"
            }
        )
    }

    // Checked before every notify() call — respects both the app's own toggle
    // (Settings → الإشعارات) and the system-level permission/opt-out, so a user
    // who denies the OS permission doesn't get a SecurityException crash instead.
    private fun canNotify(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences("mizan_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun send(ctx: Context, channel: String, id: Int, title: String, body: String) {
        if (!canNotify(ctx)) return
        ensureChannels(ctx)
        val notification = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the canNotify() check and this call
            // (e.g. the user just turned it off in system settings) — drop silently.
        }
    }

    fun notifyBudget(ctx: Context, id: Int, title: String, body: String) = send(ctx, CHANNEL_BUDGET, id, title, body)
    fun notifyBill(ctx: Context, id: Int, title: String, body: String) = send(ctx, CHANNEL_BILLS, id, title, body)
}
