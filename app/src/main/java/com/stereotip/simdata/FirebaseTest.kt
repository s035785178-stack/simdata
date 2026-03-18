package com.stereotip.simdata

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseTest {

    fun sendTest() {
        try {
            val db = FirebaseFirestore.getInstance()

            val data = hashMapOf(
                "test" to "ok",
                "time" to System.currentTimeMillis()
            )

            db.collection("test")
                .add(data)
                .addOnSuccessListener {
                    Log.d("FirebaseTest", "SUCCESS")
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseTest", "ERROR", e)
                }

        } catch (e: Exception) {
            Log.e("FirebaseTest", "CRASH", e)
        }
    }
}
