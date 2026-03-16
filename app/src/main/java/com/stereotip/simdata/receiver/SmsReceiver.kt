package com.stereotip.simdata.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import com.stereotip.simdata.util.AppPrefs
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

        val balance = extractRelevantBalance(body)
        val valid = extractValue(body, "Valid")
        val updated = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        if (balance.isNotBlank()) {
            AppPrefs.saveBalance(context, balance)
        }
        if (valid.isNotBlank()) {
            AppPrefs.saveValid(context, valid)
        }

        AppPrefs.saveUpdated(context, updated)
        AppPrefs.saveStatus(context, "התקבל SMS מ-019")
        AppPrefs.appendHistory(context, "$updated | יתרה: ${if (balance.isBlank()) "לא זוהתה" else balance} | תוקף: ${if (valid.isBlank()) "לא זוהה" else valid}")
    }

    private fun extractRelevantBalance(body: String): String {
        val dataInternet = extractValue(body, "Data Internet")
        val yourBalance = extractValue(body, "Your Balance")

        return when {
            dataInternet.isNotBlank() && isLikelyRelevant(dataInternet) -> "$dataInternet MB"
            yourBalance.isNotBlank() && isLikelyRelevant(yourBalance) -> "$yourBalance MB"
            dataInternet.isNotBlank() -> dataInternet
            yourBalance.isNotBlank() -> yourBalance
            else -> ""
        }
    }

    private fun extractValue(body: String, label: String): String {
        val regex = Regex("$label\\s*:?\\s*([0-9.]+)", RegexOption.IGNORE_CASE)
        return regex.find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    private fun isLikelyRelevant(value: String): Boolean {
        val num = value.toDoubleOrNull() ?: return false
        return num >= 500
    }
}
