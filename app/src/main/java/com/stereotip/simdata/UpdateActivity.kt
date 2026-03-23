package com.stereotip.simdata

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class UpdateActivity : AppCompatActivity() {

    private lateinit var tvCurrentVersion: TextView
    private lateinit var tvLatestVersion: TextView
    private lateinit var tvUpdateMessage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnDownloadUpdate: Button
    private lateinit var btnBackUpdate: Button

    private var latestApkUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)

        tvCurrentVersion = findViewById(R.id.tvCurrentVersion)
        tvLatestVersion = findViewById(R.id.tvLatestVersion)
        tvUpdateMessage = findViewById(R.id.tvUpdateMessage)
        progressBar = findViewById(R.id.progressUpdate)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnDownloadUpdate = findViewById(R.id.btnDownloadUpdate)
        btnBackUpdate = findViewById(R.id.btnBackUpdate)

        tvCurrentVersion.text = "הגרסה הנוכחית: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        tvLatestVersion.text = "הגרסה החדשה: עדיין לא נבדק"
        tvUpdateMessage.text = "לחץ על בדוק עדכון"

        btnDownloadUpdate.isEnabled = false

        btnCheckUpdate.setOnClickListener {
            checkUpdate()
        }

        btnDownloadUpdate.setOnClickListener {
            if (latestApkUrl.isBlank()) {
                Toast.makeText(this, "לא נמצא קישור לעדכון", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(latestApkUrl))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "שגיאה בפתיחת קישור העדכון", Toast.LENGTH_SHORT).show()
            }
        }

        btnBackUpdate.setOnClickListener {
            finish()
        }
    }

    private fun checkUpdate() {
        progressBar.visibility = View.VISIBLE
        btnCheckUpdate.isEnabled = false
        btnDownloadUpdate.isEnabled = false
        tvUpdateMessage.text = "בודק עדכון..."

        thread {
            val result = UpdateManager.checkForUpdate()

            runOnUiThread {
                progressBar.visibility = View.GONE
                btnCheckUpdate.isEnabled = true

                result.onSuccess { info ->
                    tvLatestVersion.text =
                        "הגרסה החדשה: ${info.latestVersionName} (${info.latestVersionCode})"
                    tvUpdateMessage.text = info.message
                    latestApkUrl = info.apkUrl

                    if (info.latestVersionCode > BuildConfig.VERSION_CODE && info.apkUrl.isNotBlank()) {
                        btnDownloadUpdate.isEnabled = true
                        btnDownloadUpdate.text = "הורד והתקן עדכון"
                    } else {
                        btnDownloadUpdate.isEnabled = false
                        btnDownloadUpdate.text = "אין עדכון זמין"
                    }
                }.onFailure {
                    tvUpdateMessage.text = "לא ניתן לבדוק עדכון כרגע"
                    Toast.makeText(this, "שגיאה בבדיקת עדכון", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
