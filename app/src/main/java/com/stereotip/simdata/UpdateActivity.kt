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
    private lateinit var tvUpdateStatus: TextView
    private lateinit var progressUpdate: ProgressBar
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnDownloadUpdate: Button
    private lateinit var btnBackUpdate: Button

    private var latestApkUrl: String = ""
    private val currentVersionCode = 2
    private val currentVersionName = "1.06"

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

        tvCurrentVersion.text = "גרסה מותקנת: $currentVersionName ($currentVersionCode)"
        tvLatestVersion.text = "גרסה זמינה: --"
        tvUpdateStatus.text = "לחץ על בדוק עדכון"

        btnCheckUpdate.setOnClickListener {
            checkUpdate()
        }

        btnDownloadUpdate.setOnClickListener {
            if (latestApkUrl.isBlank()) {
                Toast.makeText(this, "אין קישור להורדה", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestApkUrl)))
        }

        btnBackUpdate.setOnClickListener {
            finish()
        }
    }

    private fun checkUpdate() {
        progressUpdate.visibility = View.VISIBLE
        tvUpdateStatus.text = "בודק עדכון..."
        btnCheckUpdate.isEnabled = false
        btnDownloadUpdate.isEnabled = false

        thread {
            val result = UpdateManager.fetchUpdateInfo()

            runOnUiThread {
                progressUpdate.visibility = View.GONE
                btnCheckUpdate.isEnabled = true

                result.onSuccess { info ->
                    tvLatestVersion.text =
                        "גרסה זמינה: ${info.versionName} (${info.versionCode})"
                    latestApkUrl = info.apkUrl

                    if (info.versionCode > currentVersionCode) {
                        tvUpdateStatus.text = "יש עדכון חדש"
                        btnDownloadUpdate.isEnabled = true
                    } else {
                        tvUpdateStatus.text = "האפליקציה מעודכנת"
                        btnDownloadUpdate.isEnabled = false
                    }
                }.onFailure {
                    tvUpdateStatus.text = "שגיאה בבדיקת עדכון"
                }
            }
        }
    }
}
