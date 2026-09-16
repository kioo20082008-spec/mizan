package com.mizan.money.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mizan.money.MoneyApp
import com.mizan.money.advisor.FinancialAdvisor
import java.util.Calendar
import java.util.concurrent.TimeUnit

// Runs once a day in the background (survives app-close and reboot, unlike a
// plain coroutine timer) and checks every enabled RecurringItemEntity against
// today's date. This is a calendar nudge, not real bill tracking: it doesn't
// try to confirm the bill actually posted, it just reminds the user it's
// usually around this time of month — keeping the feature simple on purpose.
class BillReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as MoneyApp
            val items = app.repository.recurringItemsDueSoon()
            if (items.isEmpty()) return Result.success()

            val today = Calendar.getInstance()
            val todayDay = today.get(Calendar.DAY_OF_MONTH)
            val daysInMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
            val monthKey = "%04d-%02d".format(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1)

            var notifId = NOTIF_ID_BASE
            items.forEach { item ->
                if (item.lastNotifiedMonthKey == monthKey) return@forEach
                // Handles the wraparound for a bill due early next month while
                // today is already late in the current one (e.g. due day 3,
                // today day 29 of a 30-day month → 4 days away, not negative).
                val daysUntil = if (item.expectedDayOfMonth >= todayDay) {
                    item.expectedDayOfMonth - todayDay
                } else {
                    (daysInMonth - todayDay) + item.expectedDayOfMonth
                }
                if (daysUntil in 0..3) {
                    val whenLabel = if (daysUntil == 0) "اليوم" else "خلال $daysUntil يوم"
                    NotificationHelper.notifyBill(
                        applicationContext, notifId++,
                        "فاتورة قربت 🔔",
                        "متوقع دفع «${item.merchant}» (~${FinancialAdvisor.fmt(item.expectedAmount)} ر.س) $whenLabel."
                    )
                    app.repository.updateRecurringItem(item.copy(lastNotifiedMonthKey = monthKey))
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val NOTIF_ID_BASE = 2000
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS).build()
            // KEEP: re-scheduling on every app launch (see MoneyApp.onCreate) must
            // not reset an already-running daily cycle.
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "bill_reminder_check", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
