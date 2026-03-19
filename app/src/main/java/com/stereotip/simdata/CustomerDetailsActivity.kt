package com.stereotip.simdata

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class CustomerDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_details)

        val spinner = findViewById<Spinner>(R.id.spinnerPackage)

        val packages = listOf(
            "לא ידוע / אין",
            "חבילה 10GB",
            "חבילה 50GB",
            "חבילה ללא הגבלה"
        )

        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            packages
        ) {

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(android.graphics.Color.WHITE)
                view.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(android.graphics.Color.WHITE)
                view.setBackgroundColor(android.graphics.Color.BLACK)
                view.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                return view
            }
        }

        spinner.adapter = adapter
        spinner.setSelection(0)
    }
}
