package com.stereotip.simdata

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.stereotip.simdata.receiver.SmsReceiver
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.Formatter

class BalanceActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvLine: TextView
    private lateinit var tvData: TextView
    private lateinit var tvValid: TextView
    private lateinit var tvUpdated: TextView

    private var timer: CountDownTimer? = null
    private var startTimestamp: Long = 0
    private var isCallActive = false

    // 🔥 קבלת SMS
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            timer?.cancel()
            tvProgress.text = "✔ הנתונים עודכנו"
            bindLatest()
        }
    }

    // 🔥 זיהוי מצב שיחה
    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra(TelephonyManager.EXTRA_STATE)

            when (state) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    isCallActive = true
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    if (isCallActive) {
                        isCallActive = false

                        // 🔥 חזרה לאפליקציה
                        val i = Intent(this@BalanceActivity, BalanceActivity::class.java)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(i)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_balance)

        tvStatus = findViewById(R.id.tvBalanceStatus)
        tvProgress = findViewById(R.id.tvProgress)
        tvLine = findViewById(R.id.tvLineBalance)
        tvData = findViewById(R.id.tvData)
        tvValid = findViewById(R.id.tvValid)
        tvUpdated = findViewById(R.id.tvUpdatedBalance)

        findViewById<Button>(R.id.btnCheckBalance).setOnClickListener { startBalanceCheck() }
        findViewById<Button>(R.id.btnRenewFromBalance).setOnClickListener {
            startActivity(Intent(this, PackagesActivity::class.java))
        }
        findViewById<Button>(R.id.btnBackBalance).setOnClickListener { finish() }

        bindLatest()
    }

    override fun onResume() {
        super.onResume()

        // SMS listener
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receiver, IntentFilter(SmsReceiver.ACTION_BALANCE_UPDATED))

        // Call listener
        registerReceiver(callReceiver, IntentFilter("android.intent.action.PHONE_STATE"))

        // fallback
        checkSmsFallback()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        unregisterReceiver(callReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    private fun bindLatest() {
        val line = AppPrefs.getLineNumber(this) ?: "לא זוהה"
        val mb = AppPrefs.getBalanceMb(this)
        val valid = AppPrefs.getValid(this) ?: "---"
        val updated = AppPrefs.getUpdated(this)

        tvLine.text = "📱 מספר קו: $line"
        tvData.text = "📊 יתרת גלישה: ${mb?.let { Formatter.mbToDisplay(it) } ?: "---"}"
        tvValid.text = "📅 תוקף חבילה: $valid"
        tvUpdated.text = "🕒 עודכן: ${Formatter.formatDateTime(updated)}"
        tvStatus.text = Formatter.balanceStatus(mb)
    }

    private fun startBalanceCheck() {
        val hasCall = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED

        if (!hasCall) {
            Toast.makeText(this, "אין הרשאת שיחה", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔥 מתחיל מעקב לפני חיוג
        startTimestamp = System.currentTimeMillis()

        tvProgress.text = "⏳ בודק יתרה... אנא המתן עד 70 שניות"

        timer?.cancel()
        timer = object : CountDownTimer(70_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvProgress.text = "⏳ נותרו ${millisUntilFinished / 1000} שניות"
            }

            override fun onFinish() {
                tvProgress.text = "לא התקבלה תשובה, נסו שוב"
            }
        }.start()

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${Uri.encode("*019")}")
        }
        startActivity(intent)
    }

    private fun checkSmsFallback() {
        if (startTimestamp == 0L) return

        val uri = Uri.parse("content://sms/inbox")

        val cursor: Cursor? = contentResolver.query(
            uri,
            null,
            "date > ?",
            arrayOf(startTimestamp.toString()),
            "date DESC"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                timer?.cancel()
                tvProgress.text = "✔ הנתונים עודכנו"
                bindLatest()
            }
        }
    }
}
