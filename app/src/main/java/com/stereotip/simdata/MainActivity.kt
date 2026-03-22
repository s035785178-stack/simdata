package com.stereotip.simdata

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stereotip.simdata.receiver.SmsReceiver
import com.stereotip.simdata.util.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvLine: TextView
    private lateinit var tvBalanceQuick: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPackageStatus: Button
    private lateinit var btnWarrantyStatus: Button
    private lateinit var btnActivateWarranty: Button
    private lateinit var logo: ImageView

    private var startupDialogShown = false
    private var balanceReceiverRegistered = false

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.getDefault())

    // ================= הרשאות =================

    private fun phonePermissions() = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS
    )

    private fun smsPermissions() = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    private fun notificationPermissions() =
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            continuePermissionFlow()
        }

    private fun continuePermissionFlow() {
        val missingPhone = getMissing(phonePermissions())
        if (missingPhone.isNotEmpty()) {
            permissionLauncher.launch(missingPhone.toTypedArray())
            return
        }

        val missingSms = getMissing(smsPermissions())
        if (missingSms.isNotEmpty()) {
            permissionLauncher.launch(missingSms.toTypedArray())
            return
        }

        val missingNotifications = getMissing(notificationPermissions())
        if (missingNotifications.isNotEmpty()) {
            permissionLauncher.launch(missingNotifications.toTypedArray())
        }
    }

    private fun getMissing(perms: Array<String>): List<String> {
        return perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasMissingPermissions(): Boolean {
        return getMissing(phonePermissions()).isNotEmpty() ||
                getMissing(smsPermissions()).isNotEmpty() ||
                getMissing(notificationPermissions()).isNotEmpty()
    }

    // 🔥 טריגר SMS
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

    // ================= לייף סייקל =================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLine = findViewById(R.id.tvLine)
        tvBalanceQuick = findViewById(R.id.tvBalanceQuick)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvStatus = findViewById(R.id.tvStatus)
        btnPackageStatus = findViewById(R.id.btnPackageStatus)
        btnWarrantyStatus = findViewById(R.id.btnWarrantyStatus)
        btnActivateWarranty = findViewById(R.id.btnActivateWarranty)
        logo = findViewById(R.id.logo)

        // כפתורים
        findViewById<Button>(R.id.btnBalance).setOnClickListener {
            startActivity(Intent(this, BalanceActivity::class.java))
        }

        findViewById<Button>(R.id.btnNetwork).setOnClickListener {
            startActivity(Intent(this, NetworkCheckActivity::class.java))
        }

        findViewById<Button>(R.id.btnPackages).setOnClickListener {
            startActivity(Intent(this, PackagesActivity::class.java))
        }

        findViewById<Button>(R.id.btnSupport).setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }

        btnActivateWarranty.setOnClickListener {
            activateWarranty()
        }

        logo.setOnClickListener {
            startActivity(Intent(this, TechnicianActivity::class.java))
        }

        showPermissionsDialog()

        // 🔥 חשוב
        triggerSmsPermission()

        updateSummary()
        checkRegistrationIfNeeded()
    }

    override fun onResume() {
        super.onResume()

        continuePermissionFlow()

        if (!balanceReceiverRegistered) {
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(balanceReceiver, IntentFilter(SmsReceiver.ACTION_BALANCE_UPDATED))
            balanceReceiverRegistered = true
        }

        updateSummary()
        checkRegistrationIfNeeded()
    }

    override fun onPause() {
        if (balanceReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(balanceReceiver)
            balanceReceiverRegistered = false
        }
        super.onPause()
    }

    private fun showPermissionsDialog() {
        if (!hasMissingPermissions() || startupDialogShown) return

        startupDialogShown = true

        AlertDialog.Builder(this)
            .setTitle("נדרשות הרשאות")
            .setMessage("יש לאשר את כל ההרשאות כדי שהאפליקציה תעבוד")
            .setPositiveButton("אישור") { _, _ ->
                continuePermissionFlow()
            }
            .setCancelable(false)
            .show()
    }

    // ================= לוגיקה =================

    private val balanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateSummary()
        }
    }

    private fun safeDeviceLine(): String {
        return try {
            TelephonyUtils.getLineNumber(this)
        } catch (_: Exception) {
            ""
        }
    }

    private fun updateSummary() {
        val line = PhoneUtils.normalizeToLocal(safeDeviceLine())
        tvLine.text = if (line == "לא זוהה") "לא זוהה מספר" else line

        val mb = AppPrefs.getBalanceMb(this)
        tvBalanceQuick.text = mb?.let { Formatter.mbToDisplay(it) } ?: "לא בוצעה בדיקה"
        tvUpdated.text = Formatter.formatDate(AppPrefs.getUpdated(this))
        tvStatus.text = Formatter.balanceStatus(mb)
    }

    // 🔥 הרשמה חזרה
    private fun checkRegistrationIfNeeded() {
        val savedName = AppPrefs.getCustomerName(this)
        val savedPhone = AppPrefs.getCustomerPhone(this)

        if (savedName.isNullOrBlank() || savedPhone.isNullOrBlank()) {
            startActivity(Intent(this, RegistrationActivity::class.java))
            finish()
        }
    }

    private fun activateWarranty() {
        val line = PhoneUtils.normalizeToLocal(safeDeviceLine())

        if (line.isBlank()) {
            Toast.makeText(this, "לא זוהה מספר קו", Toast.LENGTH_SHORT).show()
            return
        }

        val cal = Calendar.getInstance()
        val start = System.currentTimeMillis()
        cal.timeInMillis = start
        cal.add(Calendar.YEAR, 1)
        val end = dateFormat.format(cal.time)

        val data = hashMapOf(
            "warrantyStart" to start,
            "warrantyEnd" to end
        )

        db.collection("customers")
            .document(line)
            .set(data, SetOptions.merge())
    }
}
