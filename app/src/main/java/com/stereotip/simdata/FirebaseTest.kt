package com.stereotip.simdata

import android.util.Log
import com.google.firebase.database.FirebaseDatabase

object FirebaseTest {

    fun sendTest() {
        try {
            val db = FirebaseDatabase.getInstance().reference

            val payload = mapOf(
                "message" to "hello from simdata",
                "timestamp" to System.currentTimeMillis(),
                "source" to "v1.04-stable"
            )

            db.child("test")
                .push()
                .setValue(payload)
                .addOnSuccessListener {
                    Log.d("FirebaseTest", "Test data sent successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseTest", "Failed to send test data", e)
                }
        } catch (e: Exception) {
            Log.e("FirebaseTest", "Unexpected error", e)
        }
    }
}
