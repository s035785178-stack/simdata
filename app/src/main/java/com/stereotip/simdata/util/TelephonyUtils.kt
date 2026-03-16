package com.stereotip.simdata.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.stereotip.simdata.data.NetworkCheckResult

object TelephonyUtils {

    fun getLineNumber(context: Context): String {
        val cached = AppPrefs.getLineNumber(context)
        if (!cached.isNullOrBlank()) return cached

        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val allowed =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_NUMBERS
                ) == PackageManager.PERMISSION_GRANTED

            if (!allowed) return "לא אושרו הרשאות"

            val line = tm.line1Number
            if (line.isNullOrBlank()) "לא זוהה מספר" else normalizePhoneNumber(line)
        } catch (_: Exception) {
            "לא זוהה מספר"
        }
    }

    fun checkNetwork(context: Context): NetworkCheckResult {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val connectivity =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val simStatus = when (tm.simState) {
            TelephonyManager.SIM_STATE_READY -> "מזוהה"
            TelephonyManager.SIM_STATE_ABSENT -> "לא הוכנס SIM"
            else -> "לא מוכן"
        }

        val networkType = when (tm.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "לא ידוע"
            else -> "${tm.dataNetworkType}"
        }

        val roaming = try {
            val roamingValue = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DATA_ROAMING,
                0
            )
            if (roamingValue == 1) "פעיל" else "כבוי"
        } catch (_: Exception) {
            if (tm.isNetworkRoaming) "פעיל" else "כבוי"
        }

        val active = connectivity.activeNetwork
        val caps = connectivity.getNetworkCapabilities(active)

        val internetStatus =
            if (
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true ||
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            ) {
                "תקין"
            } else {
                "לא תקין"
            }

        val mobileDataStatus =
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                "פעילים"
            } else {
                "לא פעילים"
            }

        val apnStatus = queryApn(context)

        return NetworkCheckResult(
            simStatus = simStatus,
            networkType = networkType,
            internetStatus = internetStatus,
            roamingStatus = roaming,
            apnStatus = apnStatus,
            mobileDataStatus = mobileDataStatus,
            lineNumber = getLineNumber(context)
        )
    }

    private fun queryApn(context: Context): String {
        return try {
            val uri = android.net.Uri.parse("content://telephony/carriers/preferapn")
            context.contentResolver.query(
                uri,
                arrayOf("name", "apn"),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0) ?: ""
                    val apn = c.getString(1) ?: ""

                    when {
                        apn.isNotBlank() -> apn
                        name.isNotBlank() -> name
                        else -> "לא זוהה"
                    }
                } else {
                    "לא זוהה"
                }
            } ?: "לא נגיש"
        } catch (_: SecurityException) {
            "לא זמין במכשיר זה"
        } catch (_: Exception) {
            "לא זמין"
        }
    }
    private fun normalizePhoneNumber(raw: String): String {
        val cleaned = raw.replace(" ", "").replace("-", "")
        return when {
            cleaned.startsWith("+972") -> cleaned
            cleaned.startsWith("972") -> "+$cleaned"
            cleaned.startsWith("0") -> "+972" + cleaned.removePrefix("0")
            else -> cleaned
        }
    }

}
