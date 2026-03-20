package com.stereotip.simdata

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.stereotip.simdata.util.AppPrefs

class TechnicianActivity : AppCompatActivity() {

    private lateinit var tvCustomerName: TextView
    private lateinit var tvCustomerPhone: TextView
    private lateinit var tvCarModel: TextView
    private lateinit var tvCarNumber: TextView
    private lateinit var tvPackage: TextView
    private lateinit var tvBalance: TextView
    private lateinit var tvValidUntil: TextView
    private lateinit var tvLastCheck: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician)

        tvCustomerName = findViewById(R.id.tvCustomerName)
        tvCustomerPhone = findViewById(R.id.tvCustomerPhone)
        tvCarModel = findViewById(R.id.tvCarModel)
        tvCarNumber = findViewById(R.id.tvCarNumber)
        tvPackage = findViewById(R.id.tvPackage)
        tvBalance = findViewById(R.id.tvBalance)
        tvValidUntil = findViewById(R.id.tvValidUntil)
        tvLastCheck = findViewById(R.id.tvLastCheck)

        loadCustomerData()
    }

    private fun loadCustomerData() {
        val rawLine = AppPrefs.getLineNumber(this).orEmpty()
        val lineNumber = normalizeIsraeliNumber(rawLine)

        if (lineNumber.isBlank()) return

        val db = FirebaseFirestore.getInstance()

        db.collection("customers")
            .whereEqualTo("lineNumber", lineNumber)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->

                if (result.isEmpty) {
                    tvCustomerName.text = "לא נמצא לקוח"
                    return@addOnSuccessListener
                }

                val doc = result.documents[0]

                tvCustomerName.text = doc.getString("customerName") ?: "---"
                tvCustomerPhone.text = doc.getString("customerPhone") ?: "---"
                tvCarModel.text = doc.getString("carModel") ?: "---"
                tvCarNumber.text = doc.getString("carNumber") ?: "---"
                tvPackage.text = doc.getString("dataPackage") ?: "---"

                val balance = doc.getLong("currentBalanceMb")
                tvBalance.text = balance?.let { "${it}MB" } ?: "---"

                tvValidUntil.text = doc.getString("validUntil") ?: "---"

                val lastCheck = doc.getLong("lastBalanceCheck")
                tvLastCheck.text = lastCheck?.let {
                    android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", it).toString()
                } ?: "---"
            }
    }

    private fun normalizeIsraeliNumber(value: String): String {
        var v = value.trim().replace(" ", "").replace("-", "")

        if (v.startsWith("+972")) {
            v = "0" + v.removePrefix("+972")
        } else if (v.startsWith("972")) {
            v = "0" + v.removePrefix("972")
        } else if (v.startsWith("5")) {
            v = "0$v"
        }

        return v
    }
}
