package com.stereotip.simdata

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class UpdateActivity : AppCompatActivity() {

    private lateinit var tvCurrentVersion: TextView
    private lateinit var tvLatestVersion: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var progressUpdate: ProgressBar
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnDownloadUpdate: Button
    private lateinit var btnBackUpdate: Button

    private var latestApkUrl: String = ""
    private var currentVersionCode: Long = 0L
    private var currentVersionName: String = "לא ידוע"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)

        tvCurrentVersion = findViewById(R.id.tvCurrentVersion)
        tvLatestVersion = findViewById(R.id.tvLatestVersion)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)
        progressUpdate = findViewById(R.id.progressUpdate)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnDownloadUpdate = findViewById(R.id.btnDownloadUpdate)
        btnBackUpdate = findViewById(R.id.btnBackUpdate)

        loadCurrentVersion()

        tvCurrentVersion.text = "גרסה מותקנת: $currentVersionName ($currentVersionCode)"
        tvLatestVersion.text = "גרסה זמינה: --"
        tvUpdateStatus.text = "לחץ על בדוק עדכון"
        progressUpdate.visibility = View.GONE
        progressUpdate.isIndeterminate = false
        progressUpdate.max = 100
        progressUpdate.progress = 0
        btnDownloadUpdate.isEnabled = false

        btnCheckUpdate.setOnClickListener {
            checkUpdate()
        }

        btnDownloadUpdate.setOnClickListener {
            if (latestApkUrl.isBlank()) {
                Toast.makeText(this, "אין קישור להורדה", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            downloadAndInstallApk(latestApkUrl)
        }

        btnBackUpdate.setOnClickListener {
            finish()
        }
    }

    private fun loadCurrentVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            currentVersionName = packageInfo.versionName ?: "לא ידוע"

            currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            currentVersionName = "לא ידוע"
            currentVersionCode = 0L
        }
    }

    private fun checkUpdate() {
        progressUpdate.visibility = View.VISIBLE
        progressUpdate.isIndeterminate = true
        tvUpdateStatus.text = "בודק עדכון..."
        btnCheckUpdate.isEnabled = false
        btnDownloadUpdate.isEnabled = false
        latestApkUrl = ""

        thread {
            val result = UpdateManager.fetchUpdateInfo()

            runOnUiThread {
                progressUpdate.visibility = View.GONE
                progressUpdate.isIndeterminate = false
                btnCheckUpdate.isEnabled = true

                result.onSuccess { info ->
                    tvLatestVersion.text = "גרסה זמינה: ${info.versionName}"
                    latestApkUrl = info.apkUrl

                    if (isRemoteVersionNewer(info.versionName, currentVersionName)) {
                        tvUpdateStatus.text = "${info.title}\n${info.message}"
                        btnDownloadUpdate.isEnabled = latestApkUrl.isNotBlank()
                    } else {
                        tvUpdateStatus.text = "האפליקציה מעודכנת"
                        btnDownloadUpdate.isEnabled = false
                    }
                }.onFailure {
                    tvLatestVersion.text = "גרסה זמינה: --"
                    tvUpdateStatus.text = "שגיאה בבדיקת עדכון"
                    latestApkUrl = ""
                    btnDownloadUpdate.isEnabled = false
                }
            }
        }
    }

    private fun downloadAndInstallApk(apkUrl: String) {
        btnCheckUpdate.isEnabled = false
        btnDownloadUpdate.isEnabled = false
        btnBackUpdate.isEnabled = false

        progressUpdate.visibility = View.VISIBLE
        progressUpdate.isIndeterminate = false
        progressUpdate.max = 100
        progressUpdate.progress = 0
        tvUpdateStatus.text = "מוריד עדכון... 0%"

        thread {
            try {
                val downloadDir = File(getExternalFilesDir(null), "Download")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                val apkFile = File(downloadDir, "app-update.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                val url = URL(apkUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    doInput = true
                }

                connection.connect()

                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("HTTP ${connection.responseCode}")
                }

                val totalSize = connection.contentLengthLong
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var lastProgress = -1

                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break

                            output.write(buffer, 0, read)
                            downloaded += read

                            if (totalSize > 0L) {
                                val progress = ((downloaded * 100) / totalSize).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    runOnUiThread {
                                        progressUpdate.isIndeterminate = false
                                        progressUpdate.progress = progress
                                        tvUpdateStatus.text = "מוריד עדכון... $progress%"
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    progressUpdate.isIndeterminate = true
                                    tvUpdateStatus.text = "מוריד עדכון..."
                                }
                            }
                        }
                    }
                }

                connection.disconnect()

                runOnUiThread {
                    progressUpdate.isIndeterminate = false
                    progressUpdate.progress = 100
                    tvUpdateStatus.text = "ההורדה הושלמה, פותח התקנה..."
                    btnCheckUpdate.isEnabled = true
                    btnBackUpdate.isEnabled = true
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressUpdate.visibility = View.GONE
                    progressUpdate.isIndeterminate = false
                    btnCheckUpdate.isEnabled = true
                    btnDownloadUpdate.isEnabled = true
                    btnBackUpdate.isEnabled = true
                    tvUpdateStatus.text = "שגיאה בהורדת העדכון"
                    Toast.makeText(this, "שגיאה בהורדת העדכון", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun installApk(apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(this, "קובץ העדכון לא נמצא", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Toast.makeText(this, "יש לאשר התקנה ממקור זה", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(intent)
    }

    private fun isRemoteVersionNewer(remoteVersion: String, localVersion: String): Boolean {
        val remoteParts = remoteVersion
            .removePrefix("v")
            .split(".")
            .mapNotNull { it.toIntOrNull() }

        val localParts = localVersion
            .removePrefix("v")
            .split(".")
            .mapNotNull { it.toIntOrNull() }

        val maxSize = maxOf(remoteParts.size, localParts.size)

        for (i in 0 until maxSize) {
            val remote = remoteParts.getOrNull(i) ?: 0
            val local = localParts.getOrNull(i) ?: 0

            if (remote > local) return true
            if (remote < local) return false
        }

        return false
    }
}