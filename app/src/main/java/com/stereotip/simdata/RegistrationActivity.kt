package com.stereotip.simdata

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.NetworkUtils
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.net.URLEncoder

class RegistrationActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner
    private lateinit var btnRegister: Button
    private lateinit var btnHelp: Button
    private lateinit var tvLineNumber: TextView

    private val db = FirebaseFirestore.getInstance()
    private var detectedLineNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        etName = findViewById(R.id.etRegistrationName)
        etPhone = findViewById(R.id.etRegistrationPhone)
        etCarModel = findViewById(R.id.etCarModel)
        etCarNumber = findViewById(R.id.etCarNumber)
        spinnerPackage = findViewById(R.id.spinnerPackage)
        btnRegister = findViewById(R.id.btnRegisterCustomer)
        btnHelp = findViewById(R.id.btnHelp)
        tvLineNumber = findViewById(R.id.tvRegistrationLineNumber)

        val packages = listOf(
            "לא ידוע / אין",
            "100 ג׳יגה או שנתיים",
            "36 ג׳יגה או 60 חודשים",
            "4 ג׳יגה או חודשיים"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, packages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPackage.adapter = adapter

        detectedLineNumber = normalizeLine(TelephonyUtils.getLineNumber(this))
        tvLineNumber.text = if (detectedLineNumber.isNotBlank()) {
            "מספר קו במכשיר: $detectedLineNumber"
        } else {
            "מספר קו במכשיר: לא זוהה"
        }

        btnRegister.setOnClickListener {
            register()
        }

        btnHelp.setOnClickListener {
            openHelp()
        }
    }

    private fun register() {
        val name = etName.text.toString().trim()
        val phone = normalizePhone(etPhone.text.toString())

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "נא למלא שם וטלפון", Toast.LENGTH_SHORT).show()
            return
        }

        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "אין אינטרנט", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf(
            "customerName" to name,
            "customerPhone" to phone,
            "lineNumber" to detectedLineNumber,
            "carModel" to etCarModel.text.toString(),
            "carNumber" to etCarNumber.text.toString(),
            "dataPackage" to spinnerPackage.selectedItem.toString(),
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("customers")
            .document(phone)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {

                AppPrefs.setCustomerName(this, name)
                AppPrefs.setCustomerPhone(this, phone)

                Toast.makeText(this, "נרשמת בהצלחה", Toast.LENGTH_SHORT).show()

                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "שגיאה בהרשמה", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openHelp() {
        val msg = "צריך עזרה בהרשמה"
        val url = "https://wa.me/972559911336?text=" + URLEncoder.encode(msg, "UTF-8")
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun normalizePhone(raw: String?): String {
        val n = PhoneUtils.normalizeToLocal(raw)
        return if (n == "לא זוהה") "" else n
    }

    private fun normalizeLine(raw: String?): String {
        val n = PhoneUtils.normalizeToLocal(raw)
        return if (n.contains("לא")) "" else n
    }
}
