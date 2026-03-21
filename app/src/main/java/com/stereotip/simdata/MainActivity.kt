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

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.getDefault())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateSummary()
            checkRegistrationIfNeeded()
            loadWarrantyStatus()
            loadPackageStatus()
        }

    private val balanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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

        logo.setOnClickListener { onLogoTapped() }

        askForRequiredPermissionsIfNeeded()
        updateSummary()
        loadWarrantyStatus()
        loadPackageStatus()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(balanceReceiver, IntentFilter(SmsReceiver.ACTION_BALANCE_UPDATED))
        updateSummary()
        checkRegistrationIfNeeded()
        loadWarrantyStatus()
        loadPackageStatus()
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

        val savedPhone = AppPrefs.getCustomerPhone(this)
        val savedName = AppPrefs.getCustomerName(this)
        val savedLine = normalizeLine(AppPrefs.getLineNumber(this))
        val deviceLine = normalizeLine(TelephonyUtils.getLineNumber(this))

        val normalizedLine = when {
            deviceLine.isNotBlank() -> deviceLine
            savedLine.isNotBlank() -> savedLine
            else -> ""
        }

        // אם אין הרשאות ואין בכלל מידע שמור - להרשמה
        if (!hasPhonePermissions() && savedPhone.isBlank() && savedName.isBlank() && normalizedLine.isBlank()) {
            goToRegistration()
            return
        }

        // אם אין לנו שום דרך לזהות את הלקוח וגם אין מידע שמור - להרשמה
        if (normalizedLine.isBlank() && savedPhone.isBlank() && savedName.isBlank()) {
            goToRegistration()
            return
        }

        // אם אין אינטרנט אבל כבר יש לקוח שמור מקומית - להישאר במסך הראשי
        if (!NetworkUtils.isOnline(this)) {
            if (savedPhone.isNotBlank() || savedName.isNotBlank()) {
                registrationCheckDone = true
                return
            }

            goToRegistration()
            return
        }

        registrationCheckDone = true

        if (normalizedLine.isNotBlank()) {
            db.collection("customers")
                .whereEqualTo("lineNumber", normalizedLine)
                .limit(1)
                .get()
                .addOnSuccessListener { result ->
                    if (result.isEmpty) {
                        clearLocalCustomer()
                        goToRegistration()
                    } else {
                        val doc = result.documents.first()

                        val customerName = doc.getString("customerName").orEmpty()
                        val customerPhone = normalizePhone(doc.getString("customerPhone"))
                        val carModel = doc.getString("carModel").orEmpty()
                        val carNumber = doc.getString("carNumber").orEmpty()
                        val dataPackage = doc.getString("dataPackage").orEmpty()

                        if (customerName.isNotBlank()) {
                            AppPrefs.setCustomerName(this, customerName)
                        }
                        if (customerPhone.isNotBlank()) {
                            AppPrefs.setCustomerPhone(this, customerPhone)
                        }
                        if (carModel.isNotBlank()) {
                            AppPrefs.setCarModel(this, carModel)
                        }
                        if (carNumber.isNotBlank()) {
                            AppPrefs.setCarNumber(this, carNumber)
                        }
                        if (dataPackage.isNotBlank()) {
                            AppPrefs.setDataPackage(this, dataPackage)
                        }

                        AppPrefs.setLineNumber(this, normalizedLine)
                    }
                }
                .addOnFailureListener {
                    if (savedPhone.isBlank() && savedName.isBlank()) {
                        registrationCheckDone = false
                        goToRegistration()
                    }
                }

            return
        }

        // אם אין lineNumber אבל יש טלפון שמור - נבדוק לפי מס' לקוח
        if (savedPhone.isNotBlank()) {
            db.collection("customers")
                .document(savedPhone)
                .get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        clearLocalCustomer()
                        goToRegistration()
                    }
                }
                .addOnFailureListener {
                    if (savedName.isBlank()) {
                        registrationCheckDone = false
                        goToRegistration()
                    }
                }
            return
        }

        goToRegistration()
    }

    private fun loadPackageStatus() {
        val normalizedLine = normalizeLine(TelephonyUtils.getLineNumber(this))
        if (normalizedLine.isBlank()) {
            btnPackageStatus.text = "📦 מצב חבילה\nלא זוהה"
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", normalizedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    btnPackageStatus.text = "📦 מצב חבילה\nלא ידוע"
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val pkg = doc.getString("dataPackage").orEmpty()

                btnPackageStatus.text = if (pkg.isBlank()) {
                    "📦 מצב חבילה\nלא ידוע"
                } else {
                    "📦 מצב חבילה\n$pkg"
                }
            }
            .addOnFailureListener {
                btnPackageStatus.text = "📦 מצב חבילה\nשגיאה"
            }
    }

    private fun loadWarrantyStatus() {
        val normalizedLine = normalizeLine(TelephonyUtils.getLineNumber(this))
        if (normalizedLine.isBlank()) {
            btnWarrantyStatus.text = "🛡️ תוקף אחריות\nלא זוהה"
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", normalizedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    btnWarrantyStatus.text = "🛡️ תוקף אחריות\nלא הופעלה"
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val warrantyEnd = doc.getString("warrantyEnd").orEmpty()
                val warrantyStart = doc.getLong("warrantyStart")

                if (warrantyEnd.isBlank() || warrantyStart == null || warrantyStart <= 0L) {
                    btnWarrantyStatus.text = "🛡️ תוקף אחריות\nלא הופעלה"
                } else {
                    btnWarrantyStatus.text = "🛡️ תוקף אחריות\n$warrantyEnd"
                }
            }
            .addOnFailureListener {
                btnWarrantyStatus.text = "🛡️ תוקף אחריות\nשגיאה"
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

    private fun clearLocalCustomer() {
        AppPrefs.setCustomerName(this, "")
        AppPrefs.setCustomerPhone(this, "")
        AppPrefs.setCarModel(this, "")
        AppPrefs.setCarNumber(this, "")
        AppPrefs.setDataPackage(this, "לא ידוע / אין")
        AppPrefs.setLineNumber(this, "")
    }

    private fun goToRegistration() {
        startActivity(Intent(this, RegistrationActivity::class.java))
        finish()
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
