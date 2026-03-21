package com.stereotip.simdata

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.*
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

        // Views
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

        // Spinner data
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

        // Detect line number
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

        // Animations
        playEntranceAnimation()
        playLogoPulse()
    }

    // ======================
    // 🎬 Animations
    // ======================

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
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()
    }

    private fun playLogoPulse() {
        val pulseX = ObjectAnimator.ofFloat(logoRegistration, View.SCALE_X, 1f, 1.03f, 1f)
        val pulseY = ObjectAnimator.ofFloat(logoRegistration, View.SCALE_Y, 1f, 1.03f, 1f)

        pulseX.duration = 2000
        pulseY.duration = 2000
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

    // ======================
    // 🚀 Registration
    // ======================

    private fun attemptRegistration() {

        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val carModel = etCarModel.text.toString().trim()
        val carNumber = etCarNumber.text.toString().trim()
        val selectedPackage = spinnerPackage.selectedItem.toString()

        if (name.isEmpty() || phone.isEmpty()) {
            showError(btnRegister)
            Toast.makeText(this, "נא למלא שם וטלפון", Toast.LENGTH_SHORT).show()
            return
        }

        if (!NetworkUtils.isConnected(this)) {
            showError(btnRegister)
            Toast.makeText(this, "אין חיבור לאינטרנט", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf(
            "name" to name,
            "phone" to phone,
            "carModel" to carModel,
            "carNumber" to carNumber,
            "package" to selectedPackage,
            "lineNumber" to detectedLineNumber,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("customers")
            .document(phone)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {

                showSuccess(btnRegister)

                AppPrefs.setRegistered(this, true)

                Toast.makeText(this, "נרשמת בהצלחה", Toast.LENGTH_SHORT).show()

                finish()
            }
            .addOnFailureListener {
                showError(btnRegister)
                Toast.makeText(this, "שגיאה בשליחה", Toast.LENGTH_SHORT).show()
            }
    }

    // ======================
    // 📞 WhatsApp Help
    // ======================

    private fun openHelpWhatsapp() {
        val message = "היי, צריך עזרה עם אפליקציית SIM"
        val url = "https://wa.me/972559911336?text=" + URLEncoder.encode(message, "UTF-8")

        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    // ======================
    // 📱 Utils
    // ======================

    private fun normalizeLine(line: String?): String {
        if (line.isNullOrBlank()) return ""
        return PhoneUtils.normalize(line)
    }
}
