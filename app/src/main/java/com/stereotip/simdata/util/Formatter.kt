package com.stereotip.simdata.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatter {
    private val dateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val dateOnly = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun formatDateTime(time: Long): String = if (time <= 0) "---" else dateTime.format(Date(time))
    fun formatDate(time: Long): String = if (time <= 0) "---" else dateOnly.format(Date(time))

    fun mbToDisplay(mb: Int): String {
        return if (mb >= 1024) {
            val gb = mb / 1024.0
            String.format(Locale.US, "%.1fGB", gb)
        } else {
            "$mb MB"
        }
    }

    fun balanceStatus(mb: Int?): String {
        return when {
            mb == null -> "לא בוצעה בדיקה"
            mb < 1000 -> "🔴 החבילה עומדת להסתיים"
            mb < 5000 -> "🟠 יתרת החבילה נמוכה"
            else -> "🟢 מצב חבילה תקין"
        }
    }
}
