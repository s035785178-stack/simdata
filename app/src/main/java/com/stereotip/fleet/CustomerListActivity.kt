package com.stereotip.fleet

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class CustomerListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val db = FirebaseFirestore.getInstance()
    private val customers = mutableListOf<String>()
    private val ids = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_list)

        listView = findViewById(R.id.listCustomers)

        loadCustomers()

        listView.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, CustomerDetailsActivity::class.java)
            intent.putExtra("docId", ids[position])
            startActivity(intent)
        }
    }

    private fun loadCustomers() {
        db.collection("customers")
            .get()
            .addOnSuccessListener { result ->
                customers.clear()
                ids.clear()

                for (doc in result) {
                    val name = doc.getString("customerName") ?: "---"
                    val line = doc.getString("lineNumber") ?: "---"

                    customers.add("$name\n$line")
                    ids.add(doc.id)
                }

                listView.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    customers
                )
            }
    }
}
