package com.stereotip.simdata

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.stereotip.simdata.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvLine: TextView
    private lateinit var btnBalance: Button
    private lateinit var btnPackages: Button
    private lateinit var btnSupport: Button
    private lateinit var btnNetwork: Button
    private lateinit var logo: ImageView

    private var dialogShown = false

    private fun allPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )

        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return list.toTypedArray()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLine = findViewById(R.id.tvLine)
        btnBalance = findViewById(R.id.btnBalance)
        btnPackages = findViewById(R.id.btnPackages)
        btnSupport = findViewById(R.id.btnSupport)
        btnNetwork = findViewById(R.id.btnNetwork)
        logo = findViewById(R.id.logo)

        requestPermissions()
        triggerSmsPermission()

        btnBalance.setOnClickListener {
            startActivity(Intent(this, BalanceActivity::class.java))
        }

        btnPackages.setOnClickListener {
            startActivity(Intent(this, PackagesActivity::class.java))
        }

        btnSupport.setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }

        btnNetwork.setOnClickListener {
            startActivity(Intent(this, NetworkCheckActivity::class.java))
        }

        logo.setOnClickListener {
            startActivity(Intent(this, TechnicianActivity::class.java))
        }

        updateLine()
        checkRegistration()
    }

    override fun onResume() {
        super.onResume()
        requestPermissions()
        triggerSmsPermission()
        updateLine()
        checkRegistration()
    }

    private fun requestPermissions() {
        val missing = allPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty() && !dialogShown) {
            dialogShown = true

            AlertDialog.Builder(this)
                .setTitle("נדרשות הרשאות")
                .setMessage("יש לאשר את כל ההרשאות")
                .setPositiveButton("אישור") { _, _ ->
                    permissionLauncher.launch(missing.toTypedArray())
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun triggerSmsPermission() {
        try {
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                null,
                null,
                null
            )
            cursor?.close()
        } catch (_: Exception) {}
    }

    private fun updateLine() {
        val line = PhoneUtils.normalizeToLocal(
            try { TelephonyUtils.getLineNumber(this) } catch (_: Exception) { "" }
        )

        tvLine.text = if (line.isBlank() || line == "לא זוהה") {
            "לא זוהה מספר"
        } else line
    }

    private fun checkRegistration() {
        val name = AppPrefs.getCustomerName(this)
        val phone = AppPrefs.getCustomerPhone(this)

        if (name.isNullOrBlank() || phone.isNullOrBlank()) {
            startActivity(Intent(this, RegistrationActivity::class.java))
            finish()
        }
    }
}
