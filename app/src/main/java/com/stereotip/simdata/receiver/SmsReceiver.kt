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

        val body = buildString {
            pdus.forEach { pdu ->
                val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }
                append(msg.messageBody)
            }
        }

        val parsed = SmsParser.parse(body) ?: return

        // זה כבר שומר מקומית וגם מפעיל סנכרון ל-Firebase דרך AppPrefs
        AppPrefs.saveBalance(context, parsed)

        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(ACTION_BALANCE_UPDATED))
    }

    companion object {
        const val ACTION_BALANCE_UPDATED = "com.stereotip.simdata.ACTION_BALANCE_UPDATED"
    }
}
