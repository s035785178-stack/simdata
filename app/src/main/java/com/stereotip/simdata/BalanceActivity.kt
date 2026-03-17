package com.stereotip.simdata

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class BalanceActivity : AppCompatActivity() {

    private lateinit var tvProgress: TextView
    private lateinit var btnCheck: Button

    private var handler: Handler = Handler(Looper.getMainLooper())
    private var isChecking = false
    private var startTimestamp: Long = 0

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isChecking) return

            val sms = getLatestSmsSince(startTimestamp)

            if (sms != null) {
                isChecking = false
                tvProgress.text = "📩 התקבלה הודעה:\n\n$sms"
                return
            }

            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_balance)

        tvProgress = findViewById(R.id.tvProgress)
        btnCheck = findViewById(R.id.btnCheckBalance) // 🔥 תיקון כאן

        btnCheck.setOnClickListener {
            startBalanceCheck()
        }
    }

    private fun startBalanceCheck() {

        val hasCall = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED

        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

        if (!hasCall || !hasSms) {
            Toast.makeText(this, "חסרות הרשאות", Toast.LENGTH_SHORT).show()
            return
        }

        tvProgress.text = "⏳ מתחיל בדיקה..."

        startTimestamp = System.currentTimeMillis()
        isChecking = true

        handler.post(checkRunnable)

        handler.postDelayed({
            if (isChecking) {
                isChecking = false
                tvProgress.text = "❌ לא התקבלה תשובה"
            }
        }, 70000)

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${Uri.encode("*019")}")
        }
        startActivity(intent)
    }

    private fun getLatestSmsSince(timestamp: Long): String? {

        val uri = Uri.parse("content://sms/inbox")

        val cursor: Cursor? = contentResolver.query(
            uri,
            null,
            "date > ?",
            arrayOf(timestamp.toString()),
            "date DESC"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val body = it.getString(it.getColumnIndexOrThrow("body"))
                return body
            }
        }

        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        isChecking = false
        handler.removeCallbacksAndMessages(null)
    }
}
