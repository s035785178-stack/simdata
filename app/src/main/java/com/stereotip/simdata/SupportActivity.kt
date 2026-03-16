package com.stereotip.simdata

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.TelephonyUtils

class SupportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this)
        val line = TelephonyUtils.getLineNumber(this)
        val balance = AppPrefs.getBalanceMb(this)
        val valid = AppPrefs.getValid(this)

        tv.text = buildString {
            append("תמיכה טכנית\n\n")
            append("מספר קו: ")
            append(if (line.isBlank()) "לא זוהה" else line)
            append("\n")
            append("יתרה: ")
            append(if (balance.isBlank()) "לא התקבלה" else balance)
            append("\n")
            append("תוקף: ")
            append(if (valid.isBlank()) "לא התקבל" else valid)
        }
        tv.textSize = 20f
        setContentView(tv)
    }
}
