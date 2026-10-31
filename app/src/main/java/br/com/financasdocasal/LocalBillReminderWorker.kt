package br.com.financasdocasal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Posts a single local reminder; it never changes or records bill payment state. */
class LocalBillReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Lembretes de contas", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(applicationContext, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(applicationContext)
        }.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Conta vence em breve")
            .setContentText("Uma conta cadastrada vence em breve. Abra o app para ver detalhes.")
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(inputData.getInt(KEY_NOTIFICATION_ID, 0), notification) }
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "bill_due_reminders"
        private const val KEY_NOTIFICATION_ID = "notification_id"

        /** Call after saving a bill. Replaces that bill's prior reminder, preventing duplicates. */
        fun schedule(context: Context, bill: RecurringBill) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            val uniqueName = workName(bill.id)
            if (!bill.isActive || bill.nextDate.isBlank()) {
                workManager.cancelUniqueWork(uniqueName)
                return
            }
            val due = runCatching { LocalDate.parse(bill.nextDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull() ?: return
            val delayMillis = (due - TimeUnit.HOURS.toMillis(24) - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<LocalBillReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putInt(KEY_NOTIFICATION_ID, bill.id.hashCode()).build())
                .build()
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, billId: String) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(billId))
        }

        private fun workName(id: String) = "bill-reminder-$id"
    }
}
