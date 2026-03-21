package com.stereotip.simdata

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var tvWarranty: TextView
    private lateinit var btnWarranty: Button

    private val db = FirebaseFirestore.getInstance()
    private var lineNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvWarranty = findViewById(R.id.tvWarrantyStatus)
        btnWarranty = findViewById(R.id.btnActivateWarranty)

        lineNumber = normalizeLine(TelephonyUtils.getLineNumber(this))

        loadWarranty()

        btnWarranty.setOnClickListener {
            activateWarranty()
        }
    }

    private fun loadWarranty() {
        if (lineNumber.isBlank()) {
            tvWarranty.text = "🛡️ אחריות: לא זוהה"
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", lineNumber)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    tvWarranty.text = "🛡️ אחריות: לא הופעלה"
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val warrantyEnd = doc.getString("warrantyEnd")

                if (warrantyEnd.isNullOrBlank()) {
                    tvWarranty.text = "🛡️ אחריות: לא הופעלה"
                } else {
                    tvWarranty.text = "🛡️ אחריות עד: $warrantyEnd"
                }
            }
    }

    private fun activateWarranty() {
        if (lineNumber.isBlank()) return

        db.collection("customers")
            .whereEqualTo("lineNumber", lineNumber)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "לקוח לא נמצא", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val docRef = doc.reference

                val existing = doc.getString("warrantyStart")

                if (!existing.isNullOrBlank()) {
                    Toast.makeText(
                        this,
                        "האחריות כבר הופעלה במכשיר זה. לשינויים יש לפנות לסטריאו טיפ אביזרי רכב",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                val calendar = Calendar.getInstance()
                val start = calendar.timeInMillis

                calendar.add(Calendar.YEAR, 1)
                val end = calendar.timeInMillis

                val data: HashMap<String, Any?> = hashMapOf(
                    "warrantyStart" to start,
                    "warrantyEnd" to formatDate(end)
                )

                docRef.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "האחריות הופעלה", Toast.LENGTH_SHORT).show()
                        loadWarranty()
                    }
            }
    }

    private fun normalizeLine(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return if (
            normalized == "לא זוהה" ||
            normalized == "לא זוהה מספר" ||
            normalized == "לא אושרו הרשאות"
        ) "" else normalized
    }

    private fun formatDate(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        return "$day/$month/$year"
    }
}
