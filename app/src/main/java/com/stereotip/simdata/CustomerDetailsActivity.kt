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
        "100GB לשנתיים",
        "36GB ל-60 חודשים",
        "4GB לחודשיים"
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
                    persistValidityStateIfPossible()
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

        when {
            localLine.isNotBlank() -> {
                db.collection("customers").whereEqualTo("lineNumber", localLine).limit(1).get()
                    .addOnSuccessListener { lineResult ->
                        if (!lineResult.isEmpty) {
                            val doc = lineResult.documents.first()
                            currentCustomerDocId = doc.id
                            applyCustomerDocument(doc)
                            tvMissingCustomer.visibility = View.GONE
                            loadCustomerSummary()
                        } else if (localPhone.isNotBlank()) {
                            refreshCustomerByPhone(localPhone)
                        } else {
                            onCustomerMissing()
                        }
                    }
                    .addOnFailureListener {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, "שגיאה בטעינת נתוני לקוח", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            localPhone.isNotBlank() -> refreshCustomerByPhone(localPhone)
            else -> onCustomerMissing()
        }
    }

    private fun refreshCustomerByPhone(localPhone: String) {
        db.collection("customers").whereEqualTo("phone", localPhone).limit(1).get()
            .addOnSuccessListener { phoneResult ->
                if (!phoneResult.isEmpty) {
                    val doc = phoneResult.documents.first()
                    currentCustomerDocId = doc.id
                    applyCustomerDocument(doc)
                    tvMissingCustomer.visibility = View.GONE
                    loadCustomerSummary()
                } else {
                    db.collection("customers").whereEqualTo("customerPhone", localPhone).limit(1).get()
                        .addOnSuccessListener { legacyResult ->
                            if (!legacyResult.isEmpty) {
                                val doc = legacyResult.documents.first()
                                currentCustomerDocId = doc.id
                                applyCustomerDocument(doc)
                                tvMissingCustomer.visibility = View.GONE
                                loadCustomerSummary()
                            } else {
                                onCustomerMissing()
                            }
                        }
                        .addOnFailureListener {
                            if (!isFinishing && !isDestroyed) {
                                Toast.makeText(this, "שגיאה בטעינת נתוני לקוח", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "שגיאה בטעינת נתוני לקוח", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun onCustomerMissing() {
        currentCustomerDocId = null
        tvMissingCustomer.visibility = View.VISIBLE
        AppPrefs.clearCustomerProfile(this)
        loadExistingData()
        loadCustomerSummary()
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, "הלקוח לא קיים במערכת", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyCustomerDocument(doc: DocumentSnapshot) {
        val line = normalizeDisplayPhone(doc.getString("lineNumber"))
        val name = doc.getString("name").orEmpty().ifBlank { doc.getString("customerName").orEmpty() }
        val phone = normalizeDisplayPhone(doc.getString("phone").orEmpty().ifBlank { doc.getString("customerPhone").orEmpty() })
        val carModel = doc.getString("carModel").orEmpty().ifBlank { doc.getString("vehicleModel").orEmpty() }
        val carNumber = doc.getString("carNumber").orEmpty().ifBlank { doc.getString("vehicleNumber").orEmpty() }
        val pkg = doc.getString("package").orEmpty().ifBlank { doc.getString("dataPackage").orEmpty().ifBlank { "לא ידוע / אין" } }
        val valid = doc.getString("validUntil").orEmpty()
        val mode = doc.getString("validMode").orEmpty().ifBlank { doc.getString("validityMode").orEmpty() }

        currentLineNumber = line

        if (line.isNotBlank()) AppPrefs.setLineNumber(this, line)
        AppPrefs.setCustomerName(this, name)
        AppPrefs.setCustomerPhone(this, phone)
        AppPrefs.setCarModel(this, carModel)
        AppPrefs.setCarNumber(this, carNumber)
        AppPrefs.setDataPackage(this, pkg)

        etName.setText(name)
        etPhone.setText(phone)
        etCarModel.setText(carModel)
        etCarNumber.setText(carNumber)

        val index = packages.indexOf(pkg).takeIf { it >= 0 } ?: 0
        suppressSpinnerCallback = true
        spinnerPackage.setSelection(index)
        suppressSpinnerCallback = false

        isAutoValidity = mode != "manual"
        AppPrefs.setValidityModeAuto(this, isAutoValidity)

        val warrantyEnd = doc.getString("warrantyEnd").orEmpty()
        AppPrefs.setWarrantyEnd(this, warrantyEnd)
        AppPrefs.setWarrantyActive(this, warrantyEnd.isNotBlank())

        currentValidUntil = if (valid.isNotBlank()) {
            valid
        } else if (isAutoValidity) {
            calculateValidityFromPackage(pkg)
        } else {
            ""
        }
        AppPrefs.setValid(this, currentValidUntil)
        updateValidityButtonText()
    }

    private fun loadCustomerSummary() {
        val localLine = normalizeDisplayPhone(AppPrefs.getLineNumber(this).orEmpty())
        val localPackage = AppPrefs.getDataPackage(this).ifBlank { "---" }
        val localBalance = AppPrefs.getBalanceMb(this)?.let { "${it}MB" } ?: "---"
        val localValid = AppPrefs.getValid(this).orEmpty().ifBlank { "לא ידוע" }
        val localUpdated = formatTimestamp(AppPrefs.getUpdated(this))

        val localWarranty = AppPrefs.getWarrantyEnd(this).ifBlank { "---" }
        tvCustomerSummary.text = buildSummary(
            line = if (localLine.isBlank()) "---" else localLine,
            pkg = localPackage,
            balance = localBalance,
            lastCheck = localUpdated,
            valid = localValid,
            warranty = localWarranty
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
                        persistValidityStateIfPossible()
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
                        persistValidityStateIfPossible()
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
                persistValidityStateIfPossible()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }


    private fun persistValidityStateIfPossible() {
        val docId = currentCustomerDocId ?: return
        val payload = linkedMapOf(
            "validUntil" to currentValidUntil,
            "validMode" to if (isAutoValidity) "auto" else "manual",
            "validityMode" to if (isAutoValidity) "auto" else "manual",
            "package" to spinnerPackage.selectedItem?.toString().orEmpty(),
            "dataPackage" to spinnerPackage.selectedItem?.toString().orEmpty(),
            "lastUpdate" to System.currentTimeMillis()
        )
        db.collection("customers").document(docId).set(payload, SetOptions.merge())
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
            "100GB לשנתיים" -> cal.add(Calendar.YEAR, 2)
            "36GB ל-60 חודשים" -> cal.add(Calendar.MONTH, 60)
            "4GB לחודשיים" -> cal.add(Calendar.MONTH, 2)
            else -> return ""
        }
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
    }

    private fun saveCustomerData() {
        val name = etName.text.toString().trim()
        val phone = normalizeDisplayPhone(etPhone.text.toString().trim())
        val carModel = etCarModel.text.toString().trim()
        val carNumber = etCarNumber.text.toString().trim()
        val dataPackage = spinnerPackage.selectedItem?.toString().orEmpty()

        AppPrefs.setCustomerName(this, name)
        AppPrefs.setCustomerPhone(this, phone)
        AppPrefs.setCarModel(this, carModel)
        AppPrefs.setCarNumber(this, carNumber)
        AppPrefs.setDataPackage(this, dataPackage)
        AppPrefs.setValid(this, currentValidUntil)
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

        val payload = linkedMapOf(
            "name" to name,
            "lineNumber" to lineNumber,
            "phone" to phone,
            "carNumber" to carNumber,
            "carModel" to carModel,
            "package" to dataPackage,
            "validUntil" to currentValidUntil,
            "validMode" to if (isAutoValidity) "auto" else "manual",
            "balanceMb" to (AppPrefs.getBalanceMb(this) ?: -1),
            "lastBalanceCheck" to AppPrefs.getUpdated(this),
            "status" to "",
            "lastUpdate" to System.currentTimeMillis(),
            "deviceName" to android.os.Build.MODEL,

            // aliases for legacy screens
            "customerName" to name,
            "customerPhone" to phone,
            "dataPackage" to dataPackage,
            "validityMode" to if (isAutoValidity) "auto" else "manual"
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
