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
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.stereotip.simdata.data.BalanceResult
import com.stereotip.simdata.receiver.SmsReceiver
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.Formatter
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.SmsParser
import com.stereotip.simdata.util.TelephonyUtils

class BalanceActivity : AppCompatActivity() {

    companion object {
        private const val CHECK_PREFS = "balance_check_prefs"
        private const val KEY_CHECK_ACTIVE = "check_active"
        private const val KEY_CHECK_STARTED_AT = "check_started_at"
        private const val AUTO_RETURN_DELAY_MS = 6000L
        private const val POLL_INTERVAL_MS = 2000L
        private const val CHECK_TIMEOUT_MS = 70000L
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvLine: TextView
    private lateinit var tvData: TextView
    private lateinit var tvValid: TextView
    private lateinit var tvUpdated: TextView

    private var timer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private var autoStartHandled = false
    private var fromRegistration = false

    private val callPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startBalanceCheck()
            } else {
                Toast.makeText(this, "אין הרשאת שיחה", Toast.LENGTH_SHORT).show()
            }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            timer?.cancel()
            stopPolling()
            tvProgress.text = "✔ הבדיקה הושלמה בהצלחה"
            bindLatest()
            clearCheckState()

            if (fromRegistration) {
                handler.postDelayed({
                    val nextIntent = Intent(this@BalanceActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(nextIntent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }, 1200L)
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

        findViewById<Button>(R.id.btnCheckBalance).setOnClickListener {
            checkAndCall()
        }

        findViewById<Button>(R.id.btnRenewFromBalance).setOnClickListener {
            startActivity(Intent(this, PackagesActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        findViewById<Button>(R.id.btnBackBalance).setOnClickListener {
            finish()
        }

        fromRegistration = intent.getBooleanExtra("from_registration", false)
        autoStartHandled = savedInstanceState?.getBoolean("auto_start_handled", false) ?: false

        bindLatest()
    }

    override fun onResume() {
        super.onResume()

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receiver, IntentFilter(SmsReceiver.ACTION_BALANCE_UPDATED))

        bindLatest()

        if (isCheckActive()) {
            startPollingForNewSms()
        }

        val shouldAutoStart = intent.getBooleanExtra("auto_start_check", false)
        if (shouldAutoStart && !autoStartHandled && !isCheckActive()) {
            autoStartHandled = true
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    checkAndCall()
                }
            }, 600L)
        }
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onPause()
    }

    override fun onDestroy() {
        timer?.cancel()
        stopPolling()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("auto_start_handled", autoStartHandled)
        super.onSaveInstanceState(outState)
    }

    private fun safeDeviceLine(): String {
        return try {
            TelephonyUtils.getLineNumber(this)
        } catch (_: Exception) {
            ""
        }
    }

    private fun bindLatest() {
        val savedLine = AppPrefs.getLineNumber(this)
        val deviceLine = safeDeviceLine()

        val rawLine = when {
            !savedLine.isNullOrBlank() -> savedLine
            deviceLine.isNotBlank() -> deviceLine
            else -> "לא זוהה"
        }

        val line = PhoneUtils.normalizeToLocal(rawLine)

        val mb = AppPrefs.getBalanceMb(this)
        val valid = AppPrefs.getValid(this) ?: "---"
        val updated = AppPrefs.getUpdated(this)

        tvLine.text = "📱 מספר קו: $line"
        tvData.text = "📊 יתרת גלישה: ${mb?.let { Formatter.mbToDisplay(it) } ?: "---"}"
        tvValid.text = "📅 תוקף חבילה: $valid"
        tvUpdated.text = "🕒 עודכן: ${Formatter.formatDateTime(updated)}"
        tvStatus.text = Formatter.balanceStatus(mb)
    }

    private fun checkAndCall() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }

        startBalanceCheck()
    }

    private fun startBalanceCheck() {
        val hasCall = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

        if (!hasCall) {
            Toast.makeText(this, "אין הרשאת שיחה", Toast.LENGTH_SHORT).show()
            return
        }

        val startedAt = System.currentTimeMillis()
        saveCheckState(active = true, startedAt = startedAt)

        tvProgress.text = "⏳ בודק יתרה... אנא המתן עד 70 שניות"

        timer?.cancel()
        timer = object : CountDownTimer(CHECK_TIMEOUT_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvProgress.text = "⏳ בודק יתרה... נותרו ${millisUntilFinished / 1000} שניות"
            }

            override fun onFinish() {
                tvProgress.text = "לא התקבלה תשובה, נסו שוב"
                stopPolling()
                clearCheckState()

                if (fromRegistration) {
                    handler.postDelayed({
                        val nextIntent = Intent(this@BalanceActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(nextIntent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }, 1500L)
                }
            }
        }.start()

        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode("*019")}")
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "שגיאה בהוצאת שיחה", Toast.LENGTH_SHORT).show()
            clearCheckState()
            timer?.cancel()
            return
        }

        handler.postDelayed({
            try {
                val backIntent = Intent(this, BalanceActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("from_registration", fromRegistration)
                }
                startActivity(backIntent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } catch (_: Exception) {
            }
        }, AUTO_RETURN_DELAY_MS)
    }

    private fun startPollingForNewSms() {
        stopPolling()

        pollingRunnable = object : Runnable {
            override fun run() {
                val found = checkInboxForFreshBalanceMessage()

                if (found) {
                    timer?.cancel()
                    tvProgress.text = "✔ הבדיקה הושלמה בהצלחה"
                    bindLatest()
                    clearCheckState()
                    stopPolling()

                    if (fromRegistration) {
                        handler.postDelayed({
                            val nextIntent = Intent(this@BalanceActivity, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(nextIntent)
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()
                        }, 1200L)
                    }
                    return
                }

                if (isCheckActive()) {
                    handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }

        handler.post(pollingRunnable!!)
    }

    private fun stopPolling() {
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollingRunnable = null
    }

    private fun checkInboxForFreshBalanceMessage(): Boolean {
        val startedAt = getCheckStartedAt()
        if (startedAt <= 0L) return false

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val selection = "${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(startedAt.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        return try {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: continue
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))

                    if (date <= startedAt) continue
                    if (!looksLike019Message(address, body)) continue

                    val parsed = SmsParser.parse(body) ?: continue

                    val finalLineRaw = when {
                        !parsed.lineNumber.isNullOrBlank() -> parsed.lineNumber
                        else -> {
                            val deviceLine = safeDeviceLine()
                            if (deviceLine.startsWith("לא")) null else deviceLine
                        }
                    }

                    val finalLine = PhoneUtils.normalizeToLocal(finalLineRaw)

                    val merged = BalanceResult(
                        lineNumber = finalLine,
                        dataMb = parsed.dataMb,
                        validUntil = parsed.validUntil,
                        rawMessage = body,
                        updatedAt = System.currentTimeMillis()
                    )

                    AppPrefs.saveBalance(this, merged)
                    return true
                }
                false
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun looksLike019Message(address: String, body: String): Boolean {
        val normalizedAddress = address.trim()
        val text = body.lowercase()

        return normalizedAddress.contains("019") ||
            text.contains("019") ||
            text.contains("יתרה") ||
            text.contains("גלישה") ||
            text.contains("mb") ||
            text.contains("gb")
    }

    private fun isCheckActive(): Boolean {
        val prefs = getSharedPreferences(CHECK_PREFS, MODE_PRIVATE)
        return prefs.getBoolean(KEY_CHECK_ACTIVE, false)
    }

    private fun getCheckStartedAt(): Long {
        val prefs = getSharedPreferences(CHECK_PREFS, MODE_PRIVATE)
        return prefs.getLong(KEY_CHECK_STARTED_AT, 0L)
    }

    private fun saveCheckState(active: Boolean, startedAt: Long) {
        val prefs = getSharedPreferences(CHECK_PREFS, MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CHECK_ACTIVE, active)
            .putLong(KEY_CHECK_STARTED_AT, startedAt)
            .apply()
    }

    private fun clearCheckState() {
        val prefs = getSharedPreferences(CHECK_PREFS, MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CHECK_ACTIVE, false)
            .putLong(KEY_CHECK_STARTED_AT, 0L)
            .apply()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
