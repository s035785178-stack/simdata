package com.stereotip.simdata

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class BalanceActivity : AppCompatActivity() {

    private lateinit var tvProgress: TextView
    private lateinit var btnCheck: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_balance)

        tvProgress = findViewById(R.id.tvProgress)
        btnCheck = findViewById(R.id.btnCheckBalance)

        btnCheck.setOnClickListener {
            tvProgress.text = "בדיקה בלחיצה עובדת ✔"
            Toast.makeText(this, "לחצת על בדיקה", Toast.LENGTH_SHORT).show()
        }
    }
}
