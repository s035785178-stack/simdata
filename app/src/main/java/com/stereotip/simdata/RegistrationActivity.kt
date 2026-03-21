package com.stereotip.simdata

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
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

        val adapter = ArrayAdapter(this, R.layout.spinner_item_white, packages)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
        spinnerPackage.adapter = adapter
        spinnerPackage.setSelection(1)

        detectedLineNumber = normalizeLine(TelephonyUtils.getLineNumber(this))
        tvLineNumber.text = if (detectedLineNumber.isNotBlank()) {
            "מספר קו במכשיר: $detectedLineNumber"
        } else {
            "מספר קו במכשיר: לא זוהה"
        }

        btnRegister.setOnClickListener {
            attemptRegistration()
        }

        btnHelp.setOnClickListener {
            openHelpWhatsapp()
        }
    }

    private fun attemptRegistration() {
        val name = etName.text.toString().trim()
        val phone = normalizePhone(etPhone.text.toString())

        if (name.isBlank()) {
            etName.error = "יש להזין שם"
            etName.requestFocus()
            return
        }

        if (phone.length != 10 || !phone.startsWith("05")) {
            etPhone.error = "יש להזין טלפון תקין"
            etPhone.requestFocus()
            return
        }

        if (!NetworkUtils.isOnline(this)) {
            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("success", false)
            startActivity(intent)
            finish()
            return
        }

        registerCustomer()
    }

    private fun registerCustomer() {
        val name = etName.text.toString().trim()
        val phone = normalizePhone(etPhone.text.toString())
        val carModel = etCarModel.text.toString().trim()
        val carNumber = etCarNumber.text.toString().trim()
        val dataPackage = spinnerPackage.selectedItem?.toString().orEmpty()
        val lineNumber = detectedLineNumber
        val now = System.currentTimeMillis()

        val data = hashMapOf(
            "customerName" to name,
            "customerPhone" to phone,
            "lineNumber" to lineNumber,
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
                if (lineNumber.isNotBlank()) {
                    AppPrefs.setLineNumber(this, lineNumber)
                }

                val intent = Intent(this, ResultActivity::class.java)
                intent.putExtra("success", true)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                val intent = Intent(this, ResultActivity::class.java)
                intent.putExtra("success", false)
                startActivity(intent)
                finish()
            }
    }

    private fun openHelpWhatsapp() {
        val message = "היי אני צריך עזרה בהרשמה למערכת יתרת חבילת גלישה"
        val url = "https://wa.me/972559911336?text=${URLEncoder.encode(message, "UTF-8")}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun normalizePhone(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return if (normalized == "לא זוהה") "" else normalized
    }

    private fun normalizeLine(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return when (normalized) {
            "לא זוהה", "לא זוהה מספר", "לא אושרו הרשאות" -> ""
            else -> normalized
        }
    }
}
