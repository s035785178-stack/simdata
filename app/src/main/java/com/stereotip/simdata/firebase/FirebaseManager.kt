package com.stereotip.simdata.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.Formatter

object FirebaseManager {

    private val db = FirebaseFirestore.getInstance()

    fun updateCustomer(context: Context) {
        try {
            val phone = AppPrefs.getCustomerPhone(context)

            if (phone.isBlank()) {
                Log.e("FIREBASE", "אין טלפון לקוח - לא שומר")
                return
            }

            val data = hashMapOf(
                "customerName" to AppPrefs.getCustomerName(context),
                "customerPhone" to phone,
                "carModel" to AppPrefs.getCarModel(context),
                "carNumber" to AppPrefs.getCarNumber(context),
                "dataPackage" to AppPrefs.getDataPackage(context),

                "lineNumber" to AppPrefs.getLineNumber(context),
                "balanceMb" to AppPrefs.getBalanceMb(context),
                "validUntil" to AppPrefs.getValid(context),

                "lastUpdate" to AppPrefs.getUpdated(context),
                "lastUpdateFormatted" to Formatter.formatDateTime(AppPrefs.getUpdated(context))
            )

            db.collection("customers")
                .document(phone) // 👈 מזהה ייחודי
                .set(data)
                .addOnSuccessListener {
                    Log.d("FIREBASE", "עודכן בהצלחה")
                }
                .addOnFailureListener {
                    Log.e("FIREBASE", "שגיאה", it)
                }

        } catch (e: Exception) {
            Log.e("FIREBASE", "קריסה", e)
        }
    }
}
