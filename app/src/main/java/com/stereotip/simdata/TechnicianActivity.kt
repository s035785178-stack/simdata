package com.stereotip.simdata

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.stereotip.simdata.util.Formatter
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.TelephonyUtils

class TechnicianActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician)

        tvInfo = findViewById(R.id.tvTechInfo)

        loadData()
    }

    private fun loadData() {
        val rawLine = TelephonyUtils.getLineNumber(this)
        val lineNumber = normalizeLine(rawLine)

        if (lineNumber.isBlank()) {
            tvInfo.text = "לא זוהה מספר קו"
            return
        }

        db.collection("customers")
            .document(lineNumber)
            .get()
            .addOnSuccessListener { doc ->

                if (!doc.exists()) {
                    tvInfo.text = "לא נמצא לקוח במערכת"
                    return@addOnSuccessListener
                }

                val balanceMb = doc.getLong("currentBalanceMb")
                    ?: doc.getLong("balanceMb")

                val validUntil = doc.getString("validUntil") ?: "---"

                val lastCheck = doc.getLong("lastBalanceCheck")

                val balanceText = if (balanceMb != null) {
                    formatBalance(balanceMb)
                } else {
                    "לא בוצעה בדיקה"
                }

                val lastCheckText = formatTime(lastCheck)

                tvInfo.text = buildString {
                    appendLine("📱 מספר קו: $lineNumber")
                    appendLine()
                    appendLine("📊 יתרה: $balanceText")
                    appendLine("📅 תוקף: $validUntil")
                    appendLine("🕒 בדיקה אחרונה: $lastCheckText")
                }
            }
            .addOnFailureListener {
                tvInfo.text = "שגיאה בשליפת נתונים"
            }
    }

    private fun normalizeLine(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return when (normalized) {
            "לא זוהה", "לא זוהה מספר", "לא אושרו הרשאות" -> ""
            else -> normalized
        }
    }

    private fun formatBalance(mb: Long): String {
        return if (mb >= 1024) {
            val gb = mb / 1024.0
            String.format("%.1fGB", gb)
        } else {
            "$mb MB"
        }
    }

    private fun formatTime(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return "---"

        return android.text.format.DateFormat
            .format("dd/MM/yyyy HH:mm", timestamp)
            .toString()
    }
}
