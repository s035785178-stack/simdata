package com.stereotip.simdata

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
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
    private var currentValidityMode = "auto"
    private var currentValidUntil = ""
    private var packageInitialized = false

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
        refreshFromFirebase()

        btnValidityMode.setOnClickListener {
            showValidityOptionsDialog()
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
        refreshFromFirebase()
    }

    private fun setupPackageSpinner() {
        val adapter = ArrayAdapter(this, R.layout.spinner_item_white, packages)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
        spinnerPackage.adapter = adapter
        spinnerPackage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!packageInitialized) {
                    packageInitialized = true
                    return
                }
                val selectedPackage = packages.getOrNull(position).orEmpty()
                AppPrefs.setDataPackage(this@CustomerDetailsActivity, selectedPackage)
                if (currentValidityMode == "auto") {
                    currentValidUntil = calculateAutoValidity(selectedPackage)
                    AppPrefs.setValid(this@CustomerDetailsActivity, currentValidUntil)
                    updateValidityButtonText()
                    loadCustomerSummary()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun loadExistingData() {
        etName.setText(AppPrefs.getCustomerName(this))
        etPhone.setText(normalizeDisplayPhone(AppPrefs.getCustomerPhone(this)))
        etCarModel.setText(AppPrefs.getCarModel(this))
        etCarNumber.setText(AppPrefs.getCarNumber(this))
        currentValidityMode = AppPrefs.getValidityMode(this)
        currentValidUntil = AppPrefs.getValid(this).orEmpty()

        val savedPackage = AppPrefs.getDataPackage(this)
        val index = packages.indexOf(savedPackage).takeIf { it >= 0 } ?: 0
        spinnerPackage.setSelection(index)
        updateValidityButtonText()
        loadCustomerSummary()
    }

    private fun refreshFromFirebase() {
        val line = normalizeDisplayPhone(AppPrefs.getLineNumber(this).orEmpty())
        val phone = normalizeDisplayPhone(AppPrefs.getCustomerPhone(this))

        if (line.isBlank() && phone.isBlank()) {
            showMissingCustomer(false)
            loadCustomerSummary()
            return
        }

        val query = when {
            line.isNotBlank() -> db.collection("customers").whereEqualTo("lineNumber", line).limit(1)
            else -> db.collection("customers").whereEqualTo("customerPhone", phone).limit(1)
        }

        query.get()
            .addOnSuccessListener { result ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (result.isEmpty) {
                    AppPrefs.clearCustomerProfile(this)
                    currentValidityMode = "auto"
                    currentValidUntil = ""
                    showMissingCustomer(true)
                    clearEditableFields()
                    loadCustomerSummary()
                    return@addOnSuccessListener
                }

                applyCustomerDocument(result.documents.first())
                tvMissingCustomer.visibility = View.GONE
                loadCustomerSummary(result.documents.first())
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "שגיאה בטעינת נתוני לקוח", Toast.LENGTH_SHORT).show()
                    loadCustomerSummary()
                }
            }
    }

    private fun applyCustomerDocument(doc: DocumentSnapshot) {
        val name = doc.getString("customerName").orEmpty()
        val phone = normalizeDisplayPhone(doc.getString("customerPhone").orEmpty())
        val carModel = doc.getString("carModel").orEmpty()
        val carNumber = doc.getString("carNumber").orEmpty()
        val pkg = doc.getString("dataPackage").orEmpty().ifBlank { "לא ידוע / אין" }
        val line = normalizeDisplayPhone(doc.getString("lineNumber").orEmpty())
        val valid = doc.getString("validUntil").orEmpty()

        AppPrefs.setCustomerName(this, name)
        AppPrefs.setCustomerPhone(this, phone)
        AppPrefs.setCarModel(this, carModel)
        AppPrefs.setCarNumber(this, carNumber)
        AppPrefs.setDataPackage(this, pkg)
        AppPrefs.setLineNumber(this, line)
        AppPrefs.setValid(this, valid)

        etName.setText(name)
        etPhone.setText(phone)
        etCarModel.setText(carModel)
        etCarNumber.setText(carNumber)

        val index = packages.indexOf(pkg).takeIf { it >= 0 } ?: 0
        spinnerPackage.setSelection(index, false)

        if (currentValidityMode == "auto" || valid.isNotBlank()) {
            currentValidUntil = if (currentValidityMode == "auto") calculateAutoValidity(pkg) else valid
        }
        if (currentValidityMode != "manual") {
            currentValidityMode = "auto"
        }
        updateValidityButtonText()
    }

    private fun loadCustomerSummary(doc: DocumentSnapshot? = null) {
        val localLine = normalizeDisplayPhone(AppPrefs.getLineNumber(this).orEmpty()).ifBlank { "---" }
        val localPackage = doc?.getString("dataPackage").orEmpty().ifBlank { AppPrefs.getDataPackage(this).ifBlank { "---" } }
        val balance = doc?.getLong("currentBalanceMb") ?: doc?.getLong("balanceMb") ?: AppPrefs.getBalanceMb(this)?.toLong()
        val localBalance = if (balance != null && balance >= 0) "${balance}MB" else "---"
        val valid = doc?.getString("validUntil").orEmpty().ifBlank { AppPrefs.getValid(this).orEmpty().ifBlank { "לא ידוע" } }
        val lastCheck = doc?.getLong("lastBalanceCheck") ?: doc?.getLong("lastUpdate") ?: AppPrefs.getUpdated(this)
        val warranty = doc?.getString("warrantyEnd").orEmpty().ifBlank { "---" }
        tvCustomerSummary.text = buildSummary(
            line = localLine,
            pkg = localPackage,
            balance = localBalance,
            lastCheck = formatTimestamp(lastCheck),
            valid = valid,
            warranty = warranty
        )
    }

    private fun buildSummary(line: String, pkg: String, balance: String, lastCheck: String, valid: String, warranty: String): String {
        return """
📱 קו: $line

📦 חבילה: $pkg
📊 יתרה: $balance
🕒 בדיקה: $lastCheck
📅 תוקף: $valid

🛡️ אחריות: $warranty
        """.trimIndent()
    }

    private fun saveCustomerData() {
        val name = etName.text.toString().trim()
        val phone = normalizeDisplayPhone(etPhone.text.toString().trim())
        val carModel = etCarModel.text.toString().trim()
        val carNumber = etCarNumber.text.toString().trim()
        val selectedPackage = spinnerPackage.selectedItem?.toString().orEmpty().ifBlank { "לא ידוע / אין" }
        val line = normalizeDisplayPhone(AppPrefs.getLineNumber(this).orEmpty())

        if (currentValidityMode == "auto") {
            currentValidUntil = calculateAutoValidity(selectedPackage)
        }

        AppPrefs.setCustomerName(this, name)
        AppPrefs.setCustomerPhone(this, phone)
        AppPrefs.setCarModel(this, carModel)
        AppPrefs.setCarNumber(this, carNumber)
        AppPrefs.setDataPackage(this, selectedPackage)
        AppPrefs.setValid(this, currentValidUntil)
        AppPrefs.setValidityMode(this, currentValidityMode)

        val payload = hashMapOf<String, Any?>(
            "customerName" to name,
            "customerPhone" to phone,
            "carModel" to carModel,
            "carNumber" to carNumber,
            "dataPackage" to selectedPackage,
            "validUntil" to currentValidUntil,
            "lastUpdate" to System.currentTimeMillis()
        )
        if (line.isNotBlank()) payload["lineNumber"] = line

        if (line.isBlank() && phone.isBlank()) {
            Toast.makeText(this, "נשמר מקומית בלבד - אין מזהה לקוח", Toast.LENGTH_SHORT).show()
            loadCustomerSummary()
            return
        }

        val query = when {
            line.isNotBlank() -> db.collection("customers").whereEqualTo("lineNumber", line).limit(1)
            else -> db.collection("customers").whereEqualTo("customerPhone", phone).limit(1)
        }

        query.get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "לא נמצא לקוח במערכת", Toast.LENGTH_SHORT).show()
                    showMissingCustomer(true)
                    loadCustomerSummary()
                    return@addOnSuccessListener
                }

                result.documents.first().reference
                    .set(payload, SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "פרטי הלקוח נשמרו", Toast.LENGTH_SHORT).show()
                        tvMissingCustomer.visibility = View.GONE
                        loadCustomerSummary(result.documents.first())
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "שגיאה בשמירת פרטי הלקוח", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "שגיאה באיתור לקוח", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showValidityOptionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("תוקף חבילה")
            .setItems(arrayOf("אוטומטי לפי חבילה", "ידני", "נקה תוקף")) { _, which ->
                when (which) {
                    0 -> {
                        currentValidityMode = "auto"
                        currentValidUntil = calculateAutoValidity(spinnerPackage.selectedItem?.toString().orEmpty())
                        AppPrefs.setValidityMode(this, currentValidityMode)
                        AppPrefs.setValid(this, currentValidUntil)
                        updateValidityButtonText()
                        loadCustomerSummary()
                    }
                    1 -> showManualDatePicker()
                    2 -> {
                        currentValidityMode = "manual"
                        currentValidUntil = ""
                        AppPrefs.setValidityMode(this, currentValidityMode)
                        AppPrefs.setValid(this, "")
                        updateValidityButtonText()
                        loadCustomerSummary()
                    }
                }
            }
            .show()
    }

    private fun showManualDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val picked = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            currentValidityMode = "manual"
            currentValidUntil = formatter.format(picked.time)
            AppPrefs.setValidityMode(this, currentValidityMode)
            AppPrefs.setValid(this, currentValidUntil)
            updateValidityButtonText()
            loadCustomerSummary()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun calculateAutoValidity(dataPackage: String): String {
        val cal = Calendar.getInstance()
        when (dataPackage) {
            "100GB לשנתיים" -> cal.add(Calendar.YEAR, 2)
            "36GB ל-60 חודשים" -> cal.add(Calendar.MONTH, 60)
            "4GB לחודשיים" -> cal.add(Calendar.MONTH, 2)
            else -> return "לא ידוע"
        }
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
    }

    private fun updateValidityButtonText() {
        val suffix = when {
            currentValidUntil.isBlank() -> "לא ידוע"
            else -> currentValidUntil
        }
        val modeLabel = if (currentValidityMode == "manual") "ידני" else "אוטומטי"
        btnValidityMode.text = "📅 תוקף חבילה ($modeLabel)
$suffix"
    }

    private fun showMissingCustomer(show: Boolean) {
        tvMissingCustomer.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun clearEditableFields() {
        etName.setText("")
        etPhone.setText("")
        etCarModel.setText("")
        etCarNumber.setText("")
        spinnerPackage.setSelection(0, false)
        currentValidUntil = ""
        updateValidityButtonText()
    }

    private fun normalizeDisplayPhone(phone: String): String {
        val normalized = PhoneUtils.normalizeToLocal(phone)
        return if (normalized == "לא זוהה" || normalized == "לא זוהה מספר" || normalized == "לא אושרו הרשאות") "" else normalized
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return "---"
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}
