package com.stereotip.simdata

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ מחזירים את המסך הרגיל
        setContentView(R.layout.activity_main)

        // ❌ מבטלים את בדיקת Firebase האוטומטית (שגרמה למסך שחור)
        // FirebaseTest.sendTest()
    }
}
