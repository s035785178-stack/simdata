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
    private lateinit var btnWarrantyStatus: Button
    private lateinit var logo: ImageView

    private var logoTapCount = 0
    private var lastTapTime = 0L
    private var registrationCheckDone = false
    private var movedToRegistration = false
    private var balanceReceiverRegistered = false
    private var startupDialogShown = false

    private var warrantyActivated = false
    private var currentWarrantyEnd = ""

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.getDefault())

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            continuePermissionFlowIfNeeded()
            updateSummary()
            checkRegistrationIfNeeded()
            loadWarrantyStatus()
        }

    private val balanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isFinishing || isDestroyed) return
            updateSummary()
            loadWarrantyStatus()
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
        btnWarrantyStatus = findViewById(R.id.btnWarrantyStatus)
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

        btnWarrantyStatus.setOnClickListener {
            if (warrantyActivated) {
                Toast.makeText(
                    this,
                    "תוקף האחריות: $currentWarrantyEnd",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                activateWarranty()
            }
        }

        logo.setOnClickListener {
            onLogoTapped()
        }

        showStartupPermissionsDialogIfNeeded()
        triggerSmsPermission()
        updateSummary()
        checkRegistrationIfNeeded()
        loadWarrantyStatus()
    }

    override fun onResume() {
        super.onResume()

        continuePermissionFlowIfNeeded()

        if (!balanceReceiverRegistered) {
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(balanceReceiver, IntentFilter(SmsReceiver.ACTION_BALANCE_UPDATED))
            balanceReceiverRegistered = true
        }

        if (movedToRegistration) return

        updateSummary()
        checkRegistrationIfNeeded()
        loadWarrantyStatus()
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

    private fun showStartupPermissionsDialogIfNeeded() {
        if (!hasAnyMissingStartupPermission()) return
        if (startupDialogShown) return

        startupDialogShown = true

        AlertDialog.Builder(this)
            .setTitle("נדרשות הרשאות להפעלת האפליקציה")
            .setMessage("האפליקציה תבקש עכשיו את כל ההרשאות הדרושות: טלפון, הודעות SMS, זיהוי מספר קו, ובמכשירים מתאימים גם התראות. נא לאשר הכל.")
            .setPositiveButton("אשר הרשאות") { _, _ ->
                continuePermissionFlowIfNeeded()
            }
            .setCancelable(false)
            .show()
    }

    private fun continuePermissionFlowIfNeeded() {
        val missingPhone = getMissingPermissions(phonePermissions())
        if (missingPhone.isNotEmpty()) {
            permissionLauncher.launch(missingPhone.toTypedArray())
            return
        }

        val missingSms = getMissingPermissions(smsPermissions())
        if (missingSms.isNotEmpty()) {
            permissionLauncher.launch(missingSms.toTypedArray())
            return
        }

        val missingNotifications = getMissingPermissions(notificationPermissions())
        if (missingNotifications.isNotEmpty()) {
            permissionLauncher.launch(missingNotifications.toTypedArray())
        }
    }

    private fun getMissingPermissions(group: Array<String>): List<String> {
        return group.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasAnyMissingStartupPermission(): Boolean {
        return getMissingPermissions(phonePermissions()).isNotEmpty() ||
            getMissingPermissions(smsPermissions()).isNotEmpty() ||
            getMissingPermissions(notificationPermissions()).isNotEmpty()
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
        } catch (_: Exception) {
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
        val savedLine = AppPrefs.getLineNumber(this)
        val deviceLine = safeDeviceLine()

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
        val deviceLine = normalizeLine(safeDeviceLine())

        val normalizedLine = when {
            savedLine.isNotBlank() -> savedLine
            deviceLine.isNotBlank() -> deviceLine
            else -> ""
        }

        if (normalizedLine.isBlank() && savedPhone.isBlank() && savedName.isBlank()) {
            moveToRegistration(clearLocal = false)
            return
        }

        if (!NetworkUtils.isOnline(this)) {
            if (savedPhone.isNotBlank() || savedName.isNotBlank()) {
                registrationCheckDone = true
                return
            }
            moveToRegistration(clearLocal = false)
            return
        }

        registrationCheckDone = true

        if (normalizedLine.isNotBlank()) {
            db.collection("customers")
                .whereEqualTo("lineNumber", normalizedLine)
                .limit(1)
                .get()
                .addOnSuccessListener { result ->
                    if (isFinishing || isDestroyed || movedToRegistration) return@addOnSuccessListener

                    if (result.isEmpty) {
                        moveToRegistration(clearLocal = true)
                    } else {
                        val doc = result.documents.first()

                        val customerName = doc.getString("customerName").orEmpty()
                        val customerPhone = normalizePhone(doc.getString("customerPhone"))
                        val carModel = doc.getString("carModel").orEmpty()
                        val carNumber = doc.getString("carNumber").orEmpty()
                        val dataPackage = doc.getString("dataPackage").orEmpty()

                        if (customerName.isNotBlank()) AppPrefs.setCustomerName(this, customerName)
                        if (customerPhone.isNotBlank()) AppPrefs.setCustomerPhone(this, customerPhone)
                        if (carModel.isNotBlank()) AppPrefs.setCarModel(this, carModel)
                        if (carNumber.isNotBlank()) AppPrefs.setCarNumber(this, carNumber)
                        if (dataPackage.isNotBlank()) AppPrefs.setDataPackage(this, dataPackage)

                        AppPrefs.setLineNumber(this, normalizedLine)
                    }
                }
                .addOnFailureListener {
                    if (isFinishing || isDestroyed || movedToRegistration) return@addOnFailureListener

                    if (savedPhone.isBlank() && savedName.isBlank()) {
                        registrationCheckDone = false
                        moveToRegistration(clearLocal = false)
                    }
                }
            return
        }

        if (savedPhone.isNotBlank()) {
            db.collection("customers")
                .document(savedPhone)
                .get()
                .addOnSuccessListener { doc ->
                    if (isFinishing || isDestroyed || movedToRegistration) return@addOnSuccessListener
                    if (!doc.exists()) {
                        moveToRegistration(clearLocal = true)
                    }
                }
                .addOnFailureListener {
                    if (isFinishing || isDestroyed || movedToRegistration) return@addOnFailureListener
                    if (savedName.isBlank()) {
                        registrationCheckDone = false
                        moveToRegistration(clearLocal = false)
                    }
                }
            return
        }

        moveToRegistration(clearLocal = false)
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

    private fun loadWarrantyStatus() {
        val savedLine = normalizeLine(AppPrefs.getLineNumber(this))
        val deviceLine = normalizeLine(safeDeviceLine())

        val normalizedLine = when {
            savedLine.isNotBlank() -> savedLine
            deviceLine.isNotBlank() -> deviceLine
            else -> ""
        }

        if (normalizedLine.isBlank()) {
            warrantyActivated = false
            currentWarrantyEnd = ""
            btnWarrantyStatus.text = "הפעל תקופת אחריות✔️"
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", normalizedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                if (result.isEmpty) {
                    warrantyActivated = false
                    currentWarrantyEnd = ""
                    btnWarrantyStatus.text = "הפעל תקופת אחריות✔️"
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val warrantyEnd = doc.getString("warrantyEnd").orEmpty()
                val warrantyStart = doc.getLong("warrantyStart")

                if (warrantyEnd.isBlank() || warrantyStart == null || warrantyStart <= 0L) {
                    warrantyActivated = false
                    currentWarrantyEnd = ""
                    btnWarrantyStatus.text = "הפעל תקופת אחריות✔️"
                } else {
                    warrantyActivated = true
                    currentWarrantyEnd = warrantyEnd
                    btnWarrantyStatus.text = "תוקף אחריות $warrantyEnd🛡️"
                }
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    warrantyActivated = false
                    currentWarrantyEnd = ""
                    btnWarrantyStatus.text = "הפעל תקופת אחריות✔️"
                }
            }
    }

    private fun activateWarranty() {
        val savedLine = normalizeLine(AppPrefs.getLineNumber(this))
        val deviceLine = normalizeLine(safeDeviceLine())

        val normalizedLine = when {
            savedLine.isNotBlank() -> savedLine
            deviceLine.isNotBlank() -> deviceLine
            else -> ""
        }

        if (normalizedLine.isBlank()) {
            Toast.makeText(this, "לא זוהה מספר קו", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", normalizedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "לא נמצא לקוח במערכת", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val existingWarrantyStart = doc.getLong("warrantyStart")
                val existingWarrantyEnd = doc.getString("warrantyEnd").orEmpty()

                if (existingWarrantyStart != null && existingWarrantyStart > 0L && existingWarrantyEnd.isNotBlank()) {
                    Toast.makeText(
                        this,
                        "האחריות כבר הופעלה במכשיר זה. לשינויים יש לפנות לסטריאו טיפ אביזרי רכב",
                        Toast.LENGTH_LONG
                    ).show()
                    loadWarrantyStatus()
                    return@addOnSuccessListener
                }

                val startMillis = System.currentTimeMillis()
                val cal = Calendar.getInstance()
                cal.timeInMillis = startMillis
                cal.add(Calendar.YEAR, 1)
                val endDate = dateFormat.format(cal.time)

                val data: HashMap<String, Any?> = hashMapOf(
                    "warrantyStart" to startMillis,
                    "warrantyEnd" to endDate,
                    "lastUpdate" to System.currentTimeMillis()
                )

                doc.reference.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "האחריות הופעלה בהצלחה", Toast.LENGTH_SHORT).show()
                        loadWarrantyStatus()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "שגיאה בהפעלת אחריות", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "שגיאה באיתור לקוח", Toast.LENGTH_SHORT).show()
            }
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

    private fun clearLocalCustomer() {
        AppPrefs.setCustomerName(this, "")
        AppPrefs.setCustomerPhone(this, "")
        AppPrefs.setCarModel(this, "")
        AppPrefs.setCarNumber(this, "")
        AppPrefs.setDataPackage(this, "לא ידוע / אין")
    }

    private fun normalizeLine(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return when (normalized) {
            "לא זוהה", "לא זוהה מספר", "לא אושרו הרשאות" -> ""
            else -> normalized
        }
    }

    private fun normalizePhone(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return if (normalized == "לא זוהה") "" else normalized
    }
}
