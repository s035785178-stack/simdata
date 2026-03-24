package com.stereotip.simdata

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
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
    private var forceMode: Boolean = false

    private val currentVersionCode: Int by lazy { resolveCurrentVersionCode() }
    private val currentVersionName: String by lazy { resolveCurrentVersionName() }

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

        forceMode = intent.getBooleanExtra(EXTRA_FORCE_MODE, false)

        tvCurrentVersion.text = "גרסה מותקנת: $currentVersionName ($currentVersionCode)"
        tvLatestVersion.text = "גרסה זמינה: --"
        tvUpdateStatus.text = if (forceMode) {
            "נדרש לעדכן כדי להמשיך להשתמש באפליקציה"
        } else {
            "בודק עדכון..."
        }

        btnCheckUpdate.setOnClickListener {
            checkUpdate(showAlreadyUpdatedToast = true)
        }

        btnDownloadUpdate.setOnClickListener {
            if (latestApkUrl.isBlank()) {
                Toast.makeText(this, "אין קישור להורדה", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestApkUrl)))
        }

        btnBackUpdate.setOnClickListener {
            if (forceMode) {
                finishAffinity()
            } else {
                finish()
            }
        }

        applyForceModeUi()
        checkUpdate(showAlreadyUpdatedToast = false)
    }

    override fun onBackPressed() {
        if (forceMode) {
            finishAffinity()
        } else {
            super.onBackPressed()
        }
    }

    private fun applyForceModeUi() {
        btnBackUpdate.text = if (forceMode) "סגור" else "⬅ חזרה"
    }

    private fun checkUpdate(showAlreadyUpdatedToast: Boolean) {
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
                    tvLatestVersion.text = "גרסה זמינה: ${info.versionName} (${info.versionCode})"
                    latestApkUrl = info.apkUrl

                    if (info.versionCode > currentVersionCode) {
                        forceMode = info.forceUpdate
                        applyForceModeUi()
                        tvUpdateStatus.text = if (info.forceUpdate) {
                            "זהו עדכון חובה — יש להתקין כדי להמשיך"
                        } else {
                            "יש עדכון חדש זמין"
                        }
                        btnDownloadUpdate.isEnabled = true
                    } else {
                        forceMode = false
                        applyForceModeUi()
                        tvUpdateStatus.text = "האפליקציה מעודכנת"
                        btnDownloadUpdate.isEnabled = false
                        if (showAlreadyUpdatedToast) {
                            Toast.makeText(this, "האפליקציה כבר מעודכנת", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.onFailure {
                    tvUpdateStatus.text = "שגיאה בבדיקת עדכון"
                    if (forceMode) {
                        Toast.makeText(this, "לא הצלחנו לבדוק עדכון חובה כרגע", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun resolveCurrentVersionCode(): Int {
        val packageInfo = packageInfoCompat()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    private fun resolveCurrentVersionName(): String {
        return packageInfoCompat().versionName ?: "לא ידוע"
    }

    private fun packageInfoCompat(): PackageInfo {
        return packageManager.getPackageInfo(packageName, 0)
    }

    companion object {
        const val EXTRA_FORCE_MODE = "force_mode"
    }
}
