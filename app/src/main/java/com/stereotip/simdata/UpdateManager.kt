package com.stereotip.simdata

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val forceUpdate: Boolean,
    val title: String,
    val message: String
)

object UpdateManager {

    // החלף לכתובת ה-JSON האמיתית שלך
    private const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/s035785178-stack/simdata/main/update.json"

    fun fetchUpdateInfo(): Result<UpdateInfo> {
        return try {
            val url = URL(UPDATE_JSON_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                doInput = true
                useCaches = false
            }

            connection.connect()

            if (connection.responseCode !in 200..299) {
                return Result.failure(IllegalStateException("HTTP ${connection.responseCode}"))
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val forceUpdate = json.optBoolean("forceUpdate", false)
            val versionName = json.getString("versionName")

            val defaultTitle = if (forceUpdate) "עדכון חובה" else "עדכון מומלץ"
            val defaultMessage = if (forceUpdate) {
                "יש גרסה חדשה שחובה להתקין כדי להמשיך להשתמש באפליקציה."
            } else {
                "יש גרסה חדשה מומלצת להתקנה. אפשר להמשיך גם בלי לעדכן כרגע."
            }

            val info = UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = versionName,
                apkUrl = json.getString("apkUrl"),
                forceUpdate = forceUpdate,
                title = json.optString("title", defaultTitle),
                message = json.optString(
                    "message",
                    "גרסה $versionName זמינה להורדה.\n\n$defaultMessage"
                )
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
