package com.stereotip.simdata

import android.content.Context
import com.stereotip.simdata.util.AppPrefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PackageExpiryManager {

    private val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    data class ExpiryInfo(
        val startMillis: Long,
        val endMillis: Long,
        val display: String
    )

    fun buildEstimatedExpiry(packageName: String, startMillis: Long = System.currentTimeMillis()): ExpiryInfo? {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startMillis

        when (packageName.trim()) {
            "100GB לשנתיים" -> cal.add(Calendar.YEAR, 2)
            "36GB ל-60 חודשים" -> cal.add(Calendar.MONTH, 60)
            "4GB לחודשיים" -> cal.add(Calendar.MONTH, 2)
            else -> return null
        }

        val endMillis = cal.timeInMillis
        val display = displayFormat.format(cal.time)
        return ExpiryInfo(
            startMillis = startMillis,
            endMillis = endMillis,
            display = display
        )
    }

    fun saveEstimatedExpiryLocally(
        context: Context,
        packageName: String,
        startMillis: Long = System.currentTimeMillis()
    ): ExpiryInfo? {
        val info = buildEstimatedExpiry(packageName, startMillis) ?: return null
        AppPrefs.setEstimatedPackageExpiry(
            context = context,
            startMillis = info.startMillis,
            endMillis = info.endMillis,
            display = info.display
        )
        return info
    }

    fun saveActualExpiryLocally(
        context: Context,
        endMillis: Long,
        display: String
    ) {
        AppPrefs.setActualPackageExpiry(
            context = context,
            endMillis = endMillis,
            display = display
        )
    }

    fun buildRegistrationExpiryMap(
        packageName: String,
        startMillis: Long = System.currentTimeMillis()
    ): HashMap<String, Any?> {
        val info = buildEstimatedExpiry(packageName, startMillis)
        return hashMapOf(
            "packageStartMillis" to info?.startMillis,
            "packageEstimatedEndMillis" to info?.endMillis,
            "packageEstimatedEndDisplay" to info?.display,
            "packageActualEndMillis" to null,
            "packageActualEndDisplay" to null,
            "packageFinalEndMillis" to info?.endMillis,
            "packageFinalEndDisplay" to info?.display
        )
    }

    fun daysUntilFinalExpiry(context: Context): Int? {
        val endMillis = AppPrefs.getFinalPackageEndMillis(context)
        if (endMillis <= 0L) return null

        val now = System.currentTimeMillis()
        val diff = endMillis - now
        val days = (diff / (1000L * 60L * 60L * 24L)).toInt()
        return days
    }
}