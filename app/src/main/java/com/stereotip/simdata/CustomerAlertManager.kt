package com.stereotip.simdata

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.google.firebase.firestore.FirebaseFirestore
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.Formatter
import com.stereotip.simdata.util.PhoneUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class CustomerAlertType {
    BALANCE_EMPTY,
    BALANCE_LOW,
    VALID_EXPIRED,
    VALID_SOON
}

data class CustomerAlertContent(
    val type: CustomerAlertType,
    val title: String,
    val message: String
)

object CustomerAlertManager {

    private val db = FirebaseFirestore.getInstance()

    fun checkAndShowAlerts(context: Context) {
        val lineNumber = normalizePhone(AppPrefs.getLineNumber(context))
        val customerPhone = normalizePhone(AppPrefs.getCustomerPhone(context))

        if (lineNumber.isNotBlank()) {
            db.collection("customers")
                .whereEqualTo("lineNumber", lineNumber)
                .limit(1)
                .get()
                .addOnSuccessListener { result ->
                    if (!result.isEmpty) {
                        val doc = result.documents.first()
                        checkDocumentAndShow(context, doc.data)
                    } else {
                        checkByPhoneOrLocal(context, customerPhone)
                    }
                }
                .addOnFailureListener {
                    checkLocalAndShow(context)
                }
            return
        }

        checkByPhoneOrLocal(context, customerPhone)
    }

    private fun checkByPhoneOrLocal(context: Context, phone: String) {
        if (phone.isBlank()) {
            checkLocalAndShow(context)
            return
        }

        db.collection("customers")
            .whereEqualTo("phone", phone)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val doc = result.documents.first()
                    checkDocumentAndShow(context, doc.data)
                } else {
                    db.collection("customers")
                        .whereEqualTo("customerPhone", phone)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { legacy ->
                            if (!legacy.isEmpty) {
                                val doc = legacy.documents.first()
                                checkDocumentAndShow(context, doc.data)
                            } else {
                                checkLocalAndShow(context)
                            }
                        }
                        .addOnFailureListener {
                            checkLocalAndShow(context)
                        }
                }
            }
            .addOnFailureListener {
                checkLocalAndShow(context)
            }
    }

    private fun checkDocumentAndShow(context: Context, data: Map<String, Any>?) {
        if (data == null) {
            checkLocalAndShow(context)
            return
        }

        val balanceMb = readBalanceMb(data)
        val validUntil = readValidUntil(data)

        checkValuesAndShow(
            context = context,
            balanceMb = balanceMb,
            validUntil = validUntil
        )
    }

    private fun checkLocalAndShow(context: Context) {
        val balanceMb = AppPrefs.getBalanceMb(context)
        val validUntil = AppPrefs.getValid(context).orEmpty()

        checkValuesAndShow(
            context = context,
            balanceMb = balanceMb,
            validUntil = validUntil
        )
    }

    private fun checkValuesAndShow(
        context: Context,
        balanceMb: Int?,
        validUntil: String
    ) {
        if (balanceMb != null && balanceMb >= 0) {
            when {
                balanceMb == 0 -> {
                    showAlert(context, balanceEmpty())
                    return
                }

                balanceMb in 1..1023 -> {
                    showAlert(context, balanceLow(Formatter.mbToDisplay(balanceMb)))
                    return
                }
            }
        }

        if (validUntil.isNotBlank()) {
            val daysLeft = getDaysLeft(validUntil)

            when {
                daysLeft < 0 -> {
                    showAlert(context, validExpired(validUntil))
                    return
                }

                daysLeft in 0..7 -> {
                    showAlert(context, validSoon(validUntil, daysLeft))
                    return
                }
            }
        }
    }

    private fun showAlert(context: Context, alert: CustomerAlertContent) {
        if (!Settings.canDrawOverlays(context)) {
            return
        }

        val intent = Intent(context, CustomerAlertOverlayService::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("title", alert.title)
            putExtra("message", alert.message)
        }

        context.startService(intent)
    }

    private fun balanceEmpty(): CustomerAlertContent {
        return CustomerAlertContent(
            type = CustomerAlertType.BALANCE_EMPTY,
            title = "חבילת הגלישה נגמרה",
            message = "חבילת הגלישה שלך הסתיימה.\nכדי להמשיך להשתמש באינטרנט במערכת, יש לחדש חבילה."
        )
    }

    private fun balanceLow(balanceText: String): CustomerAlertContent {
        return CustomerAlertContent(
            type = CustomerAlertType.BALANCE_LOW,
            title = "חבילת הגלישה עומדת להיגמר",
            message = "נשארה כמות קטנה של גלישה ($balanceText).\nכדאי לחדש חבילה כבר עכשיו כדי למנוע ניתוק."
        )
    }

    private fun validExpired(validUntil: String): CustomerAlertContent {
        return CustomerAlertContent(
            type = CustomerAlertType.VALID_EXPIRED,
            title = "חבילת הגלישה פגה",
            message = "תוקף חבילת הגלישה הסתיים בתאריך $validUntil.\nיש לחדש חבילה כדי להמשיך להשתמש בגלישה."
        )
    }

    private fun validSoon(validUntil: String, daysLeft: Long): CustomerAlertContent {
        return CustomerAlertContent(
            type = CustomerAlertType.VALID_SOON,
            title = "חבילת הגלישה עומדת להסתיים",
            message = "חבילת הגלישה שלך תסתיים בעוד $daysLeft ימים, בתאריך $validUntil.\nמומלץ לחדש חבילה מראש."
        )
    }

    private fun readBalanceMb(data: Map<String, Any>): Int? {
        val value = data["balanceMb"]
            ?: data["currentBalanceMb"]
            ?: return null

        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun readValidUntil(data: Map<String, Any>): String {
        return (data["validUntil"] as? String).orEmpty()
            .ifBlank { (data["valid"] as? String).orEmpty() }
    }

    private fun getDaysLeft(dateStr: String): Long {
        val formats = listOf(
            "d/M/yyyy",
            "dd/MM/yyyy",
            "yyyy-MM-dd",
            "dd.MM.yyyy",
            "dd.MM.yy"
        )

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                sdf.isLenient = false
                val target = sdf.parse(dateStr) ?: continue

                val todayStart = startOfToday()
                val targetStart = startOfDay(target.time)

                return TimeUnit.MILLISECONDS.toDays(targetStart - todayStart)
            } catch (_: Exception) {
            }
        }

        return 999
    }

    private fun startOfToday(): Long {
        return startOfDay(System.currentTimeMillis())
    }

    private fun startOfDay(timeMillis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun normalizePhone(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return when (normalized) {
            "לא זוהה", "לא זוהה מספר", "לא אושרו הרשאות" -> ""
            else -> normalized
        }
    }
}