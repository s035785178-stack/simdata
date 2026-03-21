package com.stereotip.simdata

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        detectedLineNumber = normalizeLine(TelephonyUtils.getLineNumber(this))
        tvLineNumber.text = if (detectedLineNumber.isNotBlank()) {
            "מספר קו במכשיר: $detectedLineNumber"
        } else {
            "מספר קו במכשיר: לא זוהה"
        }

        btnRegister.setOnClickListener {
            registerCustomer()
        }

        btnHelp.setOnClickListener {
            openHelpWhatsapp()
        }
    }

    private fun registerCustomer() {
        val name = etName.text.toString().trim()
        val phone = normalizePhone(etPhone.text.toString())
        val carModel = etCarModel.text.toString().trim()
        val carNumber = etCarNumber.text.toString().trim()
        val dataPackage = spinnerPackage.selectedItem?.toString().orEmpty()

        if (name.isBlank()) {
            etName.error = "נא למלא שם"
            etName.requestFocus()
            return
        }

        if (phone.length != 10 || !phone.startsWith("05")) {
            etPhone.error = "נא למלא טלפון תקין"
            etPhone.requestFocus()
            return
        }

        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "אין אינטרנט", Toast.LENGTH_SHORT).show()
            return
        }

        btnRegister.isEnabled = false
        btnHelp.isEnabled = false
        btnRegister.text = "נרשם..."

        val now = System.currentTimeMillis()
        val data = hashMapOf(
            "customerName" to name,
            "customerPhone" to phone,
            "lineNumber" to detectedLineNumber,
            "carModel" to carModel,
            "carNumber" to carNumber,
            "dataPackage" to dataPackage,
            "createdAt" to now,
            "lastUpdate" to now
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

                btnRegister.text = "✔️ נרשמת בהצלחה"
                btnRegister.setBackgroundColor(0xFF2E7D32.toInt())

                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this, WarrantyPromptActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }, 900)
            }
            .addOnFailureListener {
                btnRegister.isEnabled = true
                btnHelp.isEnabled = true
                btnRegister.text = "הרשמה"
                Toast.makeText(this, "שגיאה בהרשמה", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openHelpWhatsapp() {
        val message = "היי אני צריך עזרה בהרשמה למערכת יתרת חבילת גלישה"
        val url = "https://wa.me/972559911336?text=${URLEncoder.encode(message, "UTF-8")}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun normalizePhone(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return when (normalized) {
            "לא זוהה", "לא זוהה מספר", "לא אושרו הרשאות" -> ""
            else -> normalized
        }
    }

    private fun normalizeLine(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return when (normalized) {
            "לא זוהה", "לא זוהה מספר", "לא אושרו הרשאות" -> ""
            else -> normalized
        }
    }
}
