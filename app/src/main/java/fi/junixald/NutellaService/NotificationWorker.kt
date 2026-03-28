package fi.junixald.NutellaService

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        val secret = prefs.sharedSecret.first()
        
        if (secret.isNotEmpty()) {
            showNotification(secret)
        }

        return Result.success()
    }

    private fun showNotification(secret: String) {
        val channelId = "code_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Code Notifications", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val currentTime = System.currentTimeMillis() / 1000
        val code = CodeGenerator.generateFlCode(secret, currentTime)
        val remaining = CodeGenerator.remainingTime(currentTime)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Your Code: $code")
            .setContentText("Expires in ${remaining / 60}m ${remaining % 60}s")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        notificationManager.notify(1, notification)
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .addTag("notification_work")
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "notification_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("notification_work")
        }
    }
}
