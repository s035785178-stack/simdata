package com.stereotip.simdata

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
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

    private val apkUrl = "https://github.com/s035785178-stack/simdata/releases/download/dialer/dialer.apk"
    private val apkName = "dialer.apk"

    private var downloadedApkFile: File? = null
    private var waitingForInstallPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_required_apps)

        btnInstall = findViewById(R.id.btnInstall)
        txtProgress = findViewById(R.id.txtProgress)

        btnInstall.text = "התקן חייגן"

        btnInstall.setOnClickListener {
            val file = File(getExternalFilesDir(null), "Download/$apkName")

            // אם כבר הורד → לא מוריד שוב
            if (file.exists()) {
                btnInstall.text = "פותח התקנה..."
                installDownloadedApk(file)
            } else {
                downloadDialerApk()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // חזרה מהרשאת התקנה
        if (waitingForInstallPermission) {
            waitingForInstallPermission = false
            downloadedApkFile?.let { file ->
                if (file.exists()) {
                    openApkInstall(file)
                }
            }
            return
        }

        // אם כבר יש חייגן → ממשיך לאפליקציה
        if (hasDialer()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun downloadDialerApk() {
        btnInstall.isEnabled = false
        btnInstall.text = "מוריד..."
        txtProgress.visibility = TextView.VISIBLE
        txtProgress.text = "0%"

        Thread {
            try {
                val connection = URL(apkUrl).openConnection()
                connection.connect()

                val file = File(getExternalFilesDir(null), "Download/$apkName")
                file.parentFile?.mkdirs()

                val input = connection.getInputStream()
                val output = FileOutputStream(file)

                val buffer = ByteArray(4096)
                var total = 0L
                val fileLength = connection.contentLength

                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break

                    total += count
                    output.write(buffer, 0, count)

                    if (fileLength > 0) {
                        val progress = ((total * 100) / fileLength).toInt()
                        runOnUiThread {
                            txtProgress.text = "$progress%"
                        }
                    }
                }

                output.flush()
                output.close()
                input.close()

                downloadedApkFile = file

                runOnUiThread {
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
        // בדיקת הרשאת התקנה
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            waitingForInstallPermission = true
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

            startActivity(intent)

        } catch (e: Exception) {
            btnInstall.isEnabled = true
            btnInstall.text = "נסה שוב"
            Toast.makeText(this, "שגיאה בפתיחת התקנה", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasDialer(): Boolean {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:*019"))
        val resolve = packageManager.resolveActivity(intent, 0)
        return resolve != null
    }
}