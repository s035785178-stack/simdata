package com.stereotip.simdata

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.Formatter
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.QrUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.net.URLEncoder

class TechnicianActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician)

        tvInfo = findViewById(R.id.tvTechInfo)

        findViewById<Button>(R.id.btnTechNetwork).setOnClickListener {
            startActivity(Intent(this, NetworkCheckActivity::class.java))
        }

        findViewById<Button>(R.id.btnTechSupportQr).setOnClickListener {
            showTechQr()
        }

        findViewById<Button>(R.id.btnEditCustomer).setOnClickListener {
            startActivity(Intent(this, CustomerDetailsActivity::class.java))
        }

        findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            AppPrefs.clearHistory(this)
            bindInfo()
            Toast.makeText(this, "ההיסטוריה המקומית נוקתה", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnClearAll).setOnClickListener {
            AppPrefs.clearAll(this)
            bindInfo()
            Toast.makeText(this, "נתוני הלקוח אופסו", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnBackTech).setOnClickListener {
            finish()
        }

        bindInfo()
    }

    override fun onResume() {
        super.onResume()
        bindInfo()
    }

    private fun bindInfo() {
        val network = Telephony
