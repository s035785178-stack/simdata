package com.stereotip.simdata.util

import android.content.Context
import android.os.Build
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object FirebaseCustomerSync {

    fun sync(context: Context) {
        val db = FirebaseFirestore.getInstance()

        val lineNumber = normalizeLine(TelephonyUtils.getLineNumber(context))
            .ifBlank { normalizeLine(AppPrefs.getLineNumber(context)) }
        if (lineNumber.isBlank()) return

        val now = System.currentTimeMillis()
        val packageName = AppPrefs.getDataPackage(context)
        val validMode = if (AppPrefs.isValidityModeAuto(context)) "auto" else "manual"

        val data = linkedMapOf<String, Any?>(
            "name" to AppPrefs.getCustomerName(context),
            "lineNumber" to lineNumber,
            "phone" to AppPrefs.getCustomerPhone(context),
            "carNumber" to AppPrefs.getCarNumber(context),
            "carModel" to AppPrefs.getCarModel(context),
            "package" to packageName,
            "validUntil" to AppPrefs.getValid(context),
            "validMode" to validMode,
            "balanceMb" to AppPrefs.getBalanceMb(context),
            "lastBalanceCheck" to now,
            "status" to "",
            "lastUpdate" to now,
            "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),

            // תאימות לאחור
            "customerName" to AppPrefs.getCustomerName(context),
            "customerPhone" to AppPrefs.getCustomerPhone(context),
            "dataPackage" to packageName,
            "validityMode" to validMode,
            "currentBalanceMb" to AppPrefs.getBalanceMb(context)
        )

        db.collection("customers")
            .whereEqualTo("lineNumber", lineNumber)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val docRef = result.documents.first().reference
                    updateExistingCustomer(docRef, data, lineNumber, now, context)
                } else {
                    val fallbackPhone = AppPrefs.getCustomerPhone(context).trim()
                    val docRef = if (fallbackPhone.isNotBlank()) {
                        db.collection("customers").document(fallbackPhone)
                    } else {
                        db.collection("customers").document(lineNumber)
                    }
                    updateExistingCustomer(docRef, data, lineNumber, now, context)
                }
            }
    }

    private fun updateExistingCustomer(
        docRef: DocumentReference,
        data: LinkedHashMap<String, Any?>,
        lineNumber: String,
        now: Long,
        context: Context
    ) {
        docRef.set(data, SetOptions.merge())
            .addOnSuccessListener {
                val history: HashMap<String, Any?> = hashMapOf(
                    "checkedAt" to now,
                    "balanceMb" to AppPrefs.getBalanceMb(context),
                    "lineNumber" to lineNumber,
                    "validUntil" to AppPrefs.getValid(context).orEmpty()
                )
                docRef.collection("balance_checks").add(history)
            }
    }

    private fun normalizeLine(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return when (normalized) {
            "לא זוהה", "לא זוהה מספר", "לא אושרו הרשאות" -> ""
            else -> normalized
        }
    }
}
