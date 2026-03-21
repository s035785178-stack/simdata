package com.stereotip.simdata

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.NetworkUtils
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.net.URLEncoder

class RegistrationActivity : AppCompatActivity() {

    private lateinit var tvLineNumber: TextView
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner
    private lateinit var btnRegister: Button
    private lateinit var btnHelp: Button

    private lateinit var logoRegistration: View
    private lateinit var headerCard: LinearLayout
    private lateinit var formCard: LinearLayout
    private lateinit var buttonRow: LinearLayout

    private val db = FirebaseFirestore.getInstance()
    private var detectedLineNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        logoRegistration = findViewById(R.id.logoRegistration)
        headerCard = findViewById(R.id.headerRegistrationCard)
        formCard = findViewById(R.id.formRegistrationCard)
        buttonRow = findViewById(R.id.buttonRowRegistration)

        tvLineNumber = findViewById(R.id.tvRegistrationLineNumber)
        etName = findViewById(R.id.etRegistrationName)
        etPhone = findViewById(R.id.etRegistrationPhone)
        etCarModel = findViewById(R.id.etCarModel)
        etCarNumber = findViewById(R.id.etCarNumber)
        spinnerPackage = findViewById(R.id.spinnerPackage)
        btnRegister = findViewById(R.id.btnRegisterCustomer)
        btnHelp = findViewById(R.id.btnHelp)

        val packages = listOf(
            "לא ידוע / אין",
            "100 ג׳יגה או שנתיים",
            "36 ג׳יגה או 60 חודשים",
            "4 ג׳יגה או חודשיים"
        )

        val adapter = ArrayAdapter(this, R.layout.spinner_item_white, packages)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
        spinnerPackage.adapter = adapter
        spinnerPackage.setSelection(1)

        detectedLineNumber = normalizeLine(TelephonyUtils.getLineNumber(this))
        tvLineNumber.text = if (detectedLineNumber.isNotBlank()) {
            "מספר קו במכשיר: $detectedLineNumber"
        } else {
            "מספר קו במכשיר: לא זוהה"
        }

        btnRegister.setOnClickListener {
            attemptRegistration()
        }

        btnHelp.setOnClickListener {
            openHelpWhatsapp()
        }

        playEntranceAnimation()
        playLogoPulse()
    }

    private fun playEntranceAnimation() {
        animateEntrance(logoRegistration, 0L, 0.92f)
        animateEntrance(headerCard, 100L, 0.96f)
        animateEntrance(formCard, 200L, 0.97f)
        animateEntrance(buttonRow, 300L, 0.98f)
    }

    private fun animateEntrance(view: View, delay: Long, startScale: Float) {
        view.alpha = 0f
        view.translationY = 40f
        view.scaleX = startScale
        view.scaleY = startScale

        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delay)
            .setDuration(500L)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()
    }

    private fun playLogoPulse() {
        val pulseX = ObjectAnimator.ofFloat(logoRegistration, View.SCALE_X, 1f, 1.03f, 1f)
        val pulseY = ObjectAnimator.ofFloat(logoRegistration, View.SCALE_Y, 1f, 1.03f, 1f)

        pulseX.duration = 2000L
        pulseY.duration = 2000L
        pulseX.repeatCount = ObjectAnimator.INFINITE
        pulseY.repeatCount = ObjectAnimator.INFINITE
        pulseX.interpolator = AccelerateDecelerateInterpolator()
        pulseY.interpolator = AccelerateDecelerateInterpolator()
        pulseX.start()
        pulseY.start()
    }

    private fun showSuccess(view: View) {
        val anim = AnimationUtils.loadAnimation(this, R.anim.success_scale)
        view.startAnimation(anim)
    }

    private fun showError(view: View) {
        val anim = AnimationUtils.loadAnimation(this, R.anim.error_shake)
        view.startAnimation(anim)
    }

    private fun attemptRegistration() {
        val name = etName.text.toString().trim()
        val phone = normalizePhone(etPhone.text.toString())

        if (name.isBlank()) {
            etName.error = "יש להזין שם"
            etName.requestFocus()
            showError(etName)
            return
        }

        if (phone.length != 10 || !phone.startsWith("05")) {
            etPhone.error = "יש להזין טלפון תקין"
            etPhone.requestFocus()
            showError(etPhone)
            return
        }

        if (!NetworkUtils.isOnline(this)) {
            showError(btnRegister)

            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("success", false)
            intent.putExtra("return_to_registration", true)
            intent.putExtra("seconds", 10)
            startActivity(intent)
            finish()
            return
        }

        registerCustomer()
    }

    private fun registerCustomer() {
        val name = etName.text.toString().trim()
        val phone = normalizePhone(etPhone.text.toString())
        val carModel = etCarModel.text.toString().trim()
        val carNumber = etCarNumber.text.toString().trim()
        val dataPackage = spinnerPackage.selectedItem?.toString().orEmpty()
        val lineNumber = detectedLineNumber
        val now = System.currentTimeMillis()

        btnRegister.isEnabled = false

        val data = hashMapOf(
            "customerName" to name,
            "customerPhone" to phone,
            "lineNumber" to lineNumber,
            "carModel" to carModel,
            "carNumber" to carNumber,
            "dataPackage" to dataPackage,
            "createdAt" to now,
            "lastUpdate" to now
        )

        db.collection("customers")
            .document(phone)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                showSuccess(btnRegister)

                AppPrefs.setCustomerName(this, name)
                AppPrefs.setCustomerPhone(this, phone)
                AppPrefs.setCarModel(this, carModel)
                AppPrefs.setCarNumber(this, carNumber)
                AppPrefs.setDataPackage(this, dataPackage)

                if (lineNumber.isNotBlank()) {
                    AppPrefs.setLineNumber(this, lineNumber)
                }

                val intent = Intent(this, ResultActivity::class.java)
                intent.putExtra("success", true)
                intent.putExtra("return_to_registration", false)
                intent.putExtra("seconds", 5)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                btnRegister.isEnabled = true
                showError(btnRegister)

                val intent = Intent(this, ResultActivity::class.java)
                intent.putExtra("success", false)
                intent.putExtra("return_to_registration", true)
                intent.putExtra("seconds", 10)
                startActivity(intent)
                finish()
            }
    }

    private fun openHelpWhatsapp() {
        val message = "היי אני צריך עזרה בהרשמה למערכת יתרת חבילת גלישה"
        val url = "https://wa.me/972559911336?text=${URLEncoder.encode(message, "UTF-8")}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun normalizePhone(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return if (normalized == "לא זוהה") "" else normalized
    }

    private fun normalizeLine(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return when (normalized) {
            "לא זוהה", "לא זוהה מספר", "לא אושרו הרשאות" -> ""
            else -> normalized
        }
    }
}
