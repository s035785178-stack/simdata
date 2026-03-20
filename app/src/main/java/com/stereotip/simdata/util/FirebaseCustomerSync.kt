package com.stereotip.simdata.util

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseCustomerSync {

    fun sync(context: Context) {
        val db = FirebaseFirestore.getInstance()

        // טלפון לקוח (מההרשמה)
        val rawCustomerPhone = AppPrefs.getCustomerPhone(context)
        val customerPhone = normalizeIsraeli(rawCustomerPhone)

        if (customerPhone.isBlank()) return

        val docId = customerPhone

        // מספר קו מהמכשיר
        val rawLine = AppPrefs.getLineNumber(context)
        val lineNumber = normalizeIsraeli(rawLine)

        val now = System.currentTimeMillis()

        val data = hashMapOf(
            "customerName" to AppPrefs.getCustomerName(context),
            "customerPhone" to customerPhone,   // ✅ טלפון לקוח
            "lineNumber" to lineNumber,         // ✅ מספר קו אמיתי

            "carModel" to AppPrefs.getCarModel(context),
            "carNumber" to AppPrefs.getCarNumber(context),
            "dataPackage" to AppPrefs.getDataPackage(context),

            "currentBalanceMb" to AppPrefs.getBalanceMb(context),
            "balanceMb" to AppPrefs.getBalanceMb(context),

            "validUntil" to AppPrefs.getValid(context),
            "lastBalanceCheck" to now,
            "lastUpdate" to now
        )

        db.collection("customers")
            .document(docId)
            .set(data)
    }

    private fun normalizeIsraeli(value: String?): String {
        if (value.isNullOrBlank()) return ""

        var v = value.trim()
            .replace(" ", "")
            .replace("-", "")

        if (v.startsWith("+972")) {
            v = "0" + v.removePrefix("+972")
        } else if (v.startsWith("972")) {
            v = "0" + v.removePrefix("972")
        } else if (v.startsWith("5")) {
            v = "0$v"
        }

        return v
    }
}
