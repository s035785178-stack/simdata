package com.stereotip.simdata

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            timer?.cancel()
            tvProgress.text = "✔ הבדיקה הושלמה בהצלחה"
            bindLatest()
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
        findViewById<Button>(R.id.btnRenewFromBalance).setOnClickListener { startActivity(Intent(this, PackagesActivity::class.java)) }
        findViewById<Button>(R.id.btnBackBalance).setOnClickListener { finish() }

        bindLatest()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(SmsReceiver.ACTION_BALANCE_UPDATED))
        bindLatest()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
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
        val hasCall = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (!hasCall) {
            Toast.makeText(this, "אין הרשאת שיחה", Toast.LENGTH_SHORT).show()
            return
        }
        tvProgress.text = "⏳ בודק יתרה... ההודעה עשויה להגיע תוך כדקה"
        timer?.cancel()
        timer = object : CountDownTimer(60_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                tvProgress.text = if (sec > 30) {
                    "⏳ בודק יתרה... נותרו $sec שניות"
                } else {
                    "⏳ עדיין ממתין ל-SMS מ-019... נותרו $sec שניות"
                }
            }
            override fun onFinish() {
                tvProgress.text = "לא התקבלה תשובה מ-019, נסו שוב"
            }
        }.start()

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${Uri.encode("*019")}")
        }
        startActivity(intent)
    }
}
