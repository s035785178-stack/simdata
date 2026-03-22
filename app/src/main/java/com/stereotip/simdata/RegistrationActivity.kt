package com.stereotip.simdata

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.NetworkUtils
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.net.URLEncoder

class RegistrationActivity : AppCompatActivity() {

    private lateinit var tvLineNumber: TextView
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner
    private lateinit var btnRegister: Button
    private lateinit var btnHelp: Button

    private val db = FirebaseFirestore.getInstance()
    private var detectedLineNumber: String = ""
    private val handler = Handler(Looper.getMainLooper())

    // 🔥 בדיקה אמיתית (עוקפת אנדרואיד)
    private fun hasRealSmsAccess(): Boolean {
        return try {
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null, null, null, null
            )
            cursor?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun hasRealPhoneAccess(): Boolean {
        return try {
            val line = TelephonyUtils.getLineNumber(this)
            line.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    private fun allPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return list.toTypedArray()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshLineNumber()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        tvLineNumber = findViewById(R.id.tvRegistrationLineNumber)
        etName = findViewById(R.id.etRegistrationName)
        etPhone = findViewById(R.id.etRegistrationPhone)
        etCarModel = findViewById(R.id.etCarModel)
        etCarNumber = findViewById(R.id.etCarNumber)
        spinnerPackage = findViewById(R.id.spinnerPackage)
        btnRegister = findViewById(R.id.btnRegisterCustomer)
        btnHelp = findViewById(R.id.btnHelp)

        setupSpinner()

        // 🔥 לא מבקשים הרשאות אם כבר יש בפועל
        if (!hasRealSmsAccess() || !hasRealPhoneAccess()) {
            requestPermissionsIfNeeded()
        }

        refreshLineNumber()
        scheduleRefresh()

        btnRegister.setOnClickListener {
            registerCustomer()
        }

        btnHelp.setOnClickListener {
            openHelp()
        }
    }

    private fun setupSpinner() {
        val packages = listOf(
            "לא ידוע / אין",
            "100 ג׳יגה או שנתיים",
            "36 ג׳יגה או 60 חודשים",
            "4 ג׳יגה או חודשיים"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            packages
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPackage.adapter = adapter
        spinnerPackage.setSelection(1)
    }

    private fun requestPermissionsIfNeeded() {

        // 🔥 אם כבר יש גישה אמיתית — לא מבקשים שוב
        if (hasRealSmsAccess() && hasRealPhoneAccess()) return

        val missing = allPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun scheduleRefresh() {
        handler.postDelayed({ refreshLineNumber() }, 300)
        handler.postDelayed({ refreshLineNumber() }, 800)
        handler.postDelayed({ refreshLineNumber() }, 1500)
    }

    private fun refreshLineNumber() {

        val saved = normalize(AppPrefs.getLineNumber(this))

        val device = normalize(
            try {
                TelephonyUtils.getLineNumber(this)
            } catch (e: Exception) {
                ""
            }
        )

        detectedLineNumber = when {
            saved.isNotBlank() -> saved
            device.isNotBlank() -> device
            else -> ""
        }

        tvLineNumber.text = if (detectedLineNumber.isNotBlank()) {
            "מספר קו במכשיר: $detectedLineNumber"
        } else {
            "מספר קו במכשיר: לא זוהה"
        }
    }

    private fun registerCustomer() {

        val name = etName.text.toString().trim()
        val phone = normalize(etPhone.text.toString())
        val carModel = etCarModel.text.toString().trim()
        val carNumber = etCarNumber.text.toString().trim()
        val dataPackage = spinnerPackage.selectedItem.toString()

        if (name.isBlank()) {
            etName.error = "נא למלא שם"
            return
        }

        if (phone.length != 10) {
            etPhone.error = "טלפון לא תקין"
            return
        }

        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "אין אינטרנט", Toast.LENGTH_SHORT).show()
            return
        }

        btnRegister.isEnabled = false
        btnRegister.text = "נרשם..."

        val now = System.currentTimeMillis()

        val data = hashMapOf(
            "customerName" to name,
            "customerPhone" to phone,
            "lineNumber" to detectedLineNumber,
            "carModel" to carModel,
            "carNumber" to carNumber,
            "dataPackage" to dataPackage,
            "createdAt" to now
        )

        db.collection("customers")
            .document(phone)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {

                AppPrefs.setCustomerName(this, name)
                AppPrefs.setCustomerPhone(this, phone)
                AppPrefs.setCarModel(this, carModel)
                AppPrefs.setCarNumber(this, carNumber)
                AppPrefs.setDataPackage(this, dataPackage)

                if (detectedLineNumber.isNotBlank()) {
                    AppPrefs.setLineNumber(this, detectedLineNumber)
                }

                btnRegister.text = "✔️ נרשמת"

                handler.postDelayed({
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }, 800)
            }
            .addOnFailureListener {
                btnRegister.isEnabled = true
                btnRegister.text = "הרשמה"
                Toast.makeText(this, "שגיאה", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openHelp() {
        val msg = "צריך עזרה בהרשמה"
        val url = "https://wa.me/972559911336?text=${URLEncoder.encode(msg, "UTF-8")}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun normalize(input: String?): String {
        val n = PhoneUtils.normalizeToLocal(input)
        return if (n.contains("לא")) "" else n
    }
}
