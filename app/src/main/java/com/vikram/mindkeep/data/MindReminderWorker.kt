package com.vikram.mindkeep.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class MindReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val id = inputData.getString(KEY_ID).orEmpty()
        val channelId = "mindkeep_reminders"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders created in MindKeep"
                }
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MindKeep reminder")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return try {
            NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
            Result.success()
        } catch (_: SecurityException) {
            // The reminder remains saved in the app when notification permission is denied.
            Result.success()
        }
    }

    companion object {
        const val KEY_TITLE = "reminder_title"
        const val KEY_ID = "reminder_id"
    }
}
