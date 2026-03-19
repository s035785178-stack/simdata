package com.stereotip.simdata

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.Formatter
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.QrUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.net.URLEncoder

class TechnicianActivity : AppCompatActivity() {
    private lateinit var tvInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician)
        tvInfo = findViewById(R.id.tvTechInfo)

        findViewById<Button>(R.id.btnTechNetwork).setOnClickListener {
            startActivity(Intent(this, NetworkCheckActivity::class.java))
        }

        findViewById<Button>(R.id.btnTechSupportQr).setOnClickListener {
            showTechQr()
        }

        findViewById<Button>(R.id.btnEditCustomer).setOnClickListener {
            startActivity(Intent(this, CustomerDetailsActivity::class.java))
        }

        findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            AppPrefs.clearHistory(this)
            bindInfo()
            Toast.makeText(this, "ההיסטוריה נוקתה", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnClearAll).setOnClickListener {
            AppPrefs.clearAll(this)
            bindInfo()
            Toast.makeText(this, "נתוני הלקוח אופסו", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnBackTech).setOnClickListener {
            finish()
        }

        bindInfo()
    }

    override fun onResume() {
        super.onResume()
        bindInfo()
    }

    private fun bindInfo() {
        val network = TelephonyUtils.checkNetwork(this)
        val balance = AppPrefs.getBalanceMb(this)?.let { Formatter.mbToDisplay(it) } ?: "לא בוצעה בדיקה"

        val normalizedLine = normalizeDisplayPhone(network.lineNumber)
        val normalizedCustomerPhone = normalizeDisplayPhone(AppPrefs.getCustomerPhone(this))

        tvInfo.text = buildString {
            appendLine("📱 מספר קו: ${if (normalizedLine.isBlank()) "---" else normalizedLine}")
            appendLine("📡 דגם מכשיר: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("🧩 גרסת אפליקציה: 1.0")
            appendLine("🕒 זמן התקנה: ${Formatter.formatDateTime(AppPrefs.getInstallTimestamp(this@TechnicianActivity))}")
            appendLine()
            appendLine("👤 שם לקוח: ${AppPrefs.getCustomerName(this@TechnicianActivity).ifBlank { "---" }}")
            appendLine("☎ טלפון: ${if (normalizedCustomerPhone.isBlank()) "---" else normalizedCustomerPhone}")
            appendLine("🚘 דגם רכב: ${AppPrefs.getCarModel(this@TechnicianActivity).ifBlank { "---" }}")
            appendLine("🔢 מספר רכב: ${AppPrefs.getCarNumber(this@TechnicianActivity).ifBlank { "---" }}")
            appendLine("📦 חבילה: ${AppPrefs.getDataPackage(this@TechnicianActivity).ifBlank { "לא ידוע / אין" }}")
            appendLine()
            appendLine("📊 יתרה אחרונה: $balance")
            appendLine("📅 תוקף אחרון: ${AppPrefs.getValid(this@TechnicianActivity) ?: "---"}")
            appendLine("🕒 בדיקה אחרונה: ${Formatter.formatDateTime(AppPrefs.getUpdated(this@TechnicianActivity))}")
            appendLine()
            appendLine("📶 SIM: ${network.simStatus}")
            appendLine("📡 רשת: ${network.networkType}")
            appendLine("🌐 אינטרנט: ${network.internetStatus}")
        }
    }

    private fun normalizeDisplayPhone(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return if (normalized == "לא זוהה") "" else normalized
    }

    private fun showTechQr() {
        val text = tvInfo.text.toString()
        val wa = "https://wa.me/972559911336?text=${URLEncoder.encode("דוח אבחון StereoTip\n\n$text", "UTF-8")}"
        val bitmap = QrUtils.createQrBitmap(wa)
        QrDialogFragment
            .newInstance(bitmap, "סרקו לשליחת דוח אבחון")
            .show(supportFragmentManager, "tech_qr")
    }
}
