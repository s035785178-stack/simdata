package com.stereotip.simdata

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.telephony.TelephonyManager
import android.text.format.DateFormat
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
import com.stereotip.simdata.util.PhoneUtils

class CustomerDetailsActivity : AppCompatActivity() {

    private lateinit var tvHelloTitle: TextView
    private lateinit var tvAlertBox: TextView

    private lateinit var tvLineValue: TextView
    private lateinit var tvOperatorValue: TextView
    private lateinit var tvInternetValue: TextView

    private lateinit var tvPackageValue: TextView
    private lateinit var tvBalanceValue: TextView
    private lateinit var tvLastCheckValue: TextView

    private lateinit var tvSupportPhone: TextView

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner

    private lateinit var btnRefreshAccount: Button
    private lateinit var btnNavigateToStore: Button
    private lateinit var btnSaveCustomer: Button
    private lateinit var btnBackCustomer: Button

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

        tvHelloTitle = findViewById(R.id.tvHelloTitle)
        tvAlertBox = findViewById(R.id.tvAlertBox)

        tvLineValue = findViewById(R.id.tvLineValue)
        tvOperatorValue = findViewById(R.id.tvOperatorValue)
        tvInternetValue = findViewById(R.id.tvInternetValue)

        tvPackageValue = findViewById(R.id.tvPackageValue)
        tvBalanceValue = findViewById(R.id.tvBalanceValue)
        tvLastCheckValue = findViewById(R.id.tvLastCheckValue)

        tvSupportPhone = findViewById(R.id.tvSupportPhone)

        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etCarModel = findViewById(R.id.etCarModel)
        etCarNumber = findViewById(R.id.etCarNumber)
        spinnerPackage = findViewById(R.id.spinnerPackage)

        btnRefreshAccount = findViewById(R.id.btnRefreshAccount)
        btnNavigateToStore = findViewById(R.id.btnNavigateToStore)
        btnSaveCustomer = findViewById(R.id.btnSaveCustomer)
        btnBackCustomer = findViewById(R.id.btnBackCustomer)

        setupPackageSpinner()
        loadLocalDataToForm()
        loadAccountScreen()

        btnRefreshAccount.setOnClickListener {
            loadAccountScreen(forceRefreshToast = true)
        }

        btnNavigateToStore.setOnClickListener {
            openWazeNavigation()
        }

        tvSupportPhone.setOnClickListener {
            openDialer("035785178")
        }

        btnSaveCustomer.setOnClickListener {
            saveCustomerData()
        }

        btnBackCustomer.setOnClickListener {
            finish()
        }
    }

    private fun setupPackageSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            packages
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPackage.adapter = adapter
    }

    private fun loadLocalDataToForm() {
        val customerName = AppPrefs.getCustomerName(this).trim()
        etName.setText(customerName)
        etPhone.setText(normalizeDisplayPhone(AppPrefs.getCustomerPhone(this)))
        etCarModel.setText(AppPrefs.getCarModel(this))
        etCarNumber.setText(AppPrefs.getCarNumber(this))

        tvHelloTitle.text = if (customerName.isNotBlank()) {
            "החשבון שלי - $customerName"
        } else {
            "החשבון שלי"
        }

        val savedPackage = AppPrefs.getDataPackage(this)
        val index = packages.indexOf(savedPackage).takeIf { it != null && it >= 0 } ?: 0
        spinnerPackage.setSelection(index)
    }

    private fun loadAccountScreen(forceRefreshToast: Boolean = false) {
        loadLocalDataToForm()
        loadLineCard()
        loadPackageCard()
        updateAlertsCard()

        if (forceRefreshToast) {
            Toast.makeText(this, "בוצע רענון נתונים", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadLineCard() {
        val lineNumber = normalizeDisplayPhone(AppPrefs.getLineNumber(this) ?: "")
        tvLineValue.text = if (lineNumber.isBlank()) "---" else lineNumber
        tvOperatorValue.text = getSimOperatorDisplay()
        tvInternetValue.text = if (isInternetConnected()) "מחובר ✅" else "לא מחובר ❌"
    }

    private fun loadPackageCard() {
        val savedPackage = AppPrefs.getDataPackage(this).ifBlank { "לא ידוע / אין" }
        val balance = AppPrefs.getBalanceMb(this)
        val updated = AppPrefs.getUpdated(this)
        val lineNumber = normalizeDisplayPhone(AppPrefs.getLineNumber(this) ?: "")

        tvPackageValue.text = savedPackage
        tvBalanceValue.text = balance?.let { "$it MB" } ?: "---"
        tvLastCheckValue.text = if (updated > 0L) {
            DateFormat.format("dd/MM/yyyy HH:mm", updated).toString()
        } else {
            "---"
        }

        if (lineNumber.isBlank()) {
            updateAlertsCard()
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", lineNumber)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val doc = result.documents.first()

                    val remotePackage = doc.getString("dataPackage").orEmpty()
                    val remoteBalance = doc.getLong("currentBalanceMb") ?: doc.getLong("balanceMb")
                    val lastCheck = doc.getLong("lastBalanceCheck") ?: doc.getLong("lastUpdate")

                    if (remotePackage.isNotBlank()) {
                        tvPackageValue.text = remotePackage
                        AppPrefs.setDataPackage(this, remotePackage)
                    }

                    if (remoteBalance != null) {
                        tvBalanceValue.text = "$remoteBalance MB"
                    }

                    if (lastCheck != null && lastCheck > 0L) {
                        tvLastCheckValue.text =
                            DateFormat.format("dd/MM/yyyy HH:mm", lastCheck).toString()
                    }
                }

                updateAlertsCard()
            }
            .addOnFailureListener {
                updateAlertsCard()
            }
    }

    private fun updateAlertsCard() {
        val alerts = mutableListOf<String>()

        if (!isInternetConnected()) {
            alerts.add("⚠️ אין חיבור לאינטרנט כרגע")
        }

        val balance = AppPrefs.getBalanceMb(this)
        if (balance != null && balance in 0..1024) {
            alerts.add("📉 יתרת הגלישה נמוכה - מומלץ לבדוק או לחדש חבילה")
        }

        val lineNumber = normalizeDisplayPhone(AppPrefs.getLineNumber(this) ?: "")
        if (lineNumber.isBlank()) {
            alerts.add("📱 לא זוהה מספר קו במכשיר")
        }

        val packageName = AppPrefs.getDataPackage(this)
        if (packageName.isBlank() || packageName == "לא ידוע / אין") {
            alerts.add("📦 חבילת הגלישה לא זוהתה עדיין")
        }

        tvAlertBox.text = if (alerts.isEmpty()) {
            "✅ הכל תקין כרגע"
        } else {
            alerts.joinToString("\n")
        }
    }

    private fun saveCustomerData() {
        val name = etName.text.toString().trim()
        val phone = normalizeDisplayPhone(etPhone.text.toString())
        val carModel = etCarModel.text.toString().trim()
        val carNumber = etCarNumber.text.toString().trim()
        val dataPackage = spinnerPackage.selectedItem?.toString().orEmpty()

        if (name.isBlank()) {
            etName.error = "נא למלא שם"
            return
        }

        if (phone.isBlank()) {
            etPhone.error = "נא למלא טלפון"
            return
        }

        AppPrefs.setCustomerName(this, name)
        AppPrefs.setCustomerPhone(this, phone)
        AppPrefs.setCarModel(this, carModel)
        AppPrefs.setCarNumber(this, carNumber)
        AppPrefs.setDataPackage(this, dataPackage)

        val lineNumber = normalizeDisplayPhone(AppPrefs.getLineNumber(this) ?: "")
        val now = System.currentTimeMillis()

        val data = hashMapOf(
            "customerName" to name,
            "customerPhone" to phone,
            "carModel" to carModel,
            "carNumber" to carNumber,
            "dataPackage" to dataPackage,
            "lineNumber" to lineNumber,
            "installationId" to AppPrefs.getInstallationId(this),
            "lastUpdate" to now
        )

        db.collection("customers")
            .document(phone)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "הפרטים נשמרו בהצלחה", Toast.LENGTH_SHORT).show()
                loadAccountScreen()
            }
            .addOnFailureListener {
                Toast.makeText(this, "הפרטים נשמרו מקומית בלבד", Toast.LENGTH_SHORT).show()
                loadAccountScreen()
            }
    }

    private fun getSimOperatorDisplay(): String {
        return try {
            val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val simName = telephonyManager.simOperatorName?.trim().orEmpty()
            val networkName = telephonyManager.networkOperatorName?.trim().orEmpty()

            when {
                simName.isNotBlank() -> simName
                networkName.isNotBlank() -> networkName
                else -> "לא זוהה"
            }
        } catch (_: Exception) {
            "לא זוהה"
        }
    }

    private fun isInternetConnected(): Boolean {
        return try {
            val connectivityManager =
                getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    private fun openWazeNavigation() {
        val url = "https://waze.com/ul?q=" + Uri.encode("אברבנאל 113 בני ברק")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "לא הצלחנו לפתוח ניווט", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDialer(phone: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        } catch (_: Exception) {
            Toast.makeText(this, "לא הצלחנו לפתוח חיוג", Toast.LENGTH_SHORT).show()
        }
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