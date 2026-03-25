package com.stereotip.simdata

import android.app.DownloadManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MissingDialerActivity : AppCompatActivity() {

    private lateinit var logo: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPercent: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnInstall: Button
    private lateinit var btnRetry: Button

    private var activeDownloadId: Long = -1L
    private var monitorThreadStarted = false
    private var downloadedLocalUri: String? = null
    private var waitingInstallerResult = false
    private var openedDialerAfterInstall = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasDialerApp()) {
            continueToMain()
            return
        }

        setContentView(R.layout.activity_missing_dialer)

        logo = findViewById(R.id.logoMissingDialer)
        tvTitle = findViewById(R.id.tvMissingDialerTitle)
        tvSubtitle = findViewById(R.id.tvMissingDialerSubtitle)
        tvStatus = findViewById(R.id.tvMissingDialerStatus)
        tvPercent = findViewById(R.id.tvMissingDialerPercent)
        progressBar = findViewById(R.id.progressMissingDialer)
        btnInstall = findViewById(R.id.btnInstallMissingDialer)
        btnRetry = findViewById(R.id.btnRetryMissingDialer)

        btnInstall.isEnabled = false
        btnInstall.alpha = 0.55f

        btnInstall.setOnClickListener {
            if (downloadedLocalUri.isNullOrBlank()) {
                Toast.makeText(this, "הקובץ עדיין לא מוכן להתקנה", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!UpdateManager.canRequestPackageInstalls(this)) {
                Toast.makeText(
                    this,
                    "יש לאשר התקנה ממקור זה פעם אחת ואז לחזור למסך",
                    Toast.LENGTH_LONG
                ).show()
                UpdateManager.openUnknownAppsSettings(this)
                return@setOnClickListener
            }

            waitingInstallerResult = true
            UpdateManager.installDownloadedApk(
                context = this,
                localUri = downloadedLocalUri,
                fallbackFileName = REQUIRED_DIALER_FILE_NAME
            )
        }

        btnRetry.setOnClickListener {
            startDialerDownload()
        }

        startDialerDownload()
    }

    override fun onResume() {
        super.onResume()

        if (!waitingInstallerResult) return

        if (hasDialerApp()) {
            if (!openedDialerAfterInstall) {
                openedDialerAfterInstall = true
                tvStatus.text = "החייגן הותקן בהצלחה, פותח את אפליקציית החיוג..."
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        startActivity(Intent(Intent.ACTION_DIAL))
                    } catch (_: Exception) {
                    }
                }, 400)
                return
            }

            continueToMain()
        }
    }

    private fun startDialerDownload() {
        try {
            activeDownloadId = UpdateManager.startDownload(
                context = this,
                apkUrl = REQUIRED_DIALER_APK_URL,
                fileName = REQUIRED_DIALER_FILE_NAME,
                title = "מוריד קבצי חיוג נחוצים",
                description = "מוריד אפליקציית חיוג תואמת למכשיר..."
            )

            downloadedLocalUri = null
            monitorThreadStarted = false
            waitingInstallerResult = false
            openedDialerAfterInstall = false

            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = false
            progressBar.progress = 0
            tvPercent.text = "0%"
            tvStatus.text = "מתחיל הורדה..."
            btnInstall.isEnabled = false
            btnInstall.alpha = 0.55f
            btnRetry.visibility = View.GONE

            startProgressMonitor()
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            tvPercent.text = "--"
            tvStatus.text = "שגיאה בהתחלת ההורדה"
            btnRetry.visibility = View.VISIBLE
            Toast.makeText(this, "שגיאה בהתחלת ההורדה", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startProgressMonitor() {
        if (monitorThreadStarted) return
        monitorThreadStarted = true

        thread {
            var running = true
            while (running && !isFinishing && !isDestroyed) {
                val progress = UpdateManager.queryProgress(this, activeDownloadId)

                runOnUiThread {
                    when (progress.status) {
                        DownloadManager.STATUS_PENDING -> {
                            progressBar.visibility = View.VISIBLE
                            progressBar.isIndeterminate = true
                            tvStatus.text = "ממתין לתחילת ההורדה..."
                            tvPercent.text = "--"
                        }

                        DownloadManager.STATUS_RUNNING -> {
                            progressBar.visibility = View.VISIBLE
                            progressBar.isIndeterminate = false
                            progressBar.progress = progress.percent
                            tvPercent.text = "${progress.percent}%"

                            val downloadedMb = progress.bytesDownloaded / (1024f * 1024f)
                            val totalMb = if (progress.bytesTotal > 0) progress.bytesTotal / (1024f * 1024f) else 0f

                            tvStatus.text =
                                "מוריד את הקבצים הנחוצים... ${String.format("%.1f", downloadedMb)} / ${String.format("%.1f", totalMb)} MB"
                        }

                        DownloadManager.STATUS_SUCCESSFUL -> {
                            progressBar.visibility = View.VISIBLE
                            progressBar.isIndeterminate = false
                            progressBar.progress = 100
                            tvPercent.text = "100%"
                            tvStatus.text = "ההורדה הושלמה. לחץ התקנה כדי להמשיך"
                            downloadedLocalUri = progress.localUri
                            btnInstall.isEnabled = true
                            btnInstall.alpha = 1f
                            btnRetry.visibility = View.GONE
                            monitorThreadStarted = false
                            running = false
                        }

                        DownloadManager.STATUS_FAILED -> {
                            progressBar.visibility = View.GONE
                            tvPercent.text = "--"
                            tvStatus.text = "ההורדה נכשלה. בדוק אינטרנט ונסה שוב"
                            btnInstall.isEnabled = false
                            btnInstall.alpha = 0.55f
                            btnRetry.visibility = View.VISIBLE
                            monitorThreadStarted = false
                            running = false
                        }

                        DownloadManager.STATUS_PAUSED -> {
                            progressBar.visibility = View.VISIBLE
                            progressBar.isIndeterminate = false
                            progressBar.progress = progress.percent
                            tvPercent.text = "${progress.percent}%"
                            tvStatus.text = "ההורדה הושהתה..."
                        }
                    }
                }

                Thread.sleep(500)
            }
        }
    }

    private fun hasDialerApp(): Boolean {
        val intent = Intent(Intent.ACTION_DIAL)
        return intent.resolveActivity(packageManager) != null
    }

    private fun continueToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SKIP_DIALER_CHECK, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        private const val REQUIRED_DIALER_FILE_NAME = "required_dialer.apk"

        // אם שם הקובץ אצלך שונה, תחליף רק את הלינק הזה
        private const val REQUIRED_DIALER_APK_URL =
            "https://github.com/s035785178-stack/simdata/releases/download/dialer/dialer.apk"
    }
}