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
import com.stereotip.simdata.util.RequiredApps
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class RequiredAppsActivity : AppCompatActivity() {

    private lateinit var btnInstallDialer: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var txtProgress: TextView
    private lateinit var txtStatus: TextView

    private var isWorking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_required_apps)

        btnInstallDialer = findViewById(R.id.btnInstallDialer)
        progressBar = findViewById(R.id.progressBar)
        txtProgress = findViewById(R.id.txtProgress)
        txtStatus = findViewById(R.id.txtStatus)

        btnInstallDialer.setOnClickListener {
            if (!isWorking) {
                startDialerInstallFlow()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (RequiredApps.isRequiredDialerAvailable(this)) {
            openMainApp()
        } else if (!isWorking) {
            resetIdleState()
        }
    }

    private fun startDialerInstallFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            txtStatus.text = "כדי להשלים התקנה, אשר התקנה ממקור זה וחזור לאפליקציה."
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        isWorking = true
        btnInstallDialer.isEnabled = false
        btnInstallDialer.text = "מוריד חייגן..."
        txtStatus.text = "מוריד קובץ התקנה מאובטח מהשרת"
        progressBar.visibility = View.VISIBLE
        txtProgress.visibility = View.VISIBLE
        progressBar.progress = 0
        txtProgress.text = "0%"

        Thread {
            try {
                val apkFile = downloadDialerApk()
                runOnUiThread {
                    btnInstallDialer.text = "פותח התקנה..."
                    txtStatus.text = "ההורדה הושלמה. אשר התקנה במסך הבא."
                    txtProgress.text = "100%"
                    progressBar.progress = 100
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isWorking = false
                    btnInstallDialer.isEnabled = true
                    btnInstallDialer.text = "נסה שוב"
                    txtStatus.text = "ההורדה נכשלה. בדוק אינטרנט ונסה שוב."
                    Toast.makeText(this, "שגיאה בהורדת החייגן", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun downloadDialerApk(): File {
        val targetDir = File(cacheDir, "required_apps")
        if (!targetDir.exists()) targetDir.mkdirs()

        val targetFile = File(targetDir, RequiredApps.DIALER_APK_FILE_NAME)
        if (targetFile.exists()) targetFile.delete()

        val connection = URL(RequiredApps.DIALER_APK_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Download failed: ${connection.responseCode}")
        }

        val totalBytes = connection.contentLength
        var downloadedBytes = 0L

        connection.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read

                    if (totalBytes > 0) {
                        val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        runOnUiThread {
                            progressBar.progress = percent
                            txtProgress.text = "$percent%"
                        }
                    }
                }
            }
        }

        connection.disconnect()
        return targetFile
    }

    private fun installApk(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(intent)
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun resetIdleState() {
        progressBar.visibility = View.GONE
        txtProgress.visibility = View.GONE
        progressBar.progress = 0
        txtProgress.text = "0%"
        btnInstallDialer.isEnabled = true
        btnInstallDialer.text = "הורד והתקן חייגן"
        txtStatus.text = "חסר חייגן במערכת. בלי החייגן לא ניתן לבצע בדיקת יתרה בקו."
    }
}
