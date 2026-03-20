package com.stereotip.simdata.util

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseCustomerSync {

    fun sync(context: Context) {
        val db = FirebaseFirestore.getInstance()

        val phone = AppPrefs.getCustomerPhone(context).ifBlank { return }
        val docId = phone.replace(" ", "").replace("-", "")

        val now = System.currentTimeMillis()
        val lineNumber = AppPrefs.getLineNumber(context).orEmpty()
        val balanceMb = AppPrefs.getBalanceMb(context)
        val validUntil = AppPrefs.getValid(context).orEmpty()
        val customerName = AppPrefs.getCustomerName(context)
        val customerPhone = phone
        val carModel = AppPrefs.getCarModel(context)
        val carNumber = AppPrefs.getCarNumber(context)
        val dataPackage = AppPrefs.getDataPackage(context)

        val data = hashMapOf(
            "customerName" to customerName,
            "customerPhone" to customerPhone,
            "carModel" to carModel,
            "carNumber" to carNumber,
            "dataPackage" to dataPackage,

            // שדות קיימים
            "lineNumber" to lineNumber,
            "balanceMb" to balanceMb,
            "validUntil" to validUntil,
            "lastUpdate" to now,

            // שדות חדשים לאפליקציית הצי
            "currentBalanceMb" to balanceMb,
            "lastBalanceCheck" to now
        )

        val docRef = db.collection("customers").document(docId)

        docRef.set(data)
            .addOnSuccessListener {
                // היסטוריית בדיקות
                val history = hashMapOf(
                    "checkedAt" to now,
                    "balanceMb" to balanceMb,
                    "lineNumber" to lineNumber,
                    "validUntil" to validUntil
                )

                docRef.collection("balance_checks").add(history)
            }
    }
}
