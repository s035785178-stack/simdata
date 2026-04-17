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
    import java.text.SimpleDateFormat
    import java.util.Date
    import java.util.Locale

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
                loadCustomerSummary()
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
            val localLine = normalizeDisplayPhone(AppPrefs.getLineNumber(this).orEmpty())
            val localPackage = AppPrefs.getDataPackage(this).ifBlank { "---" }
            val localBalance = AppPrefs.getBalanceMb(this)?.let { "${it}MB" } ?: "---"
            val localValid = AppPrefs.getValid(this).orEmpty().ifBlank { "לא ידוע" }
            val localUpdated = formatTimestamp(AppPrefs.getUpdated(this))

            if (localLine.isBlank()) {
                tvCustomerSummary.text = buildSummary(
                    line = "---",
                    pkg = localPackage,
                    balance = localBalance,
                    lastCheck = localUpdated,
                    valid = localValid,
                    warranty = "---"
                )
                return
            }

            tvCustomerSummary.text = buildSummary(
                line = localLine,
                pkg = localPackage,
                balance = localBalance,
                lastCheck = localUpdated,
                valid = localValid,
                warranty = "---"
            )

            db.collection("customers")
                .whereEqualTo("lineNumber", localLine)
                .limit(1)
                .get()
                .addOnSuccessListener { result ->
                    if (result.isEmpty) {
                        return@addOnSuccessListener
                    }

                    val doc = result.documents.first()
                    val pkg = doc.getString("dataPackage").orEmpty().ifBlank { localPackage }
                    val warranty = doc.getString("warrantyEnd").orEmpty().ifBlank { "לא הופעלה" }
                    val valid = doc.getString("validUntil").orEmpty().ifBlank { localValid }
                    val balance = doc.getLong("currentBalanceMb") ?: doc.getLong("balanceMb")
                    val lastCheck = doc.getLong("lastBalanceCheck") ?: doc.getLong("lastUpdate")

                    val balanceText = if (balance != null) "${balance}MB" else localBalance
                    val lastCheckText = if (lastCheck != null && lastCheck > 0L) {
                        formatTimestamp(lastCheck)
                    } else {
                        localUpdated
                    }

                    tvCustomerSummary.text = buildSummary(
                        line = localLine,
                        pkg = pkg,
                        balance = balanceText,
                        lastCheck = lastCheckText,
                        valid = valid,
                        warranty = warranty
                    )
                }
                .addOnFailureListener {
                    tvCustomerSummary.text = buildSummary(
                        line = localLine,
                        pkg = localPackage,
                        balance = localBalance,
                        lastCheck = localUpdated,
                        valid = localValid,
                        warranty = "---"
                    )
                }
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

        private fun formatTimestamp(timestamp: Long): String {
            if (timestamp <= 0L) return "---"
            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return formatter.format(Date(timestamp))
        }
    }
