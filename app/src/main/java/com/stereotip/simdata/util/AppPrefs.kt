package com.stereotip.simdata.util

import android.content.Context
import android.content.SharedPreferences
import com.stereotip.simdata.data.BalanceResult
import com.stereotip.simdata.firebase.FirebaseManager

object AppPrefs {

    private const val PREFS = "simdata_prefs"

    private const val KEY_LINE = "line"
    private const val KEY_BALANCE_MB = "balance_mb"
    private const val KEY_VALID = "valid"
    private const val KEY_UPDATED = "updated"

    private const val KEY_CUSTOMER_NAME = "customer_name"
    private const val KEY_CUSTOMER_PHONE = "customer_phone"
    private const val KEY_CAR_MODEL = "car_model"
    private const val KEY_CAR_NUMBER = "car_number"
    private const val KEY_DATA_PACKAGE = "data_package"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun saveBalance(context: Context, result: BalanceResult) {

        val p = prefs(context)

        p.edit()
            .putString(KEY_LINE, result.lineNumber)
            .putInt(KEY_BALANCE_MB, result.dataMb ?: -1)
            .putString(KEY_VALID, result.validUntil)
            .putLong(KEY_UPDATED, result.updatedAt)
            .apply()

        // 🔥 החיבור למה שרצית — עובד על כל זיהוי (SMS + בדיקה)
        FirebaseManager.updateCustomer(context)
    }

    fun getLineNumber(context: Context): String? =
        prefs(context).getString(KEY_LINE, null)

    fun getBalanceMb(context: Context): Int? =
        prefs(context).getInt(KEY_BALANCE_MB, -1).takeIf { it >= 0 }

    fun getValid(context: Context): String? =
        prefs(context).getString(KEY_VALID, null)

    fun getUpdated(context: Context): Long =
        prefs(context).getLong(KEY_UPDATED, 0L)

    fun setCustomerName(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CUSTOMER_NAME, value).apply()
    }

    fun getCustomerName(context: Context): String =
        prefs(context).getString(KEY_CUSTOMER_NAME, "") ?: ""

    fun setCustomerPhone(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CUSTOMER_PHONE, value).apply()
    }

    fun getCustomerPhone(context: Context): String =
        prefs(context).getString(KEY_CUSTOMER_PHONE, "") ?: ""

    fun setCarModel(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CAR_MODEL, value).apply()
    }

    fun getCarModel(context: Context): String =
        prefs(context).getString(KEY_CAR_MODEL, "") ?: ""

    fun setCarNumber(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CAR_NUMBER, value).apply()
    }

    fun getCarNumber(context: Context): String =
        prefs(context).getString(KEY_CAR_NUMBER, "") ?: ""

    fun setDataPackage(context: Context, value: String) {
        prefs(context).edit().putString(KEY_DATA_PACKAGE, value).apply()
    }

    fun getDataPackage(context: Context): String =
        prefs(context).getString(KEY_DATA_PACKAGE, "לא ידוע / אין") ?: "לא ידוע / אין"
}
