package com.stereotip.simdata

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.Formatter
import com.stereotip.simdata.util.NetworkUtils
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvLine: TextView
    private lateinit var tvBalanceQuick: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPackageStatus: Button
    private lateinit var btnWarrantyStatus: Button
    private lateinit var btnActivateWarranty: Button
    private lateinit var logo: ImageView

    private var logoTapCount = 0
    private var lastTapTime = 0L
    private var registrationCheckDone = false
    private var movedToRegistration = false
    private var balanceReceiverRegistered = false
    private var optionalPermissionsRequestedOnce = false

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.getDefault())

    private fun requiredPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
    }

    private fun optionalPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return list.toTypedArray()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            updateSummary()
            checkRegistrationIfNeeded()
            loadWarrantyStatus()
            loadPackageStatus()

            if (!hasRequiredPermissions()) {
                Toast.makeText(
                    this,
                    "יש לאשר הרשאות בסיס כדי שהאפליקציה תעבוד",
                    Toast.LENGTH_LONG
                ).show()
                showRequiredPermissionsDialogIfNeeded()
                return@registerForActivityResult
            }

            requestOptionalPermissionsIfNeeded()
        }

    private val balanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isFinishing || isDestroyed) return
            updateSummary()
            loadWarrantyStatus()
            loadPackageStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppPrefs.ensureInstallTimestamp(this)

        tvLine = findViewById(R.id.tvLine)
        tvBalanceQuick = findViewById(R.id.tvBalanceQuick)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvStatus = findViewById(R.id.tvStatus)
        btnPackageStatus = findViewById(R.id.btnPackageStatus)
        btnWarrantyStatus = findViewById(R.id.btnWarrantyStatus)
        btnActivateWarranty = findViewById(R.id.btnActivateWarranty)
        logo = findViewById(R.id.logo)

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
            onLogoTapped()
        }

        showRequiredPermissionsDialogIfNeeded()
        updateSummary()
        loadWarrantyStatus()
        loadPackageStatus()
    }

    override fun onResume() {
        super.onResume()

        if (!balanceReceiverRegistered) {
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(balanceReceiver, IntentFilter(SmsReceiver.ACTION_BALANCE_UPDATED))
            balanceReceiverRegistered = true
        }

        if (movedToRegistration) return

        updateSummary()
        checkRegistrationIfNeeded()
        loadWarrantyStatus()
        loadPackageStatus()
    }

    override fun onPause() {
        if (balanceReceiverRegistered) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(balanceReceiver)
            } catch (_: Exception) {
            }
            balanceReceiverRegistered = false
        }
        super.onPause()
    }

    private fun updateSummary() {
        val savedLine = AppPrefs.getLineNumber(this)

        val deviceLine = try {
            TelephonyUtils.getLineNumber(this)
        } catch (e: Exception) {
            ""
        }

        val rawLine = when {
            !savedLine.isNullOrBlank() -> savedLine
            deviceLine.isNotBlank() -> deviceLine
            else -> null
        }

        val line = PhoneUtils.normalizeToLocal(rawLine)
        tvLine.text = if (line == "לא זוהה") "לא זוהה מספר" else line

        val mb = AppPrefs.getBalanceMb(this)
        tvBalanceQuick.text = mb?.let { Formatter.mbToDisplay(it) } ?: "לא בוצעה בדיקה"
        tvUpdated.text = Formatter.formatDate(AppPrefs.getUpdated(this))
        tvStatus.text = Formatter.balanceStatus(mb)
    }

    private fun checkRegistrationIfNeeded() {
        if (registrationCheckDone || movedToRegistration) return

        val savedPhone = AppPrefs.getCustomerPhone(this)
        val savedName = AppPrefs.getCustomerName(this)
        val savedLine = normalizeLine(AppPrefs.getLineNumber(this))

        val deviceLine = try {
            normalizeLine(TelephonyUtils.getLineNumber(this))
        } catch (e: Exception) {
            ""
        }

        val normalizedLine = when {
            savedLine.isNotBlank() -> savedLine
            deviceLine.isNotBlank() -> deviceLine
            else -> ""
        }

        if (normalizedLine.isBlank() && savedPhone.isBlank() && savedName.isBlank()) {
            moveToRegistration(clearLocal = false)
            return
        }

        registrationCheckDone = true
    }

    private fun moveToRegistration(clearLocal: Boolean) {
        if (movedToRegistration) return

        movedToRegistration = true

        if (clearLocal) {
            clearLocalCustomer()
        }

        val intent = Intent(this, RegistrationActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun activateWarranty() {
        Toast.makeText(this, "האחריות הופעלה", Toast.LENGTH_SHORT).show()
    }

    private fun onLogoTapped() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 4000) logoTapCount = 0
        lastTapTime = now
        logoTapCount++
        if (logoTapCount >= 7) {
            logoTapCount = 0
            startActivity(Intent(this, TechnicianActivity::class.java))
        }
    }

    private fun showRequiredPermissionsDialogIfNeeded() {
        val missingRequired = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingRequired.isEmpty()) {
            requestOptionalPermissionsIfNeeded()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("נדרשות הרשאות להפעלת האפליקציה")
            .setMessage("יש לאשר הרשאות שיחה ו-SMS")
            .setPositiveButton("אשר") { _, _ ->
                permissionLauncher.launch(missingRequired.toTypedArray())
            }
            .setCancelable(false)
            .show()
    }

    private fun requestOptionalPermissionsIfNeeded() {
        if (optionalPermissionsRequestedOnce) return

        val missingOptional = optionalPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingOptional.isEmpty()) return

        optionalPermissionsRequestedOnce = true
        permissionLauncher.launch(missingOptional.toTypedArray())
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun clearLocalCustomer() {
        AppPrefs.setCustomerName(this, "")
        AppPrefs.setCustomerPhone(this, "")
        AppPrefs.setCarModel(this, "")
        AppPrefs.setCarNumber(this, "")
        AppPrefs.setDataPackage(this, "לא ידוע")
    }

    private fun normalizeLine(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return if (normalized == "לא זוהה") "" else normalized
    }
}
