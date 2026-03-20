package com.stereotip.simdata.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.FirebaseCustomerSync
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.SmsParser

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (sms in messages) {
            val body = sms.messageBody ?: continue
            val sender = sms.originatingAddress ?: ""

            Log.d("SMS_DEBUG", "SMS received from: $sender | $body")

            // 🔍 מנסים לחלץ יתרה
            val balance = SmsParser.extractBalanceMb(body)

            if (balance != null) {
                Log.d("SMS_DEBUG", "Balance parsed: $balance MB")

                // 💾 שמירה מקומית
                AppPrefs.setBalanceMb(context, balance)

                val now = System.currentTimeMillis()
                AppPrefs.setLastBalanceCheck(context, now)

                // 📱 עדכון מספר קו (אם יש)
                val line = PhoneUtils.normalizeToLocal(sender)
                if (line.isNotBlank()) {
                    AppPrefs.setLineNumber(context, line)
                }

                // ☁️ שליחה ל-Firebase
                FirebaseCustomerSync.saveCustomer(context) { success, error ->
                    if (success) {
                        Log.d("FIREBASE", "Customer updated successfully")
                    } else {
                        Log.e("FIREBASE", "Error: $error")
                    }
                }
            }
        }
    }
}
