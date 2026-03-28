package fi.junixald.NutellaService

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class CodeNotificationService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var updateJob: Job? = null

    private val channelId = "code_channel"
    private val notificationId = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground immediately to satisfy system requirements
        val initialNotification = createNotification("FL CODE", "Initializing...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ requires foreground service type in startForeground
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0 // Default or specific type if needed for older versions
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(notificationId, initialNotification, type)
                } else {
                    startForeground(notificationId, initialNotification)
                }
            } catch (e: Exception) {
                // Fallback for missing permissions or other foreground issues
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                     // On Android 12+, we might get ForegroundServiceStartNotAllowedException
                }
                startForeground(notificationId, initialNotification)
            }
        } else {
            startForeground(notificationId, initialNotification)
        }

        startUpdating()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Code Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the current FL code and time remaining"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // Prevent sound/vibration on every 3s update
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun startUpdating() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            val prefs = PreferencesManager(applicationContext)
            while (isActive) {
                try {
                    val secret = prefs.sharedSecret.first()
                    if (secret.isNotEmpty()) {
                        val currentTime = System.currentTimeMillis() / 1000
                        val code = CodeGenerator.generateFlCode(secret, currentTime)
                        val remaining = CodeGenerator.remainingTime(currentTime)
                        
                        val minutes = remaining / 60
                        val seconds = remaining % 60
                        
                        val title = "FL CODE"
                        val text = String.format("Code: %s\nNext in: %02dm %02ds", code, minutes, seconds)
                        
                        val notification = createNotification(title, text)
                        val notificationManager = getSystemService(NotificationManager::class.java)
                        notificationManager.notify(notificationId, notification)
                    }
                } catch (e: Exception) {
                    // Silently catch errors in update loop to prevent service crash
                }
                delay(3000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CodeNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CodeNotificationService::class.java)
            context.stopService(intent)
        }
    }
}
