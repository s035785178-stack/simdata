package com.stereotip.simdata

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.TelephonyUtils

class MainActivity : AppCompatActivity() {

    private lateinit var tvLineNumber: TextView
    private lateinit var tvLastBalance: TextView
    private lateinit var tvLastCheck: TextView
    private lateinit var tvStatusText: TextView
    private lateinit var logo: ImageView

    private var logoTapCount = 0
    private var lastLogoTapTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLineNumber = findViewById(R.id.lineNumber)
        tvLastBalance = findViewById(R.id.lastBalance)
        tvLastCheck = findViewById(R.id.lastCheck)
        tvStatusText = findViewById(R.id.statusText)
        logo = findViewById(R.id.logo)

        val btnBalance = findViewById<Button>(R.id.btnBalance)
        val btnNetwork = findViewById<Button>(R.id.btnNetwork)
        val btnPackages = findViewById<Button>(R.id.btnPackages)
        val btnSupport = findViewById<Button>(R.id.btnSupport)

        refreshMainScreen()

        btnBalance.setOnClickListener {
            startActivity(Intent(this, BalanceActivity::class.java))
        }

        btnNetwork.setOnClickListener {
            startActivity(Intent(this, NetworkCheckActivity::class.java))
        }

        btnPackages.setOnClickListener {
            startActivity(Intent(this, PackagesActivity::class.java))
        }

        btnSupport.setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }

        logo.setOnClickListener {
            handleLogoTap()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMainScreen()
    }

    private fun refreshMainScreen() {
        val lineNumber = TelephonyUtils.getLineNumber(this)
        val savedBalance = AppPrefs.getLastBalance(this)
        val savedCheckTime = AppPrefs.getLastCheckTime(this)
        val savedStatus = AppPrefs.getLastStatus(this)

        tvLineNumber.text = lineNumber.ifBlank { "לא זוהה מספר" }
        tvLastBalance.text = if (savedBalance.isNullOrBlank()) "לא בוצעה בדיקה" else savedBalance
        tvLastCheck.text = if (savedCheckTime.isNullOrBlank()) "לא בוצעה בדיקה" else savedCheckTime
        tvStatusText.text = if (savedStatus.isNullOrBlank()) "מוכן לבדיקה" else savedStatus
    }

    private fun handleLogoTap() {
        val now = System.currentTimeMillis()

        if (now - lastLogoTapTime > 3000) {
            logoTapCount = 0
        }

        logoTapCount++
        lastLogoTapTime = now

        if (logoTapCount >= 7) {
            logoTapCount = 0
            startActivity(Intent(this, TechnicianActivity::class.java))
        }
    }
}
