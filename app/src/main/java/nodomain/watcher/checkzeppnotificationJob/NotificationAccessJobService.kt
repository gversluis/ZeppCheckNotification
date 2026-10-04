package nodomain.watcher.checkzeppnotificationJob

import android.app.job.JobParameters
import android.app.job.JobService
import androidx.work.Configuration

class NotificationAccessJobService : JobService() {
    companion object {
        private lateinit var notificationAccess: NotificationAccess
    }

    init {
        Configuration.Builder()
            .setJobSchedulerJobIdRange(0, 1000)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        notificationAccess = NotificationAccess(applicationContext)
        notificationAccess.prepare()
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        notificationAccess.check()
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Return true to reschedule if job is interrupted
        // Shizuku.unbindUserService(userServiceArgs, connection, true)
        return true
    }
}
