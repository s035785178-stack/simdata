package com.stereotip.simdata.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.SmsParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle: Bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return

        val messages = pdus.mapNotNull { pdu ->
            try {
                SmsMessage.createFromPdu(pdu as ByteArray)
            } catch (_: Exception) {
                null
            }
        }

        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val balance = SmsParser.extractBalance(body)
        val valid = SmsParser.extractValid(body)
        val updated = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        if (balance.isNotBlank()) {
            AppPrefs.saveBalance(context, balance)
        }
        if (valid.isNotBlank()) {
            AppPrefs.saveValid(context, valid)
        }

        AppPrefs.saveUpdated(context, updated)
        AppPrefs.saveStatus(context, "התקבל SMS מ-019")
        AppPrefs.appendHistory(
            context,
            "$updated | יתרה: ${if (balance.isBlank()) "לא זוהתה" else balance} | תוקף: ${if (valid.isBlank()) "לא זוהה" else valid}"
        )
    }
}
