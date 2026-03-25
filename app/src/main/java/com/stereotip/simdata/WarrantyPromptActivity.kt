package com.stereotip.simdata

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var btnCancelWarranty: Button

    private lateinit var successContainer: View
    private lateinit var tvSuccessIcon: TextView
    private lateinit var tvSuccessTitle: TextView
    private lateinit var tvSuccessCountdown: TextView

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.getDefault())
    private var countdownTimer: CountDownTimer? = null
    private var isContinuing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warranty_prompt)

        logo = findViewById(R.id.logoWarranty)
        tvTitle = findViewById(R.id.tvWarrantyTitle)
        tvSubtitle = findViewById(R.id.tvWarrantySubtitle)
        btnActivateWarranty = findViewById(R.id.btnActivateWarrantyPrompt)
        btnSkipWarranty = findViewById(R.id.btnSkipWarrantyPrompt)
        btnCancelWarranty = findViewById(R.id.btnCancelWarrantyPrompt)

        successContainer = findViewById(R.id.successContainer)
        tvSuccessIcon = findViewById(R.id.tvSuccessIcon)
        tvSuccessTitle = findViewById(R.id.tvSuccessTitle)
        tvSuccessCountdown = findViewById(R.id.tvSuccessCountdown)

        if (AppPrefs.isWarrantyBlockedNotOurs(this)) {
            btnActivateWarranty.isEnabled = false
            btnActivateWarranty.alpha = 0.55f
            tvSubtitle.text = "האחריות בוטלה למכשיר זה כי סומן שלא נרכש בסטריאו טיפ"
        }

        btnActivateWarranty.setOnClickListener {
            if (AppPrefs.isWarrantyBlockedNotOurs(this)) {
                Toast.makeText(
                    this,
                    "המכשיר לא נרכש מסטריאו טיפ לא ניתן להפעיל אחריות",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            activateWarrantyAndContinue()
        }

        btnSkipWarranty.setOnClickListener {
            continueToBalance()
        }

        btnCancelWarranty.setOnClickListener {
            markDeviceAsNotOursAndContinue()
        }
    }

    private fun markDeviceAsNotOursAndContinue() {
        if (isContinuing) return

        AppPrefs.setWarrantyBlockedNotOurs(this, true)
        AppPrefs.clearWarrantyInfo(this)

        btnActivateWarranty.isEnabled = false
        btnActivateWarranty.alpha = 0.55f
        btnSkipWarranty.isEnabled = false
        btnCancelWarranty.isEnabled = false

        showInfoState(
            icon = "🚫",
            message = "האחריות בוטלה - המכשיר לא נרכש בסטריאו טיפ"
        )
    }

    private fun activateWarrantyAndContinue() {
        if (isContinuing) return

        if (AppPrefs.isWarrantyBlockedNotOurs(this)) {
            Toast.makeText(
                this,
                "המכשיר לא נרכש מסטריאו טיפ לא ניתן להפעיל אחריות",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val savedPhone = AppPrefs.getCustomerPhone(this).orEmpty().trim()
        val savedLine = AppPrefs.getLineNumber(this).orEmpty().trim()

        if (savedPhone.isBlank() && savedLine.isBlank()) {
            Toast.makeText(this, "לא נמצא לקוח להפעלת אחריות", Toast.LENGTH_SHORT).show()
            continueToBalance()
            return
        }

        btnActivateWarranty.isEnabled = false
        btnSkipWarranty.isEnabled = false
        btnCancelWarranty.isEnabled = false
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
                        showInfoState(
                            icon = "✅",
                            message = "האחריות כבר פעילה על מכשיר זה"
                        )
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
                            AppPrefs.setWarrantyBlockedNotOurs(this, false)
                            showInfoState(
                                icon = "✅",
                                message = "האחריות הופעלה בהצלחה"
                            )
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
                    showInfoState(
                        icon = "✅",
                        message = "האחריות כבר פעילה על מכשיר זה"
                    )
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
                        AppPrefs.setWarrantyBlockedNotOurs(this, false)
                        showInfoState(
                            icon = "✅",
                            message = "האחריות הופעלה בהצלחה"
                        )
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

    private fun showInfoState(icon: String, message: String) {
        if (isContinuing) return

        btnActivateWarranty.visibility = View.GONE
        btnSkipWarranty.visibility = View.GONE
        btnCancelWarranty.visibility = View.GONE

        successContainer.visibility = View.VISIBLE
        tvSuccessIcon.text = icon
        tvSuccessTitle.text = message

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(3000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000L).toInt().coerceAtLeast(1)
                tvSuccessCountdown.text = "מעביר לבדיקה אוטומטית בעוד $seconds שניות..."
            }

            override fun onFinish() {
                tvSuccessCountdown.text = "מעביר לבדיקה אוטומטית..."
                continueToBalance()
            }
        }.start()
    }

    private fun restoreButtons() {
        btnActivateWarranty.isEnabled = !AppPrefs.isWarrantyBlockedNotOurs(this)
        btnActivateWarranty.alpha = if (AppPrefs.isWarrantyBlockedNotOurs(this)) 0.55f else 1f
        btnSkipWarranty.isEnabled = true
        btnCancelWarranty.isEnabled = true
        btnActivateWarranty.text = "הפעלת אחריות על מכשיר זה🛡️"
    }

    private fun continueToBalance() {
        if (isContinuing) return
        isContinuing = true

        countdownTimer?.cancel()

        val intent = Intent(this, BalanceActivity::class.java).apply {
            putExtra("auto_start_check", true)
            putExtra("from_registration", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}