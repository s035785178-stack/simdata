package com.stereotip.simdata.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

object RequiredApps {

    private const val TEST_NUMBER = "*019"

    fun isRequiredDialerAvailable(context: Context): Boolean {
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$TEST_NUMBER"))

            val activities = context.packageManager.queryIntentActivities(
                dialIntent,
                0 // 🔥 לא MATCH_DEFAULT_ONLY
            )

            if (activities.isEmpty()) return false

            activities.any { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName.orEmpty().lowercase()
                val activityName = resolveInfo.activityInfo?.name.orEmpty().lowercase()
                val label = resolveInfo.loadLabel(context.packageManager)?.toString().orEmpty().lowercase()

                !isBluetoothDialer(packageName, activityName, label)
            }

        } catch (e: Exception) {
            true // 🔥 לא מפיל את האפליקציה אף פעם
        }
    }

    fun canCallDirectly(context: Context): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun isBluetoothDialer(
        packageName: String,
        activityName: String,
        label: String
    ): Boolean {
        val combined = "$packageName $activityName $label"

        return combined.contains("bluetooth") ||
                combined.contains("bt") ||
                combined.contains("btdial") ||
                combined.contains("bt dial") ||
                combined.contains("btphone") ||
                combined.contains("bt phone") ||
                combined.contains("handsfree") ||
                combined.contains("hfp")
    }
}