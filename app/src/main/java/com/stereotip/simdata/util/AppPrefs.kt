package com.stereotip.simdata.util

import android.content.Context

object AppPrefs {

    private const val PREFS = "simdata_prefs"

    private const val KEY_LINE = "line"
    private const val KEY_BALANCE = "balance"
    private const val KEY_VALID = "valid"
    private const val KEY_UPDATED = "updated"
    private const val KEY_HISTORY = "history"
    private const val KEY_INSTALL = "install"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveLineNumber(context: Context, value: String) {
        prefs(context).edit().putString(KEY_LINE, value).apply()
    }

    fun getLineNumber(context: Context): String {
        return prefs(context).getString(KEY_LINE, "") ?: ""
    }

    fun saveBalance(context: Context, value: String) {
        prefs(context).edit().putString(KEY_BALANCE, value).apply()
    }

    fun getBalanceMb(context: Context): String {
        return prefs(context).getString(KEY_BALANCE, "") ?: ""
    }

    fun saveValid(context: Context, value: String) {
        prefs(context).edit().putString(KEY_VALID, value).apply()
    }

    fun getValid(context: Context): String {
        return prefs(context).getString(KEY_VALID, "") ?: ""
    }

    fun saveUpdated(context: Context, value: String) {
        prefs(context).edit().putString(KEY_UPDATED, value).apply()
    }

    fun getUpdated(context: Context): String {
        return prefs(context).getString(KEY_UPDATED, "") ?: ""
    }

    fun saveHistory(context: Context, value: String) {
        prefs(context).edit().putString(KEY_HISTORY, value).apply()
    }

    fun getHistory(context: Context): String {
        return prefs(context).getString(KEY_HISTORY, "") ?: ""
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).apply()
    }

    fun saveInstallTimestamp(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_INSTALL, value).apply()
    }

    fun getInstallTimestamp(context: Context): Long {
        return prefs(context).getLong(KEY_INSTALL, 0)
    }
}
