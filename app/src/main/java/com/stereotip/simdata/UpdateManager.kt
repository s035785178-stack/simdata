package com.stereotip.simdata

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
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

    private const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/s035785178-stack/simdata/main/update.json"
    private const val APK_FILE_NAME = "simdata_update.apk"

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

    fun startDownload(
        context: Context,
        apkUrl: String,
        fileName: String = APK_FILE_NAME,
        title: String = "מוריד עדכון",
        description: String = "מוריד את הגרסה החדשה..."
    ): Long {
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle(title)
            setDescription(description)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setMimeType("application/vnd.android.package-archive")
            setDestinationUri(Uri.fromFile(file))
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    fun queryProgress(context: Context, downloadId: Long): DownloadProgress {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        var cursor: Cursor? = null
        return try {
            cursor = manager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                val bytesDownloaded =
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal =
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val localUri =
                    cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                val percent = if (bytesTotal > 0L) {
                    ((bytesDownloaded * 100L) / bytesTotal).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                DownloadProgress(
                    status = status,
                    reason = reason,
                    bytesDownloaded = bytesDownloaded,
                    bytesTotal = bytesTotal,
                    percent = percent,
                    localUri = localUri
                )
            } else {
                DownloadProgress.notFound()
            }
        } finally {
            cursor?.close()
        }
    }

    fun installDownloadedApk(
        context: Context,
        localUri: String?,
        fallbackFileName: String = APK_FILE_NAME
    ) {
        val apkUri = when {
            !localUri.isNullOrBlank() && localUri.startsWith("file://") -> {
                val file = File(Uri.parse(localUri).path ?: "")
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            }

            !localUri.isNullOrBlank() -> Uri.parse(localUri)

            else -> {
                val file = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    fallbackFileName
                )
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            }
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(installIntent)
        } catch (_: ActivityNotFoundException) {
            openUnknownAppsSettings(context)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownAppsSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

data class DownloadProgress(
    val status: Int,
    val reason: Int,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val percent: Int,
    val localUri: String?
) {
    companion object {
        fun notFound(): DownloadProgress {
            return DownloadProgress(
                status = DownloadManager.STATUS_FAILED,
                reason = -1,
                bytesDownloaded = 0L,
                bytesTotal = 0L,
                percent = 0,
                localUri = null
            )
        }
    }
}