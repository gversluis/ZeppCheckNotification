package nodomain.watcher.checkzeppnotificationJob

import android.app.job.JobParameters
import android.app.job.JobService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.Configuration
import rikka.shizuku.Shizuku
import rikka.shizuku.shared.BuildConfig
import java.util.concurrent.CompletableFuture

class NotificationAccessJobService : JobService() {
    companion object {
        private const val NOTIFICATION_SERVICE = "com.huami.watch.hmwatchmanager/com.xiaomi.hm.health.ui.smartplay.NotificationAccessService"
        private const val CHANNEL_ID = "notification_access_alert"
    }

    private lateinit var userServiceArgs: Shizuku.UserServiceArgs

    init {
        Configuration.Builder()
            .setJobSchedulerJobIdRange(0, 1000)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        userServiceArgs = Shizuku.UserServiceArgs(
            ComponentName(this@NotificationAccessJobService, ShellUserService::class.java)
        )
            .daemon(false)
            .processNameSuffix("shell_service")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
            .tag("check_zepp_notification")
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val packageName = NOTIFICATION_SERVICE.split("/").firstOrNull() ?: ""
        if (!isNotificationListenerEnabled(packageName)) {
            tryShizuku().thenAccept { success ->
                if (success) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.permission_restored, success),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    showNotification()
                }
            }
        }
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Return true to reschedule if job is interrupted
        // Shizuku.unbindUserService(userServiceArgs, connection, true)
        return true
    }

    private fun tryShizuku(): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (binder.pingBinder()) {
                    try {
                        val service = IUserService.Stub.asInterface(binder)
                        service.exec(
                            "cmd notification allow_listener "+NOTIFICATION_SERVICE
                        )
                        val serviceEscaped = Regex.escape(NOTIFICATION_SERVICE)
                        val result = service.exec(
                            "dumpsys notification | grep isPrimary | grep -o \"[:\\s]$serviceEscaped[:\\s]\""
                        )
                        future.complete(result.isNotEmpty())
                    } catch (e: Exception) {
                        future.complete(false)
                    }
                }
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }

        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Shizuku.bindUserService(userServiceArgs, connection)
            return future
        }
        future.complete(false)
        return future
    }

    private fun isNotificationListenerEnabled(packageName: String): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val names = enabledListeners.split(":")
        for (name in names) {
            val component = ComponentName.unflattenFromString(name)
            if (component != null && component.packageName == packageName) {
                return true
            }
        }
        return false
    }

    private fun showNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notification Access Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val settingsIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(1001, notification)
    }
}
