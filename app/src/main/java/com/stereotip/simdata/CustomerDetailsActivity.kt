package com.stereotip.simdata

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.AppPrefs

class CustomerDetailsActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnBack: Button

    private val packages = listOf(
        "100GB לשנתיים",
        "36GB ל-60 חודשים",
        "4GB לחודשיים"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_details)

        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etCarModel = findViewById(R.id.etCarModel)
        etCarNumber = findViewById(R.id.etCarNumber)
        spinnerPackage = findViewById(R.id.spinnerPackage)
        btnSave = findViewById(R.id.btnSaveCustomer)
        btnBack = findViewById(R.id.btnBackCustomer)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, packages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPackage.adapter = adapter

        loadExistingData()

        btnSave.setOnClickListener {
            saveCustomerData()
            Toast.makeText(this, "פרטי הלקוח נשמרו", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadExistingData() {
        etName.setText(AppPrefs.getCustomerName(this))
        etPhone.setText(AppPrefs.getCustomerPhone(this))
        etCarModel.setText(AppPrefs.getCarModel(this))
        etCarNumber.setText(AppPrefs.getCarNumber(this))

        val savedPackage = AppPrefs.getDataPackage(this)
        val packageIndex = packages.indexOf(savedPackage)
        if (packageIndex >= 0) {
            spinnerPackage.setSelection(packageIndex)
        }
    }

    private fun saveCustomerData() {
        AppPrefs.setCustomerName(this, etName.text.toString().trim())
        AppPrefs.setCustomerPhone(this, etPhone.text.toString().trim())
        AppPrefs.setCarModel(this, etCarModel.text.toString().trim())
        AppPrefs.setCarNumber(this, etCarNumber.text.toString().trim())
        AppPrefs.setDataPackage(this, spinnerPackage.selectedItem.toString())
    }
}
