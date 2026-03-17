package com.stereotip.simdata

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvLine: TextView
    private lateinit var tvBalanceQuick: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvStatus: TextView

    private lateinit var logo: ImageView

    private lateinit var btnBalance: Button
    private lateinit var btnNetwork: Button
    private lateinit var btnPackages: Button
    private lateinit var btnSupport: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLine = findViewById(R.id.tvLine)
        tvBalanceQuick = findViewById(R.id.tvBalanceQuick)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvStatus = findViewById(R.id.tvStatus)

        logo = findViewById(R.id.logo)

        btnBalance = findViewById(R.id.btnBalance)
        btnNetwork = findViewById(R.id.btnNetwork)
        btnPackages = findViewById(R.id.btnPackages)
        btnSupport = findViewById(R.id.btnSupport)

        // כרגע אין קריאה למספר הסים כדי למנוע שגיאות build
        val number = ""
        tvLine.text = normalizePhone(number)

        btnBalance.setOnClickListener {
            tvStatus.text = "מבצע בדיקה..."
        }

        btnNetwork.setOnClickListener {
            tvStatus.text = "מאפס רשת..."
        }

        btnPackages.setOnClickListener {
            tvStatus.text = "פותח חבילות..."
        }

        btnSupport.setOnClickListener {
            tvStatus.text = "תמיכה..."
        }
    }

    private fun normalizePhone(number: String?): String {

        if (number.isNullOrEmpty())
            return "מספר קו: לא זוהה"

        var n = number.trim()

        if (n.startsWith("+972"))
            n = "0" + n.substring(4)

        else if (n.startsWith("972"))
            n = "0" + n.substring(3)

        return n
    }
}
