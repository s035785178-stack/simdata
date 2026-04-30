package com.stereotip.simdata

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RegistrationActivity : AppCompatActivity() {

    private lateinit var tvLineNumber: TextView
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner
    private lateinit var etManualValidUntil: EditText
    private lateinit var tvValidityHint: TextView
    private lateinit var btnUseAutoValidity: Button
    private lateinit var btnRegister: Button
    private lateinit var btnHelp: Button

    private val db = FirebaseFirestore.getInstance()
    private var detectedLineNumber: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val packageOptions = listOf(
        "לא ידוע / אין",
        "100 ג׳יגה או שנתיים",
        "36 ג׳יגה או 60 חודשים",
        "4 ג׳יגה או חודשיים"
    )

    private var isManualValidity = false

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
        etManualValidUntil = findViewById(R.id.etManualValidUntil)
        tvValidityHint = findViewById(R.id.tvValidityHint)
        btnUseAutoValidity = findViewById(R.id.btnUseAutoValidity)
        btnRegister = findViewById(R.id.btnRegisterCustomer)
        btnHelp = findViewById(R.id.btnHelp)

        setupSpinner()
        setupValidityControls()

        if (!hasRealSmsAccess() || !hasRealPhoneAccess()) {
            requestPermissionsIfNeeded()
        }

        refreshLineNumber()
        scheduleRefresh()

        btnRegister.setOnClickListener { registerCustomer() }
        btnHelp.setOnClickListener { openHelp() }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            packageOptions
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPackage.adapter = adapter
        spinnerPackage.setSelection(1)

        spinnerPackage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateValidityUi()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupValidityControls() {
        etManualValidUntil.setOnClickListener {
            val seedValue = etManualValidUntil.text.toString().trim().ifBlank {
                calculatePackageValidUntil(spinnerPackage.selectedItem?.toString().orEmpty())
            }
            showDatePicker(seedValue) { selectedDate ->
                isManualValidity = true
                etManualValidUntil.setText(selectedDate)
                updateValidityUi()
            }
        }

        btnUseAutoValidity.setOnClickListener {
            isManualValidity = false
            etManualValidUntil.setText("")
            updateValidityUi()
        }

        updateValidityUi()
    }

    private fun showDatePicker(seedValue: String, onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        tryParseDate(seedValue)?.let { calendar.time = it }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance()
                chosen.set(year, month, dayOfMonth)
                onDateSelected(displayDateFormat.format(chosen.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateValidityUi() {
        val selectedPackage = spinnerPackage.selectedItem?.toString().orEmpty().trim()
        val autoDate = calculatePackageValidUntil(selectedPackage)
        val manualDate = etManualValidUntil.text.toString().trim()

        when {
            isManualValidity && manualDate.isNotBlank() -> {
                tvValidityHint.text = "נבחר תוקף ידני: $manualDate. החבילה נשארת $selectedPackage."
                btnUseAutoValidity.visibility = android.view.View.VISIBLE
            }
            autoDate.isBlank() -> {
                tvValidityHint.text = "לחבילה הזו אין תוקף אוטומטי. אפשר לבחור תאריך ידני אם צריך."
                btnUseAutoValidity.visibility = android.view.View.GONE
            }
            else -> {
                tvValidityHint.text = "התוקף יחושב אוטומטית לפי החבילה: $autoDate"
                btnUseAutoValidity.visibility = android.view.View.GONE
            }
        }
    }

    private fun calculatePackageValidUntil(selectedPackage: String): String {
        val cal = Calendar.getInstance()
        when (selectedPackage) {
            "100 ג׳יגה או שנתיים" -> cal.add(Calendar.YEAR, 2)
            "36 ג׳יגה או 60 חודשים" -> cal.add(Calendar.MONTH, 60)
            "4 ג׳יגה או חודשיים" -> cal.add(Calendar.MONTH, 2)
            else -> return ""
        }
        return displayDateFormat.format(cal.time)
    }

    private fun requestPermissionsIfNeeded() {
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
            try { TelephonyUtils.getLineNumber(this) } catch (e: Exception) { "" }
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
        val selectedPackage = spinnerPackage.selectedItem?.toString().orEmpty().trim()

        val manualValidUntil = etManualValidUntil.text.toString().trim()
        val autoValidUntil = calculatePackageValidUntil(selectedPackage)

        val validMode = if (isManualValidity && manualValidUntil.isNotBlank()) {
            "manual"
        } else {
            "auto"
        }

        val validUntil = if (validMode == "manual") {
            manualValidUntil
        } else {
            autoValidUntil
        }

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
        val safePackage = if (selectedPackage.isBlank()) "לא ידוע / אין" else selectedPackage
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        val data = hashMapOf<String, Any?>(
            "name" to name,
            "lineNumber" to detectedLineNumber,
            "phone" to phone,
            "carNumber" to carNumber,
            "carModel" to carModel,
            "package" to safePackage,
            "validUntil" to validUntil,
            "validMode" to validMode,
            "balanceMb" to null,
            "currentBalanceMb" to null,
            "lastBalanceCheck" to null,
            "status" to "",
            "lastUpdate" to now,
            "deviceName" to deviceName,
            "customerName" to name,
            "customerPhone" to phone,
            "dataPackage" to safePackage,
            "validityMode" to validMode,
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
                AppPrefs.setDataPackage(this, safePackage)
                AppPrefs.setValidityModeAuto(this, validMode == "auto")
                AppPrefs.setValid(this, validUntil)

                if (detectedLineNumber.isNotBlank()) {
                    AppPrefs.setLineNumber(this, detectedLineNumber)
                }

                btnRegister.text = "✔️ נרשמת"
                handler.postDelayed({
                    startActivity(Intent(this, WarrantyPromptActivity::class.java))
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

    private fun tryParseDate(value: String): java.util.Date? {
        if (value.isBlank()) return null
        return try { displayDateFormat.parse(value) } catch (_: Exception) { null }
    }
}