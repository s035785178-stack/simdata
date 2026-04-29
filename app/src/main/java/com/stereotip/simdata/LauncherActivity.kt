package com.stereotip.simdata

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // עוקף הכל - נכנס ישר לאפליקציה
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}