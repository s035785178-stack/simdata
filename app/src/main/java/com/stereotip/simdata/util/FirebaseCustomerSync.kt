package com.stereotip.simdata.util

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseCustomerSync {

    fun sync(context: Context) {
        val db = FirebaseFirestore.getInstance()

        val lineNumber = PhoneUtils.normalizeToLocal(TelephonyUtils.getLineNumber(context))
            .let {
                if (it == "לא זוהה" || it == "לא זוהה מספר" || it == "לא אושרו הרשאות") "" else it
            }

        if (lineNumber.isBlank()) return

        val now = System.currentTimeMillis()

        val balanceMb = AppPrefs.getBalanceMb(context)
        val validUntil = AppPrefs.getValid(context).orEmpty()

        val data = hashMapOf(
            "lineNumber" to lineNumber,

            // נתונים דינמיים שהלקוח באמת יודע
            "balanceMb" to balanceMb,
            "currentBalanceMb" to balanceMb,
            "validUntil" to validUntil,
            "lastUpdate" to now,
            "lastBalanceCheck" to now,

            // מידע טכני
            "deviceName" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
        )

        val docRef = db.collection("customers").document(lineNumber)

        docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
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
