package nodomain.watcher.checkzeppnotificationJob

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
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.shared.BuildConfig
import java.util.concurrent.CompletableFuture

class NotificationAccess(private val applicationContext: Context) {
    companion object {
        private const val NOTIFICATION_SERVICE = "com.huami.watch.hmwatchmanager/com.xiaomi.hm.health.ui.smartplay.NotificationAccessService"
        private const val CHANNEL_ID = "notification_access_alert"
        private lateinit var userServiceArgs: Shizuku.UserServiceArgs
    }

    fun prepare() {
        userServiceArgs = Shizuku.UserServiceArgs(
            ComponentName(applicationContext, ShellUserService::class.java)
        )
            .daemon(false)
            .processNameSuffix("shell_service")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
            .tag("check_zepp_notification")
    }

    fun check() {
        if (!isNotificationListenerEnabled()) {
            tryShizuku().thenAccept { success ->
                if (success) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            applicationContext,
                            applicationContext.getString(R.string.permission_restored, success),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    showNotification()
                }
            }
        }
    }

    private fun getPackageName(): String {
        return NOTIFICATION_SERVICE.split("/").firstOrNull() ?: ""
    }

    private fun tryShizuku(): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (binder.pingBinder()) {
                    try {
                        val service = IUserService.Stub.asInterface(binder)
                        service.exec(
                            "cmd notification allow_listener $NOTIFICATION_SERVICE"
                        )
                        val serviceEscaped = Regex.escape(NOTIFICATION_SERVICE)
                        val result = service.exec(
                            "dumpsys notification | grep isPrimary | grep -o \"[:\\s]$serviceEscaped[:\\s]\""
                        )
                        Log.d(this.toString(), result)
                        future.complete(result.isNotEmpty())
                    } catch (e: Exception) {
                        Log.e(this.toString(), "Failed to allow notification service: $e")
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

    fun isNotificationListenerEnabled(): Boolean {
        val packageName = getPackageName()
        val enabledListeners = Settings.Secure.getString(
            applicationContext.contentResolver,
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
        val manager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

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
            applicationContext, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText(applicationContext.getString(R.string.notification_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(1001, notification)
    }

}