package com.stereotip.fleet

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CustomerDetailsActivity : AppCompatActivity() {

    private lateinit var tvSummary: TextView
    private lateinit var tvCustomerInfo: TextView

    private val db = FirebaseFirestore.getInstance()
    private val df = SimpleDateFormat("d/M/yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_details)

        tvSummary = findViewById(R.id.tvCustomerSummary)
        tvCustomerInfo = findViewById(R.id.tvCustomerInfo)

        val docId = intent.getStringExtra("docId") ?: return

        loadSummary(docId)
        loadCustomerDetails(docId)
    }

    private fun loadSummary(docId: String) {
        db.collection("customers")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->

                val line = doc.getString("lineNumber") ?: "---"
                val pkg = doc.getString("dataPackage") ?: "---"
                val warranty = doc.getString("warrantyEnd") ?: "לא הופעלה"
                val balance = doc.getLong("currentBalanceMb") ?: 0
                val lastCheck = doc.getLong("lastBalanceCheck")

                val lastCheckText = if (lastCheck != null)
                    df.format(Date(lastCheck))
                else "---"

                tvSummary.text = """
📱 קו: $line

📦 חבילה: $pkg
📊 יתרה: ${balance}MB
🕒 בדיקה: $lastCheckText

🛡️ אחריות: $warranty
                """.trimIndent()
            }
            .addOnFailureListener {
                tvSummary.text = "שגיאה בטעינת נתונים"
            }
    }

    private fun loadCustomerDetails(docId: String) {
        db.collection("customers")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->

                val name = doc.getString("customerName") ?: "---"
                val phone = doc.getString("customerPhone") ?: "---"
                val carModel = doc.getString("carModel") ?: "---"
                val carNumber = doc.getString("carNumber") ?: "---"

                tvCustomerInfo.text = """
👤 שם: $name
☎ טלפון: $phone

🚘 דגם רכב: $carModel
🔢 מספר רכב: $carNumber
                """.trimIndent()
            }
            .addOnFailureListener {
                tvCustomerInfo.text = "שגיאה בטעינת פרטי לקוח"
            }
    }
}
