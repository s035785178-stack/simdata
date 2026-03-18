package com.stereotip.simdata

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 בדיקת Firebase אוטומטית
        FirebaseTest.sendTest()

        Toast.makeText(this, "Firebase Test Sent", Toast.LENGTH_SHORT).show()
    }
}
