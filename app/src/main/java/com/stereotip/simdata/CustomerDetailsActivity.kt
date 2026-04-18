package com.stereotip.simdata

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.PhoneUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CustomerDetailsActivity : AppCompatActivity() {

    private lateinit var tvCustomerSummary: TextView
    private lateinit var tvMissingCustomer: TextView
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner
    private lateinit var btnValidityMode: Button
    private lateinit var btnSave: Button
    private lateinit var btnBack: Button

    private val db = FirebaseFirestore.getInstance()

    private val packages = listOf(
        "לא ידוע / אין",
        "100 ג׳יגה או שנתיים",
        "36 ג׳יגה או 60 חודשים",
        "4 ג׳יגה או חודשיים"
    )

    private var isAutoValidity = true
    private var currentValidUntil = ""
    private var currentCustomerDocId: String? = null
    private var currentLineNumber = ""
    private var suppressSpinnerCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_details)

        tvCustomerSummary = findViewById(R.id.tvCustomerSummary)
        tvMissingCustomer = findViewById(R.id.tvMissingCustomer)
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etCarModel = findViewById(R.id.etCarModel)
        etCarNumber = findViewById(R.id.etCarNumber)
        spinnerPackage = findViewById(R.id.spinnerPackage)
        btnValidityMode = findViewById(R.id.btnValidityMode)
        btnSave = findViewById(R.id.btnSaveCustomer)
        btnBack = findViewById(R.id.btnBackCustomer)

        setupPackageSpinner()
        loadExistingData()
        loadCustomerSummary()
        refreshCustomerFromFirebase()

        btnValidityMode.setOnClickListener {
            showValidityOptions()
        }

        btnSave.setOnClickListener {
            saveCustomerData()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCustomerFromFirebase()
    }

    private fun setupPackageSpinner() {
        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item_white,
            packages
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
        spinnerPackage.adapter = adapter

        spinnerPackage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressSpinnerCallback) return
                if (isAutoValidity) {
                    currentValidUntil = calculateValidityFromPackage(packages[position])
                    AppPrefs.setValid(this@CustomerDetailsActivity, currentValidUntil)
                    updateValidityButtonText()
                    loadCustomerSummary()
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun loadExistingData() {
        etName.setText(AppPrefs.getCustomerName(this))
        etPhone.setText(normalizeDisplayPhone(AppPrefs.getCustomerPhone(this)))
        etCarModel.setText(AppPrefs.getCarModel(this))
        etCarNumber.setText(AppPrefs.getCarNumber(this))

        val savedPackage = AppPrefs.getDataPackage(this)
        val index = packages.indexOf(savedPackage).takeIf { it >= 0 } ?: 0
        suppressSpinnerCallback = true
        spinnerPackage.setSelection(index)
        suppressSpinnerCallback = false

        isAutoValidity = AppPrefs.isValidityModeAuto(this)
        currentValidUntil = AppPrefs.getValid(this).orEmpty().ifBlank {
            if (isAutoValidity) calculateValidityFromPackage(savedPackage) else ""
        }
        updateValidityButtonText()
    }

    private fun refreshCustomerFromFirebase() {
        val localLine = normalizeDisplayPhone(AppPrefs.getLineNumber(this).orEmpty())
        val localPhone = normalizeDisplayPhone(AppPrefs.getCustomerPhone(this))

        val query = when {
            localLine.isNotBlank() -> db.collection("customers").whereEqualTo("lineNumber", localLine).limit(1)
            localPhone.isNotBlank() -> db.collection("customers").whereEqualTo("customerPhone", localPhone).limit(1)
            else -> null
        }

        if (query == null) {
            tvMissingCustomer.visibility = View.VISIBLE
            return
        }

        query.get()
            .addOnSuccessListener { result ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                if (result.isEmpty) {
                    currentCustomerDocId = null
                    tvMissingCustomer.visibility = View.VISIBLE
                    AppPrefs.clearCustomerProfile(this)
                    loadExistingData()
                    loadCustomerSummary()
                    Toast.makeText(this, "הלקוח לא קיים במערכת", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                currentCustomerDocId = doc.id
                applyCustomerDocument(doc)
                tvMissingCustomer.visibility = View.GONE
                loadCustomerSummary()
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "שגיאה בטעינת נתוני לקוח", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun applyCustomerDocument(doc: DocumentSnapshot) {
        val line = normalizeDisplayPhone(doc.getString("lineNumber"))
        val name = doc.getString("name").orEmpty().ifBlank { doc.getString("customerName").orEmpty() }
        val phone = normalizeDisplayPhone(
            doc.getString("phone").orEmpty().ifBlank { doc.getString("customerPhone").orEmpty() }
        )
        val carNumber = doc.getString("carNumber").orEmpty()
        val carModel = doc.getString("carModel").orEmpty()
        val pkg = doc.getString("package").orEmpty().ifBlank { doc.getString("dataPackage").orEmpty() }
            .ifBlank { "לא ידוע / אין" }
        val valid = doc.getString("validUntil").orEmpty()
        val mode = doc.getString("validMode").orEmpty().ifBlank { doc.getString("validityMode").orEmpty() }

        currentLineNumber = line

        if (line.isNotBlank()) AppPrefs.setLineNumber(this, line)
        AppPrefs.setCustomerName(this, name)
        AppPrefs.setCustomerPhone(this, phone)
        AppPrefs.setCarModel(this, carModel)
        AppPrefs.setCarNumber(this, carNumber)
        AppPrefs.setDataPackage(this, pkg)
        AppPrefs.setValid(this, valid)
        AppPrefs.setValidityModeAuto(this, mode != "manual")

        etName.setText(name)
        etPhone.setText(phone)
        etCarModel.setText(carModel)
        etCarNumber.setText(carNumber)

        val index = packages.indexOf(pkg).takeIf { it >= 0 } ?: 0
        suppressSpinnerCallback = true
        spinnerPackage.setSelection(index)
        suppressSpinnerCallback = false

        isAutoValidity = mode != "manual"
        currentValidUntil = valid.ifBlank {
            if (isAutoValidity) calculateValidityFromPackage(pkg) else ""
        }
        updateValidityButtonText()
    }

    private fun loadCustomerSummary() {
        val localLine = normalizeDisplayPhone(AppPrefs.getLineNumber(this).orEmpty())
        val localPackage = AppPrefs.getDataPackage(this).ifBlank { "---" }
        val localBalance = AppPrefs.getBalanceMb(this)?.let { "${it}MB" } ?: "---"
        val localValid = AppPrefs.getValid(this).orEmpty().ifBlank { "לא ידוע" }
        val localUpdated = formatTimestamp(AppPrefs.getUpdated(this))

        tvCustomerSummary.text = buildSummary(
            line = if (localLine.isBlank()) "---" else localLine,
            pkg = localPackage,
            balance = localBalance,
            lastCheck = localUpdated,
            valid = localValid,
            warranty = "---"
        )
    }

    private fun buildSummary(
        line: String,
        pkg: String,
        balance: String,
        lastCheck: String,
        valid: String,
        warranty: String
    ): String {
        return """
📱 קו: $line

📦 חבילה: $pkg
📊 יתרה: $balance
🕒 בדיקה: $lastCheck
📅 תוקף: $valid

🛡️ אחריות: $warranty
        """.trimIndent()
    }

    private fun showValidityOptions() {
        val options = arrayOf(
            "אוטומטי לפי חבילה",
            "ידני - בחירת תאריך",
            "נקה תוקף"
        )

        AlertDialog.Builder(this)
            .setTitle("ניהול תוקף חבילה")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        isAutoValidity = true
                        AppPrefs.setValidityModeAuto(this, true)
                        currentValidUntil = calculateValidityFromPackage(spinnerPackage.selectedItem?.toString().orEmpty())
                        AppPrefs.setValid(this, currentValidUntil)
                        updateValidityButtonText()
                        loadCustomerSummary()
                    }

                    1 -> {
                        isAutoValidity = false
                        AppPrefs.setValidityModeAuto(this, false)
                        showDatePicker()
                    }

                    2 -> {
                        isAutoValidity = false
                        AppPrefs.setValidityModeAuto(this, false)
                        currentValidUntil = ""
                        AppPrefs.setValid(this, "")
                        updateValidityButtonText()
                        loadCustomerSummary()
                    }
                }
            }
            .show()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                currentValidUntil = String.format(
                    Locale.getDefault(),
                    "%02d/%02d/%04d",
                    dayOfMonth,
                    month + 1,
                    year
                )
                AppPrefs.setValid(this, currentValidUntil)
                updateValidityButtonText()
                loadCustomerSummary()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateValidityButtonText() {
        val mode = if (isAutoValidity) "אוטומטי" else "ידני"
        val value = currentValidUntil.ifBlank { "לא ידוע" }
        btnValidityMode.text = "📅 תוקף חבילה ($mode) - $value"
    }

    private fun calculateValidityFromPackage(selectedPackage: String): String {
        if (selectedPackage.isBlank() || selectedPackage == "לא ידוע / אין") return ""

        val cal = Calendar.getInstance()
        when (selectedPackage) {
            "100 ג׳יגה או שנתיים" -> cal.add(Calendar.YEAR, 2)
            "36 ג׳יגה או 60 חודשים" -> cal.add(Calendar.MONTH, 60)
            "4 ג׳יגה או חודשיים" -> cal.add(Calendar.MONTH, 2)
            else -> return ""
        }
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
    }

    private fun saveCustomerData() {
        val name = etName.text.toString().trim()
        val phone = normalizeDisplayPhone(etPhone.text.toString().trim())
        val carModel = etCarModel.text.toString().trim()
        val carNumber = etCarNumber.text.toString().trim()
        val packageName = spinnerPackage.selectedItem?.toString().orEmpty()
        val validUntil = if (isAutoValidity) {
            calculateValidityFromPackage(packageName)
        } else {
            currentValidUntil
        }.ifBlank { currentValidUntil }

        AppPrefs.setCustomerName(this, name)
        AppPrefs.setCustomerPhone(this, phone)
        AppPrefs.setCarModel(this, carModel)
        AppPrefs.setCarNumber(this, carNumber)
        AppPrefs.setDataPackage(this, packageName)
        AppPrefs.setValid(this, validUntil)
        AppPrefs.setValidityModeAuto(this, isAutoValidity)

        val lineNumber = normalizeDisplayPhone(AppPrefs.getLineNumber(this).orEmpty())
        val identifier = when {
            lineNumber.isNotBlank() -> lineNumber
            phone.isNotBlank() -> phone
            else -> ""
        }

        if (identifier.isBlank()) {
            Toast.makeText(this, "אין מזהה לקוח לשמירה", Toast.LENGTH_SHORT).show()
            loadCustomerSummary()
            return
        }

        val validMode = if (isAutoValidity) "auto" else "manual"
        currentValidUntil = validUntil
        val payload = linkedMapOf<String, Any?>(
            "name" to name,
            "lineNumber" to lineNumber,
            "phone" to phone,
            "carNumber" to carNumber,
            "carModel" to carModel,
            "package" to packageName,
            "validUntil" to validUntil,
            "validMode" to validMode,
            "lastUpdate" to System.currentTimeMillis(),

            // תאימות לאחור
            "customerName" to name,
            "customerPhone" to phone,
            "dataPackage" to packageName,
            "validityMode" to validMode
        )

        val saveTask = when {
            currentCustomerDocId != null -> db.collection("customers").document(currentCustomerDocId!!).set(payload, SetOptions.merge())
            lineNumber.isNotBlank() -> db.collection("customers").document(lineNumber).set(payload, SetOptions.merge())
            else -> db.collection("customers").document(phone).set(payload, SetOptions.merge())
        }

        saveTask
            .addOnSuccessListener {
                Toast.makeText(this, "פרטי הלקוח נשמרו", Toast.LENGTH_SHORT).show()
                tvMissingCustomer.visibility = View.GONE
                loadCustomerSummary()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "שגיאה בשמירת פרטי הלקוח", Toast.LENGTH_SHORT).show()
            }
    }

    private fun normalizeDisplayPhone(phone: String?): String {
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

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return "---"
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}
