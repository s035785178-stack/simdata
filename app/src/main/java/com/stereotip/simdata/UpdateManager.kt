package com.stereotip.simdata

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val forceUpdate: Boolean
)

object UpdateManager {

    // החלף לכתובת האמיתית שלך כשתהיה מוכנה
    private const val UPDATE_JSON_URL = "https://your-server.com/update.json"

    fun fetchUpdateInfo(): Result<UpdateInfo> {
        return try {
            val url = URL(UPDATE_JSON_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                doInput = true
            }

            connection.connect()

            if (connection.responseCode !in 200..299) {
                return Result.failure(IllegalStateException("HTTP ${connection.responseCode}"))
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val info = UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                forceUpdate = json.optBoolean("forceUpdate", false)
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
