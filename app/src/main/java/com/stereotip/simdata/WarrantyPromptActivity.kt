package com.stereotip.simdata

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var btnNoWarrantyExternal: Button

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

        successContainer = findViewById(R.id.successContainer)
        tvSuccessIcon = findViewById(R.id.tvSuccessIcon)
        tvSuccessTitle = findViewById(R.id.tvSuccessTitle)
        tvSuccessCountdown = findViewById(R.id.tvSuccessCountdown)

        setupNoWarrantyButton()

        btnActivateWarranty.setOnClickListener {
            activateWarrantyAndContinue()
        }

        btnSkipWarranty.setOnClickListener {
            continueToBalance()
        }

        btnNoWarrantyExternal.setOnClickListener {
            markNoWarrantyAndContinue()
        }
    }

    private fun setupNoWarrantyButton() {
        val parent = btnSkipWarranty.parent as? ViewGroup
        if (parent == null) {
            throw IllegalStateException("Parent for warranty buttons not found")
        }

        btnNoWarrantyExternal = Button(this).apply {
            id = View.generateViewId()
            text = "אין אחריות (נרכש במקום אחר)"
            isAllCaps = false
            setTextColor(btnSkipWarranty.currentTextColor)
            textSize = 17f
            typeface = btnSkipWarranty.typeface
            background = btnSkipWarranty.background.constantState?.newDrawable()?.mutate()
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val sourceParams = btnSkipWarranty.layoutParams
        val newParams = when (sourceParams) {
            is LinearLayout.LayoutParams -> {
                LinearLayout.LayoutParams(sourceParams).apply {
                    topMargin = sourceParams.topMargin
                    bottomMargin = sourceParams.bottomMargin
                    marginStart = sourceParams.marginStart
                    marginEnd = sourceParams.marginEnd
                }
            }
            is ViewGroup.MarginLayoutParams -> {
                ViewGroup.MarginLayoutParams(sourceParams).apply {
                    topMargin = sourceParams.topMargin
                    bottomMargin = sourceParams.bottomMargin
                    marginStart = sourceParams.marginStart
                    marginEnd = sourceParams.marginEnd
                }
            }
            else -> {
                ViewGroup.LayoutParams(sourceParams)
            }
        }

        btnNoWarrantyExternal.layoutParams = newParams

        val insertIndex = parent.indexOfChild(btnSkipWarranty) + 1
        parent.addView(btnNoWarrantyExternal, insertIndex)
    }

    private fun getCustomerQuery() = run {
        val savedPhone = AppPrefs.getCustomerPhone(this).orEmpty().trim()
        val savedLine = AppPrefs.getLineNumber(this).orEmpty().trim()

        when {
            savedPhone.isNotBlank() -> db.collection("customers")
                .document(savedPhone)

            savedLine.isNotBlank() -> null
            else -> null
        }
    }

    private fun activateWarrantyAndContinue() {
        if (isContinuing) return

        val savedPhone = AppPrefs.getCustomerPhone(this).orEmpty().trim()
        val savedLine = AppPrefs.getLineNumber(this).orEmpty().trim()

        if (savedPhone.isBlank() && savedLine.isBlank()) {
            Toast.makeText(this, "לא נמצא לקוח להפעלת אחריות", Toast.LENGTH_SHORT).show()
            continueToBalance()
            return
        }

        btnActivateWarranty.isEnabled = false
        btnSkipWarranty.isEnabled = false
        btnNoWarrantyExternal.isEnabled = false
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

                    val noWarrantyExternal = doc.getBoolean("noWarrantyExternal") == true
                    if (noWarrantyExternal) {
                        restoreButtons()
                        Toast.makeText(this, "לקוח זה מסומן ללא אחריות", Toast.LENGTH_SHORT).show()
                        continueToBalance()
                        return@addOnSuccessListener
                    }

                    val existingWarrantyStart = doc.getLong("warrantyStart")
                    val existingWarrantyEnd = doc.getString("warrantyEnd").orEmpty()

                    if (existingWarrantyStart != null && existingWarrantyStart > 0L && existingWarrantyEnd.isNotBlank()) {
                        showSuccessState("האחריות כבר פעילה על מכשיר זה")
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
                        "lastUpdate" to System.currentTimeMillis(),
                        "noWarrantyExternal" to false
                    )

                    doc.reference.set(data, SetOptions.merge())
                        .addOnSuccessListener {
                            showSuccessState("האחריות הופעלה בהצלחה")
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
                val noWarrantyExternal = doc.getBoolean("noWarrantyExternal") == true
                if (noWarrantyExternal) {
                    restoreButtons()
                    Toast.makeText(this, "לקוח זה מסומן ללא אחריות", Toast.LENGTH_SHORT).show()
                    continueToBalance()
                    return@addOnSuccessListener
                }

                val existingWarrantyStart = doc.getLong("warrantyStart")
                val existingWarrantyEnd = doc.getString("warrantyEnd").orEmpty()

                if (existingWarrantyStart != null && existingWarrantyStart > 0L && existingWarrantyEnd.isNotBlank()) {
                    showSuccessState("האחריות כבר פעילה על מכשיר זה")
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
                    "lastUpdate" to System.currentTimeMillis(),
                    "noWarrantyExternal" to false
                )

                doc.reference.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        showSuccessState("האחריות הופעלה בהצלחה")
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

    private fun markNoWarrantyAndContinue() {
        if (isContinuing) return

        val savedPhone = AppPrefs.getCustomerPhone(this).orEmpty().trim()
        val savedLine = AppPrefs.getLineNumber(this).orEmpty().trim()

        if (savedPhone.isBlank() && savedLine.isBlank()) {
            Toast.makeText(this, "לא נמצא לקוח לעדכון", Toast.LENGTH_SHORT).show()
            continueToBalance()
            return
        }

        btnActivateWarranty.isEnabled = false
        btnSkipWarranty.isEnabled = false
        btnNoWarrantyExternal.isEnabled = false
        btnNoWarrantyExternal.text = "שומר נתון..."

        if (savedPhone.isNotBlank()) {
            db.collection("customers")
                .document(savedPhone)
                .set(
                    hashMapOf(
                        "noWarrantyExternal" to true,
                        "lastUpdate" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .addOnSuccessListener {
                    showSuccessState("הלקוח סומן ללא אחריות")
                }
                .addOnFailureListener {
                    restoreButtons()
                    Toast.makeText(this, "שגיאה בשמירת סטטוס אחריות", Toast.LENGTH_SHORT).show()
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
                doc.reference.set(
                    hashMapOf(
                        "noWarrantyExternal" to true,
                        "lastUpdate" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                    .addOnSuccessListener {
                        showSuccessState("הלקוח סומן ללא אחריות")
                    }
                    .addOnFailureListener {
                        restoreButtons()
                        Toast.makeText(this, "שגיאה בשמירת סטטוס אחריות", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                restoreButtons()
                Toast.makeText(this, "שגיאה באיתור לקוח", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSuccessState(message: String) {
        if (isContinuing) return

        btnActivateWarranty.visibility = View.GONE
        btnSkipWarranty.visibility = View.GONE
        btnNoWarrantyExternal.visibility = View.GONE

        successContainer.visibility = View.VISIBLE
        tvSuccessIcon.text = "✅"
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
        btnActivateWarranty.isEnabled = true
        btnSkipWarranty.isEnabled = true
        btnNoWarrantyExternal.isEnabled = true

        btnActivateWarranty.text = "הפעלת אחריות על מכשיר זה🛡️"
        btnNoWarrantyExternal.text = "אין אחריות (נרכש במקום אחר)"
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
