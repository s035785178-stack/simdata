package com.stereotip.simdata

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    private val OPTIONAL_PERMISSIONS = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->

            val requiredOk = REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }

            if (!requiredOk) {
                Toast.makeText(
                    this,
                    "יש לאשר הרשאות בסיס כדי שהאפליקציה תעבוד",
                    Toast.LENGTH_LONG
                ).show()

                requestRequiredPermissions()
                return@registerForActivityResult
            }

            // ✔️ ממשיכים רגיל גם אם אופציונליות לא אושרו
            onPermissionsReady()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            requestOptionalPermissions()
        }
    }

    private fun requestOptionalPermissions() {
        val missingOptional = OPTIONAL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingOptional.isNotEmpty()) {
            permissionLauncher.launch(missingOptional.toTypedArray())
        } else {
            onPermissionsReady()
        }
    }

    private fun onPermissionsReady() {
        Toast.makeText(this, "האפליקציה מוכנה ✔️", Toast.LENGTH_SHORT).show()

        // כאן ממשיך כל הזרימה הרגילה שלך
        // בדיקת רישום / SMS / יתרה וכו
    }
}
