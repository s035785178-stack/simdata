package com.stereotip.simdata.util

import android.content.Context
import android.content.SharedPreferences

object AppPrefs {

    private const val PREFS_NAME = "simdata_prefs"

    private fun prefs(ctx: Context): SharedPreferences {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ---------------------------
    // 🔥 כל מה שכבר קיים אצלך (לא נוגע)
    // ---------------------------

    fun getBalanceMb(ctx: Context): Int? =
        if (prefs(ctx).contains("balance_mb")) prefs(ctx).getInt("balance_mb", 0) else null

    fun setBalanceMb(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("balance_mb", value).apply()
    }

    fun getValid(ctx: Context): String? =
        prefs(ctx).getString("valid", null)

    fun setValid(ctx: Context, value: String?) {
        prefs(ctx).edit().putString("valid", value).apply()
    }

    fun getUpdated(ctx: Context): Long =
        prefs(ctx).getLong("updated", 0L)

    fun setUpdated(ctx: Context, value: Long) {
        prefs(ctx).edit().putLong("updated", value).apply()
    }

    fun getInstallTimestamp(ctx: Context): Long =
        prefs(ctx).getLong("install_time", 0L)

    fun ensureInstallTimestamp(ctx: Context) {
        if (!prefs(ctx).contains("install_time")) {
            prefs(ctx).edit().putLong("install_time", System.currentTimeMillis()).apply()
        }
    }

    fun getLineNumber(ctx: Context): String? =
        prefs(ctx).getString("line_number", null)

    fun setLineNumber(ctx: Context, value: String?) {
        prefs(ctx).edit().putString("line_number", value).apply()
    }

    fun getHistory(ctx: Context): List<String> {
        val set = prefs(ctx).getStringSet("history", emptySet()) ?: emptySet()
        return set.toList()
    }

    fun addHistory(ctx: Context, value: String) {
        val current = prefs(ctx).getStringSet("history", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        current.add(value)
        prefs(ctx).edit().putStringSet("history", current).apply()
    }

    fun clearHistory(ctx: Context) {
        prefs(ctx).edit().remove("history").apply()
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    // ---------------------------
    // 🔥 חדש — פרטי לקוח
    // ---------------------------

    fun setCustomerName(ctx: Context, value: String) {
        prefs(ctx).edit().putString("customer_name", value).apply()
    }

    fun getCustomerName(ctx: Context): String =
        prefs(ctx).getString("customer_name", "") ?: ""

    fun setCustomerPhone(ctx: Context, value: String) {
        prefs(ctx).edit().putString("customer_phone", value).apply()
    }

    fun getCustomerPhone(ctx: Context): String =
        prefs(ctx).getString("customer_phone", "") ?: ""

    fun setCarModel(ctx: Context, value: String) {
        prefs(ctx).edit().putString("car_model", value).apply()
    }

    fun getCarModel(ctx: Context): String =
        prefs(ctx).getString("car_model", "") ?: ""

    fun setCarNumber(ctx: Context, value: String) {
        prefs(ctx).edit().putString("car_number", value).apply()
    }

    fun getCarNumber(ctx: Context): String =
        prefs(ctx).getString("car_number", "") ?: ""

    fun setDataPackage(ctx: Context, value: String) {
        prefs(ctx).edit().putString("data_package", value).apply()
    }

    fun getDataPackage(ctx: Context): String =
        prefs(ctx).getString("data_package", "") ?: ""
}
