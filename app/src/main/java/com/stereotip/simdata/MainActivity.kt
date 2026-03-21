package com.stereotip.simdata

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 1001

    private val permissions = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            onAllPermissionsGranted()
        }
    }

    private fun onAllPermissionsGranted() {
        Toast.makeText(this, "כל ההרשאות אושרו ✔️", Toast.LENGTH_SHORT).show()
        // כאן ממשיכים רגיל (בדיקת יתרה וכו)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                onAllPermissionsGranted()
            } else {
                Toast.makeText(
                    this,
                    "האפליקציה צריכה הרשאות כדי לעבוד",
                    Toast.LENGTH_LONG
                ).show()

                // מבקש שוב (כמו שביקשת — עדיף יותר מאשר פחות)
                checkAndRequestPermissions()
            }
        }
    }
}
