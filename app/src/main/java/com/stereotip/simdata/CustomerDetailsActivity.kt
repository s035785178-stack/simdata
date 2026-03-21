package com.stereotip.simdata

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.PhoneUtils

class CustomerDetailsActivity : AppCompatActivity() {

    private lateinit var tvCustomerSummary: TextView
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnBack: Button

    private val db = FirebaseFirestore.getInstance()

    private val packages = listOf(
        "לא ידוע / אין",
        "100GB לשנתיים",
        "36GB ל-60 חודשים",
        "4GB לחודשיים"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_details)

        tvCustomerSummary = findViewById(R.id.tvCustomerSummary)
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etCarModel = findViewById(R.id.etCarModel)
        etCarNumber = findViewById(R.id.etCarNumber)
        spinnerPackage = findViewById(R.id.spinnerPackage)
        btnSave = findViewById(R.id.btnSaveCustomer)
        btnBack = findViewById(R.id.btnBackCustomer)

        setupPackageSpinner()
        loadExistingData()
        loadCustomerSummary()

        btnSave.setOnClickListener {
            saveCustomerData()
            Toast.makeText(this, "פרטי הלקוח נשמרו", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupPackageSpinner() {
        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item_white,
            packages
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
        spinnerPackage.adapter = adapter
    }

    private fun loadExistingData() {
        etName.setText(AppPrefs.getCustomerName(this))
        etPhone.setText(normalizeDisplayPhone(AppPrefs.getCustomerPhone(this)))
        etCarModel.setText(AppPrefs.getCarModel(this))
        etCarNumber.setText(AppPrefs.getCarNumber(this))

        val savedPackage = AppPrefs.getDataPackage(this)
        val index = packages.indexOf(savedPackage).takeIf { it >= 0 } ?: 0
        spinnerPackage.setSelection(index)
    }

    private fun loadCustomerSummary() {
        val lineNumber = normalizeDisplayPhone(AppPrefs.getLineNumber(this) ?: "")

        if (lineNumber.isBlank()) {
            tvCustomerSummary.text = "📱 קו: ---\n\n📦 חבילה: ---\n📊 יתרה: ---\n🕒 בדיקה: ---\n\n🛡️ אחריות: ---"
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", lineNumber)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    tvCustomerSummary.text = "📱 קו: $lineNumber\n\n📦 חבילה: ---\n📊 יתרה: ---\n🕒 בדיקה: ---\n\n🛡️ אחריות: ---"
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val pkg = doc.getString("dataPackage").orEmpty().ifBlank { "---" }
                val warranty = doc.getString("warrantyEnd").orEmpty().ifBlank { "לא הופעלה" }
                val balance = doc.getLong("currentBalanceMb") ?: doc.getLong("balanceMb")
                val lastCheck = doc.getLong("lastBalanceCheck") ?: doc.getLong("lastUpdate")

                val balanceText = if (balance != null) "${balance}MB" else "---"
                val lastCheckText = if (lastCheck != null && lastCheck > 0L) {
                    android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", lastCheck).toString()
                } else {
                    "---"
                }

                tvCustomerSummary.text = """
📱 קו: $lineNumber

📦 חבילה: $pkg
📊 יתרה: $balanceText
🕒 בדיקה: $lastCheckText

🛡️ אחריות: $warranty
                """.trimIndent()
            }
            .addOnFailureListener {
                tvCustomerSummary.text = "שגיאה בטעינת נתוני לקוח"
            }
    }

    private fun saveCustomerData() {
        AppPrefs.setCustomerName(this, etName.text.toString().trim())
        AppPrefs.setCustomerPhone(this, etPhone.text.toString().trim())
        AppPrefs.setCarModel(this, etCarModel.text.toString().trim())
        AppPrefs.setCarNumber(this, etCarNumber.text.toString().trim())
        AppPrefs.setDataPackage(this, spinnerPackage.selectedItem.toString())
    }

    private fun normalizeDisplayPhone(phone: String): String {
        val normalized = PhoneUtils.normalizeToLocal(phone)
        return if (
            normalized == "לא זוהה" ||
            normalized == "לא זוהה מספר" ||
            normalized == "לא אושרו הרשאות"
        ) {
            ""
        } else {
            normalized
        }
    }
}
