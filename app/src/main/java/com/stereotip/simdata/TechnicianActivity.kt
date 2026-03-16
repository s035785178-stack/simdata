package com.stereotip.simdata

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.TelephonyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TechnicianActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val line = TelephonyUtils.getLineNumber(this)
        val balance = AppPrefs.getBalanceMb(this)
        val valid = AppPrefs.getValid(this)
        val updated = AppPrefs.getUpdated(this)
        val history = AppPrefs.getHistory(this)
        val installTimestamp = AppPrefs.getInstallTimestamp(this)

        val installText = if (installTimestamp == 0L) {
            "לא נשמר"
        } else {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(installTimestamp))
        }

        val text = TextView(this).apply {
            textSize = 18f
            text = buildString {
                append("מצב טכנאי\n\n")
                append("מספר קו: ${if (line.isBlank()) "לא זוהה" else line}\n")
                append("יתרה אחרונה: ${if (balance.isBlank()) "לא התקבלה" else balance}\n")
                append("תוקף: ${if (valid.isBlank()) "לא התקבל" else valid}\n")
                append("עודכן: ${if (updated.isBlank()) "לא עודכן" else updated}\n")
                append("תאריך התקנה: $installText\n\n")
                append("היסטוריה:\n")
                append(if (history.isBlank()) "אין היסטוריה" else history)
            }
        }

        container.addView(text)
        scroll.addView(container)
        setContentView(scroll)
    }
}
