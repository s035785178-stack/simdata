package com.stereotip.simdata

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.TelephonyUtils

class RegistrationActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnRegister: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        etName = findViewById(R.id.etRegistrationName)
        etPhone = findViewById(R.id.etRegistrationPhone)
        btnRegister = findViewById(R.id.btnRegisterCustomer)

        btnRegister.setOnClickListener {
            registerCustomer()
        }
    }

    private fun registerCustomer() {
        val name = etName.text.toString().trim()
        val phone = normalizePhone(etPhone.text.toString())
        val lineNumber = normalizeLine(TelephonyUtils.getLineNumber(this))

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

        val now = System.currentTimeMillis()

        val data = hashMapOf(
            "customerName" to name,
            "customerPhone" to phone,
            "lineNumber" to lineNumber,
            "createdAt" to now,
            "lastUpdate" to now
        )

        db.collection("customers")
            .document(phone)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                AppPrefs.setCustomerName(this, name)
                AppPrefs.setCustomerPhone(this, phone)
                if (lineNumber.isNotBlank()) {
                    AppPrefs.setLineNumber(this, lineNumber)
                }

                Toast.makeText(this, "ההרשמה בוצעה בהצלחה", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "שגיאה בהרשמה, נסה שוב", Toast.LENGTH_SHORT).show()
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
