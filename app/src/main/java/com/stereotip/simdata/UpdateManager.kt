package com.stereotip.simdata

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val forceUpdate: Boolean,
    val message: String
)

object UpdateManager {

    // מחר תחליף ללינק האמיתי שלך
    private const val UPDATE_JSON_URL = "https://example.com/update.json"

    fun checkForUpdate(): Result<UpdateInfo> {
        return try {
            val url = URL(UPDATE_JSON_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != 200) {
                return Result.failure(Exception("HTTP ${connection.responseCode}"))
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val info = UpdateInfo(
                latestVersionCode = json.optInt("versionCode", 1),
                latestVersionName = json.optString("versionName", "לא ידוע"),
                apkUrl = json.optString("apkUrl", ""),
                forceUpdate = json.optBoolean("forceUpdate", false),
                message = json.optString("message", "קיימת גרסה חדשה לאפליקציה")
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
