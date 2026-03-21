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
        if (lineNumber.isBlank()) return

        val now = System.currentTimeMillis()

        val data: HashMap<String, Any?> = hashMapOf(
            "lineNumber" to lineNumber,
            "balanceMb" to AppPrefs.getBalanceMb(context),
            "currentBalanceMb" to AppPrefs.getBalanceMb(context),
            "validUntil" to AppPrefs.getValid(context),
            "lastUpdate" to now,
            "lastBalanceCheck" to now,
            "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}".trim()
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
                    val docRef = db.collection("customers").document(lineNumber)
                    updateExistingCustomer(docRef, data, lineNumber, now, context)
                }
            }
    }

    private fun updateExistingCustomer(
        docRef: DocumentReference,
        data: HashMap<String, Any?>,
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
