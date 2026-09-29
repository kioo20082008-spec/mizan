package com.mizan.money.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mizan.money.MoneyApp
import com.mizan.money.cloud.CloudBackup
import java.util.concurrent.TimeUnit

// Once a day, uploads a full snapshot to the user's cloud backup. Does nothing
// unless cloud backup is configured and the user is signed in (Settings ->
// Backup). Runs only when the network is available; WorkManager retries later.
class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            if (!CloudBackup.isConfigured || !CloudBackup.isSignedIn) return Result.success()
            val dao = (applicationContext as MoneyApp).db.backupDao()
            val data = BackupData(
                transactions = dao.transactions(),
                budgets = dao.budgets(),
                goals = dao.goals(),
                debts = dao.debts(),
                recurringItems = dao.recurringItems(),
                goalContributions = dao.goalContributions(),
            )
            // Never overwrite a good cloud backup with an empty database.
            if (data.transactions.isEmpty() && data.budgets.isEmpty() && data.goals.isEmpty() && data.debts.isEmpty())
                return Result.success()
            if (CloudBackup.upload(data).isSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e("Mizan", "daily cloud backup failed", e)
            Result.retry()
        }
    }

    companion object {
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "auto_backup_daily", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
