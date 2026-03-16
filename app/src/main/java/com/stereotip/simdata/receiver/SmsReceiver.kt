package com.stereotip.simdata.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.SmsParser

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")

        val parts = mutableListOf<SmsMessage>()
        pdus.forEach { pdu ->
            val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SmsMessage.createFromPdu(pdu as ByteArray, format)
            } else {
                @Suppress("DEPRECATION")
                SmsMessage.createFromPdu(pdu as ByteArray)
            }
            parts.add(msg)
        }

        val body = parts.joinToString(separator = "") { it.messageBody ?: "" }
        val sender = parts.firstOrNull()?.displayOriginatingAddress.orEmpty()

        if (!isLikelyCarrierSms(sender, body)) return

        val parsed = SmsParser.parse(body) ?: return
        AppPrefs.saveBalance(context, parsed)
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_BALANCE_UPDATED))
    }

    private fun isLikelyCarrierSms(sender: String, body: String): Boolean {
        if (sender.contains("019", true)) return true
        return body.contains("Your Balance", true) ||
            body.contains("Data Internet", true) ||
            body.contains("Your number is", true) ||
            body.contains("Valid", true)
    }

    companion object {
        const val ACTION_BALANCE_UPDATED = "com.stereotip.simdata.ACTION_BALANCE_UPDATED"
    }
}
