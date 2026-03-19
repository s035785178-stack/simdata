package com.stereotip.simdata.util

import android.content.Context
import android.content.SharedPreferences
import com.stereotip.simdata.data.BalanceResult

object AppPrefs {

    private const val PREFS = "simdata_prefs"

    private const val KEY_LINE = "line"
    private const val KEY_BALANCE_MB = "balance_mb"
    private const val KEY_VALID = "valid"
    private const val KEY_UPDATED = "updated"
    private const val KEY_INSTALL = "install"
    private const val KEY_HISTORY = "history"

    private const val KEY_CUSTOMER_NAME = "customer_name"
    private const val KEY_CUSTOMER_PHONE = "customer_phone"
    private const val KEY_CAR_MODEL = "car_model"
    private const val KEY_CAR_NUMBER = "car_number"
    private const val KEY_DATA_PACKAGE = "data_package"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun ensureInstallTimestamp(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_INSTALL)) {
            p.edit().putLong(KEY_INSTALL, System.currentTimeMillis()).apply()
        }
    }

    fun getInstallTimestamp(context: Context): Long {
        return prefs(context).getLong(KEY_INSTALL, 0L)
    }

    fun saveBalance(context: Context, result: BalanceResult) {
        prefs(context).edit()
            .putString(KEY_LINE, result.lineNumber)
            .putInt(KEY_BALANCE_MB, result.dataMb ?: -1)
            .putString(KEY_VALID, result.validUntil)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()

        addHistory(
            context,
            buildString {
                append("מספר: ").append(result.lineNumber ?: "לא זוהה").append('\n')
                append("יתרה: ").append(result.dataMb?.toString() ?: "לא זוהה").append('\n')
                append("תוקף: ").append(result.validUntil ?: "לא זוהה")
            }
        )

        // 🔥 זה החיבור ל-Firebase
        FirebaseCustomerSync.sync(context)
    }

    fun getLineNumber(context: Context): String? {
        return prefs(context).getString(KEY_LINE, null)
    }

    fun getBalanceMb(context: Context): Int? {
        val value = prefs(context).getInt(KEY_BALANCE_MB, -1)
        return value.takeIf { it >= 0 }
    }

    fun getValid(context: Context): String? {
        return prefs(context).getString(KEY_VALID, null)
    }

    fun getUpdated(context: Context): Long {
        return prefs(context).getLong(KEY_UPDATED, 0L)
    }

    fun getHistory(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n---\n").filter { it.isNotBlank() }
    }

    fun addHistory(context: Context, entry: String) {
        val current = getHistory(context).toMutableList()
        current.add(0, entry)
        val trimmed = current.take(50)
        prefs(context).edit()
            .putString(KEY_HISTORY, trimmed.joinToString("\n---\n"))
            .apply()
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
        ensureInstallTimestamp(context)
    }

    fun setCustomerName(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CUSTOMER_NAME, value).apply()
    }

    fun getCustomerName(context: Context): String {
        return prefs(context).getString(KEY_CUSTOMER_NAME, "") ?: ""
    }

    fun setCustomerPhone(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CUSTOMER_PHONE, value).apply()
    }

    fun getCustomerPhone(context: Context): String {
        return prefs(context).getString(KEY_CUSTOMER_PHONE, "") ?: ""
    }

    fun setCarModel(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CAR_MODEL, value).apply()
    }

    fun getCarModel(context: Context): String {
        return prefs(context).getString(KEY_CAR_MODEL, "") ?: ""
    }

    fun setCarNumber(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CAR_NUMBER, value).apply()
    }

    fun getCarNumber(context: Context): String {
        return prefs(context).getString(KEY_CAR_NUMBER, "") ?: ""
    }

    fun setDataPackage(context: Context, value: String) {
        prefs(context).edit().putString(KEY_DATA_PACKAGE, value).apply()
    }

    fun getDataPackage(context: Context): String {
        return prefs(context).getString(KEY_DATA_PACKAGE, "לא ידוע / אין") ?: "לא ידוע / אין"
    }
}
