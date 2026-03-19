package com.stereotip.simdata.util

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseCustomerSync {

    fun sync(context: Context) {
        val db = FirebaseFirestore.getInstance()

        val phone = AppPrefs.getCustomerPhone(context).ifBlank { return }
        val docId = phone.replace(" ", "").replace("-", "")

        val data = hashMapOf(
            "customerName" to AppPrefs.getCustomerName(context),
            "customerPhone" to phone,
            "carModel" to AppPrefs.getCarModel(context),
            "carNumber" to AppPrefs.getCarNumber(context),
            "dataPackage" to AppPrefs.getDataPackage(context),

            "lineNumber" to AppPrefs.getLineNumber(context),
            "balanceMb" to AppPrefs.getBalanceMb(context),
            "validUntil" to AppPrefs.getValid(context),
            "lastUpdate" to System.currentTimeMillis()
        )

        db.collection("customers")
            .document(docId)
            .set(data)
    }
}
