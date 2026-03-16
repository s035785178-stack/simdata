package com.stereotip.simdata.util

import android.content.Context
import android.content.SharedPreferences
import com.stereotip.simdata.data.BalanceResult

object AppPrefs {
    private const val PREFS = "simdata_prefs"
    private const val KEY_LINE = "line"
    private const val KEY_BALANCE_MB = "balance_mb"
    private const val KEY_VALID = "valid"
    private const val KEY_RAW = "raw"
    private const val KEY_UPDATED = "updated"
    private const val KEY_INSTALL = "install"
    private const val KEY_HISTORY = "history"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureInstallTimestamp(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_INSTALL)) {
            p.edit().putLong(KEY_INSTALL, System.currentTimeMillis()).apply()
        }
    }

    fun getInstallTimestamp(context: Context): Long = prefs(context).getLong(KEY_INSTALL, 0L)

    fun saveBalance(context: Context, result: BalanceResult) {
        val p = prefs(context)
        p.edit()
            .putString(KEY_LINE, result.lineNumber)
            .putInt(KEY_BALANCE_MB, result.dataMb ?: -1)
            .putString(KEY_VALID, result.validUntil)
            .putString(KEY_RAW, result.rawMessage)
            .putLong(KEY_UPDATED, result.updatedAt)
            .apply()
        appendHistory(context, result)
    }

    fun getLineNumber(context: Context): String? = prefs(context).getString(KEY_LINE, null)
    fun getBalanceMb(context: Context): Int? = prefs(context).getInt(KEY_BALANCE_MB, -1).takeIf { it >= 0 }
    fun getValid(context: Context): String? = prefs(context).getString(KEY_VALID, null)
    fun getUpdated(context: Context): Long = prefs(context).getLong(KEY_UPDATED, 0L)
    fun getRaw(context: Context): String? = prefs(context).getString(KEY_RAW, null)

    fun getHistory(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_HISTORY, "") ?: ""
        return raw.split("\n---\n").filter { it.isNotBlank() }
    }

    private fun appendHistory(context: Context, result: BalanceResult) {
        val existing = getHistory(context).toMutableList()
        val entry = buildString {
            append("זמן: ").append(Formatter.formatDateTime(result.updatedAt)).append('\n')
            append("מספר: ").append(result.lineNumber ?: "לא זוהה").append('\n')
            append("יתרה: ").append(result.dataMb?.let { Formatter.mbToDisplay(it) } ?: "לא זוהה").append('\n')
            append("תוקף: ").append(result.validUntil ?: "לא זוהה")
        }
        existing.add(0, entry)
        val trimmed = existing.take(50)
        prefs(context).edit().putString(KEY_HISTORY, trimmed.joinToString("\n---\n")).apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
        ensureInstallTimestamp(context)
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).apply()
    }
}
