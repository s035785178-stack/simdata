package com.stereotip.simdata

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
    private lateinit var tvWarrantyStatus: TextView
    private lateinit var btnActivateWarranty: Button
    private lateinit var logo: ImageView

    private var logoTapCount = 0
    private var lastTapTime = 0L
    private var registrationCheckDone = false

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.getDefault())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateSummary()
            checkRegistrationIfNeeded()
            loadWarrantyStatus()
        }

    private val balanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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
        tvWarrantyStatus = findViewById(R.id.tvWarrantyStatus)
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

        logo.setOnClickListener { onLogoTapped() }

        askForRequiredPermissionsIfNeeded()
        updateSummary()
        loadWarrantyStatus()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(balanceReceiver, IntentFilter(SmsReceiver.ACTION_BALANCE_UPDATED))
        updateSummary()
        checkRegistrationIfNeeded()
        loadWarrantyStatus()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(balanceReceiver)
        super.onPause()
    }

    private fun updateSummary() {
        val savedLine = AppPrefs.getLineNumber(this)
        val deviceLine = TelephonyUtils.getLineNumber(this)

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
        if (registrationCheckDone) return
        if (!hasPhonePermissions()) return

        val normalizedLine = normalizeLine(TelephonyUtils.getLineNumber(this))
        if (normalizedLine.isBlank()) return

        registrationCheckDone = true

        db.collection("customers")
            .whereEqualTo("lineNumber", normalizedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    startActivity(Intent(this, RegistrationActivity::class.java))
                } else {
                    val doc = result.documents.first()

                    val customerName = doc.getString("customerName").orEmpty()
                    val customerPhone = normalizePhone(doc.getString("customerPhone"))

                    if (customerName.isNotBlank()) {
                        AppPrefs.setCustomerName(this, customerName)
                    }
                    if (customerPhone.isNotBlank()) {
                        AppPrefs.setCustomerPhone(this, customerPhone)
                    }
                    AppPrefs.setLineNumber(this, normalizedLine)
                }
            }
            .addOnFailureListener {
                registrationCheckDone = false
            }
    }

    private fun loadWarrantyStatus() {
        val normalizedLine = normalizeLine(TelephonyUtils.getLineNumber(this))
        if (normalizedLine.isBlank()) {
            tvWarrantyStatus.text = "🛡️ אחריות: לא זוהה מספר קו"
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", normalizedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    tvWarrantyStatus.text = "🛡️ אחריות: לא הופעלה"
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val warrantyEnd = doc.getString("warrantyEnd").orEmpty()
                val warrantyStart = doc.getLong("warrantyStart")

                if (warrantyEnd.isBlank() || warrantyStart == null || warrantyStart <= 0L) {
                    tvWarrantyStatus.text = "🛡️ אחריות: לא הופעלה"
                } else {
                    tvWarrantyStatus.text = "🛡️ אחריות עד: $warrantyEnd"
                }
            }
            .addOnFailureListener {
                tvWarrantyStatus.text = "🛡️ אחריות: שגיאה בטעינה"
            }
    }

    private fun activateWarranty() {
        val normalizedLine = normalizeLine(TelephonyUtils.getLineNumber(this))
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

    private fun askForRequiredPermissionsIfNeeded() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.CALL_PHONE
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("נדרשות הרשאות להפעלת האפליקציה")
                .setMessage("האפליקציה זקוקה להרשאות כדי לבדוק יתרה, לזהות מספר קו ולקבל הודעות מהמערכת. נא לאשר הכל.")
                .setPositiveButton("הפעל את האפליקציה") { _, _ ->
                    permissionLauncher.launch(missing.toTypedArray())
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun hasPhonePermissions(): Boolean {
        val required = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        )

        return required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
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
