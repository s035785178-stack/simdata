package com.stereotip.simdata

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val forceUpdate: Boolean,
    val title: String,
    val message: String
)

object UpdateManager {

    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/s035785178-stack/simdata/releases/latest"

    fun fetchUpdateInfo(): Result<UpdateInfo> {
        return try {
            val url = URL(GITHUB_LATEST_RELEASE_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                doInput = true
                setRequestProperty("Accept", "application/vnd.github+json")
            }

            connection.connect()

            if (connection.responseCode !in 200..299) {
                return Result.failure(IllegalStateException("HTTP ${connection.responseCode}"))
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val cleanVersionName = tagName.removePrefix("v").trim()

            val releaseName = json.optString("name", "עדכון זמין")
            val releaseBody = json.optString("body", "יש גרסה חדשה זמינה לעדכון")

            val assets = json.getJSONArray("assets")
            var apkUrl = ""

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")

                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = downloadUrl
                    break
                }
            }

            if (cleanVersionName.isBlank()) {
                return Result.failure(IllegalStateException("Missing release tag"))
            }

            if (apkUrl.isBlank()) {
                return Result.failure(IllegalStateException("No APK asset found"))
            }

            val info = UpdateInfo(
                versionName = cleanVersionName,
                apkUrl = apkUrl,
                forceUpdate = false,
                title = releaseName.ifBlank { "עדכון זמין" },
                message = releaseBody.ifBlank { "יש גרסה חדשה זמינה לעדכון" }
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}