package com.stereotip.simdata

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stereotip.simdata.util.AppPrefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WarrantyPromptActivity : AppCompatActivity() {

    private lateinit var logo: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnActivateWarranty: Button
    private lateinit var btnSkipWarranty: Button

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warranty_prompt)

        logo = findViewById(R.id.logoWarranty)
        tvTitle = findViewById(R.id.tvWarrantyTitle)
        tvSubtitle = findViewById(R.id.tvWarrantySubtitle)
        btnActivateWarranty = findViewById(R.id.btnActivateWarrantyPrompt)
        btnSkipWarranty = findViewById(R.id.btnSkipWarrantyPrompt)

        btnActivateWarranty.setOnClickListener {
            activateWarrantyAndContinue()
        }

        btnSkipWarranty.setOnClickListener {
            continueToBalance()
        }
    }

    private fun activateWarrantyAndContinue() {
        val savedPhone = AppPrefs.getCustomerPhone(this).orEmpty().trim()
        val savedLine = AppPrefs.getLineNumber(this).orEmpty().trim()

        if (savedPhone.isBlank() && savedLine.isBlank()) {
            Toast.makeText(this, "לא נמצא לקוח להפעלת אחריות", Toast.LENGTH_SHORT).show()
            continueToBalance()
            return
        }

        btnActivateWarranty.isEnabled = false
        btnSkipWarranty.isEnabled = false
        btnActivateWarranty.text = "מפעיל אחריות..."

        if (savedPhone.isNotBlank()) {
            db.collection("customers")
                .document(savedPhone)
                .get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        restoreButtons()
                        Toast.makeText(this, "לא נמצא לקוח במערכת", Toast.LENGTH_SHORT).show()
                        continueToBalance()
                        return@addOnSuccessListener
                    }

                    val existingWarrantyStart = doc.getLong("warrantyStart")
                    val existingWarrantyEnd = doc.getString("warrantyEnd").orEmpty()

                    if (existingWarrantyStart != null && existingWarrantyStart > 0L && existingWarrantyEnd.isNotBlank()) {
                        showAlreadyActivatedDialog()
                        return@addOnSuccessListener
                    }

                    val startMillis = System.currentTimeMillis()
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = startMillis
                    cal.add(Calendar.YEAR, 1)
                    val endDate = dateFormat.format(cal.time)

                    val data: HashMap<String, Any?> = hashMapOf(
                        "warrantyStart" to startMillis,
                        "warrantyEnd" to endDate,
                        "lastUpdate" to System.currentTimeMillis()
                    )

                    doc.reference.set(data, SetOptions.merge())
                        .addOnSuccessListener {
                            showSuccessDialog()
                        }
                        .addOnFailureListener {
                            restoreButtons()
                            Toast.makeText(this, "שגיאה בהפעלת אחריות", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    restoreButtons()
                    Toast.makeText(this, "שגיאה באיתור לקוח", Toast.LENGTH_SHORT).show()
                }

            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", savedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    restoreButtons()
                    Toast.makeText(this, "לא נמצא לקוח במערכת", Toast.LENGTH_SHORT).show()
                    continueToBalance()
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val existingWarrantyStart = doc.getLong("warrantyStart")
                val existingWarrantyEnd = doc.getString("warrantyEnd").orEmpty()

                if (existingWarrantyStart != null && existingWarrantyStart > 0L && existingWarrantyEnd.isNotBlank()) {
                    showAlreadyActivatedDialog()
                    return@addOnSuccessListener
                }

                val startMillis = System.currentTimeMillis()
                val cal = Calendar.getInstance()
                cal.timeInMillis = startMillis
                cal.add(Calendar.YEAR, 1)
                val endDate = dateFormat.format(cal.time)

                val data: HashMap<String, Any?> = hashMapOf(
                    "warrantyStart" to startMillis,
                    "warrantyEnd" to endDate,
                    "lastUpdate" to System.currentTimeMillis()
                )

                doc.reference.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        showSuccessDialog()
                    }
                    .addOnFailureListener {
                        restoreButtons()
                        Toast.makeText(this, "שגיאה בהפעלת אחריות", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                restoreButtons()
                Toast.makeText(this, "שגיאה באיתור לקוח", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSuccessDialog() {
        btnActivateWarranty.text = "✔️ האחריות הופעלה"

        AlertDialog.Builder(this)
            .setTitle("✅")
            .setMessage("האחריות הופעלה בהצלחה")
            .setCancelable(false)
            .setPositiveButton("המשך לבדיקה") { _, _ ->
                continueToBalance()
            }
            .show()
    }

    private fun showAlreadyActivatedDialog() {
        btnActivateWarranty.text = "✔️ האחריות כבר פעילה"

        AlertDialog.Builder(this)
            .setTitle("🛡️")
            .setMessage("האחריות כבר הופעלה על מכשיר זה")
            .setCancelable(false)
            .setPositiveButton("המשך לבדיקה") { _, _ ->
                continueToBalance()
            }
            .show()
    }

    private fun restoreButtons() {
        btnActivateWarranty.isEnabled = true
        btnSkipWarranty.isEnabled = true
        btnActivateWarranty.text = "הפעלת אחריות על מכשיר זה🛡️"
    }

    private fun continueToBalance() {
        val intent = Intent(this, BalanceActivity::class.java).apply {
            putExtra("auto_start_check", true)
            putExtra("from_registration", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
