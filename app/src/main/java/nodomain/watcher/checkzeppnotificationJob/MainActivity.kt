package nodomain.watcher.checkzeppnotificationJob

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class MainActivity : AppCompatActivity() {
    companion object {
        const val SHIZUKU_REQUEST_CODE = 1001
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            grantedNotificationAccess()
        }
    }

    private var requestedShizuku = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        scheduleJob()
        setContentView(R.layout.activity_main)
        createViewTextLinks()
        Shizuku.addRequestPermissionResultListener(permissionListener)
        checkShizukuAndRequest()
    }

    override fun onResume() {
        super.onResume()
        checkShizukuAndRequest()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun scheduleJob() {
        Thread {
            val componentName = ComponentName(this, NotificationAccessJobService::class.java)
            var period = 60 * 1_000L   // 60s
            if (JobInfo.getMinPeriodMillis() > period) period =
                JobInfo.getMinPeriodMillis()    // I read that minimum is 15m
            val jobInfo = JobInfo.Builder(123, componentName)
                .setPeriodic(period)
                .setPersisted(true)
                .build()

            val jobScheduler = getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.schedule(jobInfo)
        }.start()
    }

    fun createViewTextLinks() {
        val textView = findViewById<TextView>(R.id.textView)
        val text = textView.text.toString()
        val permissionString = getString(R.string.permission)
        val spannableString = SpannableString(text)
        var start = text.indexOf(permissionString)
        while(start>=0) {
            val end = start + permissionString.length
            spannableString.setSpan(TextLinkClicked(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            textView.text = spannableString
            textView.movementMethod = LinkMovementMethod.getInstance()
            start = text.indexOf(permissionString, ++start)
        }
    }

    private class TextLinkClicked(): ClickableSpan() {
        override fun onClick(view: View) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            view.context.startActivity(intent)
        }
    }

    private fun grantedNotificationAccess() {
        findViewById<Button>(R.id.button).visibility = View.GONE
        findViewById<CheckBox>(R.id.shizukuEnabled).visibility = View.VISIBLE
    }

    private fun requestShizuku() {
        if (!requestedShizuku) Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        requestedShizuku = true
    }

    private fun checkShizukuAndRequest() {
        val button = findViewById<Button>(R.id.button)
        button.visibility = View.VISIBLE
        button.setOnClickListener { requestedShizuku=false; checkShizukuAndRequest() }
        findViewById<CheckBox>(R.id.shizukuEnabled).visibility = View.GONE
        if (!Shizuku.pingBinder()) {
            button.isEnabled = false
            button.text = getString(R.string.shizuku_not_available)
            return
        }
        when {
            ContextCompat.checkSelfPermission(applicationContext, ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> grantedNotificationAccess()
            Shizuku.shouldShowRequestPermissionRationale() -> Toast.makeText(applicationContext, getString(R.string.shizuku_permission_denied),Toast.LENGTH_LONG).show()
            else -> requestShizuku()
        }
    }

}
