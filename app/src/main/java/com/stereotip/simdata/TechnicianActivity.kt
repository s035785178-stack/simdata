package com.stereotip.simdata

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.Formatter
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.QrUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.net.URLEncoder

class TechnicianActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private val db = FirebaseFirestore.getInstance()

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
            Toast.makeText(this, "ההיסטוריה המקומית נוקתה", Toast.LENGTH_SHORT).show()
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
        val normalizedLine = normalizeDisplayPhone(network.lineNumber)

        if (normalizedLine.isBlank()) {
            bindFallbackInfo()
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", normalizedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    bindFallbackInfo()
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                bindFromDocument(doc, network, normalizedLine)
            }
            .addOnFailureListener {
                bindFallbackInfo()
            }
    }

    private fun bindFromDocument(
        doc: DocumentSnapshot,
        network: com.stereotip.simdata.data.NetworkCheckResult,
        normalizedLine: String
    ) {
        val customerName = doc.getString("customerName").orEmpty().ifBlank { "---" }
        val customerPhone = normalizeDisplayPhone(doc.getString("customerPhone")).ifBlank { "---" }
        val carModel = doc.getString("carModel").orEmpty().ifBlank { "---" }
        val carNumber = doc.getString("carNumber").orEmpty().ifBlank { "---" }
        val dataPackage = doc.getString("dataPackage").orEmpty().ifBlank { "לא ידוע / אין" }
        val validUntil = doc.getString("validUntil").orEmpty().ifBlank { "---" }
        val warrantyEnd = doc.getString("warrantyEnd").orEmpty().ifBlank { "---" }

        val balanceText = when {
            doc.getLong("currentBalanceMb") != null -> {
                Formatter.mbToDisplay(doc.getLong("currentBalanceMb")!!.toInt())
            }
            doc.getLong("balanceMb") != null -> {
                Formatter.mbToDisplay(doc.getLong("balanceMb")!!.toInt())
            }
            else -> "לא בוצעה בדיקה"
        }

        val lastCheckText = formatLastCheck(
            doc.getLong("lastBalanceCheck") ?: doc.getLong("lastUpdate")
        )

        val baseText = buildString {
            appendLine("📱 מספר קו: $normalizedLine")
            appendLine("📡 דגם מכשיר: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("🧩 גרסת אפליקציה: 1.0")
            appendLine("🕒 זמן התקנה: ${Formatter.formatDateTime(AppPrefs.getInstallTimestamp(this@TechnicianActivity))}")
            appendLine()
            appendLine("👤 שם לקוח: $customerName")
            appendLine("☎ טלפון: $customerPhone")
            appendLine("🚘 דגם רכב: $carModel")
            appendLine("🔢 מספר רכב: $carNumber")
            appendLine("📦 חבילה: $dataPackage")
            appendLine()
            appendLine("📊 יתרה אחרונה: $balanceText")
            appendLine("📅 תוקף אחרון: $validUntil")
            appendLine("🕒 בדיקה אחרונה: $lastCheckText")
            appendLine("🛡️ אחריות עד: $warrantyEnd")
            appendLine()
            appendLine("📶 SIM: ${network.simStatus}")
            appendLine("📡 רשת: ${network.networkType}")
            appendLine("🌐 אינטרנט: ${network.internetStatus}")
        }

        tvInfo.text = "$baseText\n\n⏳ טוען היסטוריית בדיקות..."

        loadBalanceHistory(doc.reference, baseText)
    }

    private fun loadBalanceHistory(docRef: DocumentReference, baseText: String) {
        docRef.collection("balance_checks")
            .orderBy("checkedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    tvInfo.text = "$baseText\n\n📚 היסטוריית בדיקות:\nאין היסטוריה עדיין"
                    return@addOnSuccessListener
                }

                val historyText = buildString {
                    appendLine("📚 היסטוריית בדיקות:")
                    appendLine()

                    result.documents.forEachIndexed { index, item ->
                        val checkedAt = item.getLong("checkedAt")
                        val balanceMb = item.getLong("balanceMb")
                        val validUntil = item.getString("validUntil").orEmpty().ifBlank { "---" }

                        val balanceText = if (balanceMb != null) {
                            Formatter.mbToDisplay(balanceMb.toInt())
                        } else {
                            "לא זוהה"
                        }

                        val timeText = formatLastCheck(checkedAt)

                        appendLine("${index + 1}. $timeText")
                        appendLine("   📊 יתרה: $balanceText")
                        appendLine("   📅 תוקף: $validUntil")
                        appendLine()
                    }
                }

                tvInfo.text = "$baseText\n\n$historyText"
            }
            .addOnFailureListener {
                tvInfo.text = "$baseText\n\n📚 היסטוריית בדיקות:\nשגיאה בטעינת היסטוריה"
            }
    }

    private fun bindFallbackInfo() {
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
        return if (
            normalized == "לא זוהה" ||
            normalized == "לא זוהה מספר" ||
            normalized == "לא אושרו הרשאות"
        ) {
            ""
        } else {
            normalized
        }
    }

    private fun formatLastCheck(timestamp: Long?): String {
        return if (timestamp == null || timestamp <= 0L) {
            "---"
        } else {
            Formatter.formatDateTime(timestamp)
        }
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
