package com.stereotip.simdata

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasDialer()) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, RequiredAppsActivity::class.java))
        }

        finish()
    }

    private fun hasDialer(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:*019"))
            val resolve = packageManager.resolveActivity(intent, 0)
            resolve != null
        } catch (e: Exception) {
            true
        }
    }
}