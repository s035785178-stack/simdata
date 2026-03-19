package com.stereotip.simdata.util

import android.content.Context
import android.os.Build
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

object FirebaseCustomerSync {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun saveCustomer(context: Context, onComplete: (Boolean, String?) -> Unit) {
        val lineRaw = AppPrefs.getLineNumber(context)
        val lineNumber = PhoneUtils.normalizeToLocal(lineRaw)
        val customerPhone = PhoneUtils.normalizeToLocal(AppPrefs.getCustomerPhone(context))

        val documentId = when {
            lineNumber.isNotBlank() && lineNumber != "לא זוהה" -> lineNumber
            customerPhone.isNotBlank() && customerPhone != "לא זוהה" -> customerPhone
            else -> "device_${System.currentTimeMillis()}"
        }

        val balanceMb = AppPrefs.getBalanceMb(context)
        val balanceDisplay = balanceMb?.let { Formatter.mbToDisplay(it) } ?: ""

        val data = hashMapOf(
            "lineNumber" to (if (lineNumber == "לא זוהה") "" else lineNumber),
            "customerName" to AppPrefs.getCustomerName(context),
            "customerPhone" to (if (customerPhone == "לא זוהה") "" else customerPhone),
            "carModel" to AppPrefs.getCarModel(context),
            "carNumber" to AppPrefs.getCarNumber(context),
            "dataPackage" to AppPrefs.getDataPackage(context),
            "installTimestamp" to AppPrefs.getInstallTimestamp(context),
            "installDateText" to Formatter.formatDateTime(AppPrefs.getInstallTimestamp(context)),
            "balanceMb" to (balanceMb ?: -1),
            "balanceDisplay" to balanceDisplay,
            "validUntil" to (AppPrefs.getValid(context) ?: ""),
            "lastBalanceUpdate" to AppPrefs.getUpdated(context),
            "lastBalanceUpdateText" to Formatter.formatDateTime(AppPrefs.getUpdated(context)),
            "deviceManufacturer" to Build.MANUFACTURER,
            "deviceModel" to Build.MODEL,
            "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            "appVersion" to "1.05",
            "updatedAt" to System.currentTimeMillis(),
            "updatedAtText" to Formatter.formatDateTime(System.currentTimeMillis()),
            "locale" to Locale.getDefault().toString()
        )

        db.collection("customers")
            .document(documentId)
            .set(data)
            .addOnSuccessListener {
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                onComplete(false, e.message)
            }
    }
}
