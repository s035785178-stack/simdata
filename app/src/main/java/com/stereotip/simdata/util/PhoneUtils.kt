package com.stereotip.simdata.util

object PhoneUtils {

    fun normalizeToLocal(phone: String?): String {
        if (phone.isNullOrBlank()) return "לא זוהה"

        var p = phone.trim()

        // הסרת כל מה שלא מספרים
        p = p.replace(Regex("[^0-9]"), "")

        return when {
            // 9725XXXXXXXX → 05XXXXXXXX
            p.startsWith("9725") && p.length >= 12 -> {
                "0" + p.substring(3)
            }

            // 5XXXXXXXX → 05XXXXXXXX
            p.startsWith("5") && p.length == 9 -> {
                "0$p"
            }

            // כבר תקין
            p.startsWith("05") && p.length == 10 -> {
                p
            }

            else -> {
                p
            }
        }
    }
}
