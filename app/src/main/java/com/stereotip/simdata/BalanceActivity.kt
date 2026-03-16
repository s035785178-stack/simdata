package com.stereotip.simdata

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.TelephonyUtils

class BalanceActivity : AppCompatActivity() {

    private lateinit var tvBalanceStatus: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvLineBalance: TextView
    private lateinit var tvData: TextView
    private lateinit var tvValid: TextView
    private lateinit var tvUpdatedBalance: TextView

    private lateinit var btnCheckBalance: Button
    private lateinit var btnRenewFromBalance: Button
    private lateinit var btnBackBalance: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_balance)

        tvBalanceStatus = findViewById(R.id.tvBalanceStatus)
        tvProgress = findViewById(R.id.tvProgress)
        tvLineBalance = findViewById(R.id.tvLineBalance)
        tvData = findViewById(R.id.tvData)
        tvValid = findViewById(R.id.tvValid)
        tvUpdatedBalance = findViewById(R.id.tvUpdatedBalance)

        btnCheckBalance = findViewById(R.id.btnCheckBalance)
        btnRenewFromBalance = findViewById(R.id.btnRenewFromBalance)
        btnBackBalance = findViewById(R.id.btnBackBalance)

        refreshUi()

        btnCheckBalance.setOnClickListener {
            tvProgress.text = "מבצע בדיקה מול 019...\nההודעה עשויה להגיע תוך כדקה"
            AppPrefs.saveStatus(this, "ממתין ל-SMS מ-019")
            refreshUi()
        }

        btnRenewFromBalance.setOnClickListener {
            startActivity(Intent(this, PackagesActivity::class.java))
        }

        btnBackBalance.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val line = TelephonyUtils.getLineNumber(this)
        val balance = AppPrefs.getBalanceMb(this)
        val valid = AppPrefs.getValid(this)
        val updated = AppPrefs.getUpdated(this)

        tvLineBalance.text = "מספר קו: ${if (line.isBlank()) "לא זוהה מספר" else line}"
        tvData.text = "יתרת גלישה: ${if (balance.isBlank()) "טרם התקבלה" else balance}"
        tvValid.text = "תוקף: ${if (valid.isBlank()) "טרם התקבל" else valid}"
        tvUpdatedBalance.text = "עודכן: ${if (updated.isBlank()) "טרם בוצעה בדיקה" else updated}"

        tvBalanceStatus.text = if (balance.isBlank()) {
            "טרם התקבלה יתרה"
        } else {
            "היתרה עודכנה"
        }

        if (AppPrefs.getStatus(this).isBlank()) {
            tvProgress.text = "לחץ על בדוק יתרה והמתן ל-SMS"
        } else {
            tvProgress.text = AppPrefs.getStatus(this)
        }
    }
}
