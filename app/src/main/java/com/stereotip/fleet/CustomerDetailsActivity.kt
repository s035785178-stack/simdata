package com.stereotip.fleet

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CustomerDetailsActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private val db = FirebaseFirestore.getInstance()
    private val df = SimpleDateFormat("d/M/yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_details)

        tvInfo = findViewById(R.id.tvCustomerInfo)

        val docId = intent.getStringExtra("docId") ?: return

        loadCustomer(docId)
    }

    private fun loadCustomer(docId: String) {
        db.collection("customers")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->

                val name = doc.getString("customerName") ?: "---"
                val phone = doc.getString("customerPhone") ?: "---"
                val line = doc.getString("lineNumber") ?: "---"
                val pkg = doc.getString("dataPackage") ?: "---"
                val warranty = doc.getString("warrantyEnd") ?: "---"
                val balance = doc.getLong("currentBalanceMb") ?: 0
                val lastCheck = doc.getLong("lastBalanceCheck")

                val lastCheckText = if (lastCheck != null)
                    df.format(Date(lastCheck))
                else "---"

                tvInfo.text = """
👤 $name
☎ $phone
📱 $line

📦 חבילה: $pkg
📊 יתרה: ${balance}MB
🕒 בדיקה: $lastCheckText

🛡️ אחריות עד: $warranty
                """.trimIndent()
            }
    }
}
