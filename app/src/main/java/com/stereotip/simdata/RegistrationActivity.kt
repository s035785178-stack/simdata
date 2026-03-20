package com.stereotip.simdata

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.TelephonyUtils

class RegistrationActivity : AppCompatActivity() {

    private lateinit var tvLineNumber: TextView
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner
    private lateinit var btnRegister: Button

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

        // חבילות
        val packages = listOf(
            "100GB",
            "200GB",
            "300GB",
            "ללא הגבלה",
            "לא ידוע / אין"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, packages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPackage.adapter = adapter

        // ברירת מחדל 100GB
        spinnerPackage.setSelection(0)

        detectedLineNumber = normalizeLine(TelephonyUtils.getLineNumber(this))
        tvLineNumber.text = if (detectedLineNumber.isNotBlank()) {
            "מספר קו במכשיר: $detectedLineNumber"
        } else {
            "מספר קו במכשיר: לא זוהה"
        }

        btnRegister.setOnClickListener {
            registerCustomer()
        }
    }

    private fun registerCustomer() {
        val name = etName.text.toString().trim()
        val phone = normalizePhone(etPhone.text.toString())
        val carModel = etCarModel.text.toString().trim()
        val carNumber = etCarNumber.text.toString().trim()
        val dataPackage = spinnerPackage.selectedItem.toString()
        val lineNumber = detectedLineNumber

        if (name.isBlank()) {
            etName.error = "יש להזין שם"
            return
        }

        if (phone.length != 10 || !phone.startsWith("05")) {
            etPhone.error = "טלפון לא תקין"
            return
        }

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

                Toast.makeText(this, "ההרשמה בוצעה בהצלחה", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "שגיאה בהרשמה", Toast.LENGTH_SHORT).show()
            }
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
