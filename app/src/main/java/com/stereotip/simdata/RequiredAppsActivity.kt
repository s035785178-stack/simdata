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
import java.io.FileOutputStream
import java.net.URL

class RequiredAppsActivity : AppCompatActivity() {

    private lateinit var btnInstall: Button
    private lateinit var txtProgress: TextView
    private lateinit var progressBar: ProgressBar

    private val apkUrl = "https://github.com/s035785178-stack/simdata/releases/download/dialer/dialer.apk"
    private val apkName = "dialer.apk"

    private var downloadedApkFile: File? = null
    private var waitingForInstallPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_required_apps)

        btnInstall = findViewById(R.id.btnInstall)
        txtProgress = findViewById(R.id.txtProgress)
        progressBar = findViewById(R.id.progressBar)

        resetDownloadUi()

        btnInstall.setOnClickListener {
            downloadDialerApk()
        }
    }

    override fun onResume() {
        super.onResume()

        if (waitingForInstallPermission) {
            waitingForInstallPermission = false
            downloadedApkFile?.let { file ->
                if (file.exists()) {
                    openApkInstall(file)
                }
            }
            return
        }

        if (hasDialer()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun downloadDialerApk() {
        btnInstall.isEnabled = false
        btnInstall.text = "מוריד..."
        txtProgress.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        txtProgress.text = "0%"
        progressBar.progress = 0

        Thread {
            try {
                val file = File(getExternalFilesDir(null), "Download/$apkName")
                file.parentFile?.mkdirs()

                if (file.exists()) {
                    file.delete()
                }

                val connection = URL(apkUrl).openConnection()
                connection.connect()

                val input = connection.getInputStream()
                val output = FileOutputStream(file, false)

                val buffer = ByteArray(4096)
                var total = 0L
                val fileLength = connection.contentLength

                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break

                    total += count
                    output.write(buffer, 0, count)

                    if (fileLength > 0) {
                        val progress = ((total * 100) / fileLength).toInt().coerceIn(0, 100)
                        runOnUiThread {
                            progressBar.progress = progress
                            txtProgress.text = "$progress%"
                        }
                    }
                }

                output.flush()
                output.close()
                input.close()

                downloadedApkFile = file

                runOnUiThread {
                    progressBar.progress = 100
                    txtProgress.text = "100%"
                    btnInstall.text = "מתקין..."
                    installDownloadedApk(file)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    btnInstall.isEnabled = true
                    btnInstall.text = "נסה שוב"
                    Toast.makeText(this, "שגיאה בהורדה", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun installDownloadedApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            waitingForInstallPermission = true
            btnInstall.isEnabled = true
            btnInstall.text = "אשר הרשאה והמשך"
            Toast.makeText(this, "אשר התקנה ממקור זה", Toast.LENGTH_LONG).show()

            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            return
        }

        openApkInstall(file)
    }

    private fun openApkInstall(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file
            )

            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }

            btnInstall.isEnabled = true
            btnInstall.text = "הורד שוב אם נכשל"

            startActivity(intent)

        } catch (e: Exception) {
            btnInstall.isEnabled = true
            btnInstall.text = "נסה שוב"
            Toast.makeText(this, "שגיאה בפתיחת התקנה", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetDownloadUi() {
        btnInstall.isEnabled = true
        btnInstall.text = "התקן חייגן"
        txtProgress.visibility = View.GONE
        txtProgress.text = "0%"
        progressBar.visibility = View.GONE
        progressBar.progress = 0
    }

    private fun hasDialer(): Boolean {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:*019"))
        val resolve = packageManager.resolveActivity(intent, 0)
        return resolve != null
    }
}