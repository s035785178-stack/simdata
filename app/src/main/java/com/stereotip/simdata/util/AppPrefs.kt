package com.stereotip.simdata.util

import android.content.Context
import android.content.SharedPreferences

object AppPrefs {

    private fun prefs(ctx: Context): SharedPreferences {
        return ctx.getSharedPreferences("simdata_prefs", Context.MODE_PRIVATE)
    }

    fun set(ctx: Context, key: String, value: String) {
        prefs(ctx).edit().putString(key, value).apply()
    }

    fun get(ctx: Context, key: String): String {
        return prefs(ctx).getString(key, "") ?: ""
    }

    fun ensureInstallTimestamp(ctx: Context) {
        if (!prefs(ctx).contains("install_time")) {
            prefs(ctx).edit().putLong("install_time", System.currentTimeMillis()).apply()
        }
    }
}
