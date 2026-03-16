package com.stereotip.simdata.util

import android.content.Context

object AppPrefs {

    private const val PREFS_NAME = "simdata_prefs"

    private const val KEY_LINE_NUMBER = "line_number"
    private const val KEY_LAST_BALANCE = "last_balance"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val KEY_LAST_STATUS = "last_status"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLineNumber(context: Context, value: String) {
        prefs(context).edit().putString(KEY_LINE_NUMBER, value).apply()
    }

    fun getLineNumber(context: Context): String {
        return prefs(context).getString(KEY_LINE_NUMBER, "") ?: ""
    }

    fun saveLastBalance(context: Context, value: String) {
        prefs(context).edit().putString(KEY_LAST_BALANCE, value).apply()
    }

    fun getLastBalance(context: Context): String {
        return prefs(context).getString(KEY_LAST_BALANCE, "") ?: ""
    }

    fun saveLastCheckTime(context: Context, value: String) {
        prefs(context).edit().putString(KEY_LAST_CHECK_TIME, value).apply()
    }

    fun getLastCheckTime(context: Context): String {
        return prefs(context).getString(KEY_LAST_CHECK_TIME, "") ?: ""
    }

    fun saveLastStatus(context: Context, value: String) {
        prefs(context).edit().putString(KEY_LAST_STATUS, value).apply()
    }

    fun getLastStatus(context: Context): String {
        return prefs(context).getString(KEY_LAST_STATUS, "") ?: ""
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
