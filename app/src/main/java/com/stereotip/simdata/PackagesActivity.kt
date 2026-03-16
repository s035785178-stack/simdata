package com.stereotip.simdata

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class PackagesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_packages)

        val btnPackageTrial = findViewById<Button>(R.id.btnPackageTrial)
        val btnPackageSaver = findViewById<Button>(R.id.btnPackageSaver)
        val btnPackageBest = findViewById<Button>(R.id.btnPackageBest)
        val btnBack = findViewById<Button>(R.id.btnBack)

        btnPackageTrial.setOnClickListener {
            openQrScreen(
                packageName = "4GB לחודשיים",
                packagePrice = "40₪"
            )
        }

        btnPackageSaver.setOnClickListener {
            openQrScreen(
                packageName = "36GB ל-5 שנים",
                packagePrice = "400₪"
            )
        }

        btnPackageBest.setOnClickListener {
            openQrScreen(
                packageName = "100GB לשנתיים",
                packagePrice = "250₪"
            )
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun openQrScreen(packageName: String, packagePrice: String) {
        val intent = Intent(this, SupportActivity::class.java).apply {
            putExtra("mode", "renew")
            putExtra("package_name", packageName)
            putExtra("package_price", packagePrice)
        }
        startActivity(intent)
    }
}
