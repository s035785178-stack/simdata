package com.stereotip.simdata

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.AppPrefs

class CustomerDetailsActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCarModel: EditText
    private lateinit var etCarNumber: EditText
    private lateinit var spinnerPackage: Spinner
    private lateinit var btnSave: Button

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
        btnSave = findViewById(R.id.btnSave)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, packages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPackage.adapter = adapter

        loadData()

        btnSave.setOnClickListener {
            saveData()
            Toast.makeText(this, "נשמר בהצלחה", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadData() {
        etName.setText(AppPrefs.get(this, "customer_name"))
        etPhone.setText(AppPrefs.get(this, "customer_phone"))
        etCarModel.setText(AppPrefs.get(this, "car_model"))
        etCarNumber.setText(AppPrefs.get(this, "car_number"))

        val savedPackage = AppPrefs.get(this, "data_package")
        val index = packages.indexOf(savedPackage)
        if (index >= 0) spinnerPackage.setSelection(index)
    }

    private fun saveData() {
        AppPrefs.set(this, "customer_name", etName.text.toString())
        AppPrefs.set(this, "customer_phone", etPhone.text.toString())
        AppPrefs.set(this, "car_model", etCarModel.text.toString())
        AppPrefs.set(this, "car_number", etCarNumber.text.toString())
        AppPrefs.set(this, "data_package", spinnerPackage.selectedItem.toString())
    }
}
