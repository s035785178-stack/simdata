package com.stereotip.simdata

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.AppPrefs

class RegistrationActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner
    private lateinit var btnRegister: Button
    private lateinit var btnHelp: Button
    private lateinit var tvLine: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_registration)
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
            return
        }

        initViews()
        setupSafeSpinner()
    }

    private fun initViews() {
        etName = findViewById(R.id.etRegistrationName)
        etPhone = findViewById(R.id.etRegistrationPhone)
        etCarModel = findViewById(R.id.etCarModel)
        etCarNumber = findViewById(R.id.etCarNumber)
        spinnerPackage = findViewById(R.id.spinnerPackage)
        btnRegister = findViewById(R.id.btnRegisterCustomer)
        btnHelp = findViewById(R.id.btnHelp)
        tvLine = findViewById(R.id.tvRegistrationLineNumber)
    }

    // 🔥 זה הפתרון לקריסה
    private fun setupSafeSpinner() {
        try {
            val packages = listOf(
                "100 ג׳יגה לשנתיים",
                "36 ג׳יגה ל-5 שנים",
                "4 ג׳יגה לחודשיים"
            )

            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                packages
            )

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPackage.adapter = adapter

        } catch (e: Exception) {
            e.printStackTrace()

            // fallback שלא יקרוס
            Toast.makeText(this, "שגיאה בטעינת חבילות", Toast.LENGTH_SHORT).show()
        }
    }
}
