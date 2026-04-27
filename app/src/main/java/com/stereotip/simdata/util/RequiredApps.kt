package com.stereotip.simdata.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object RequiredApps {

    const val DIALER_APK_URL: String =
        "https://github.com/s035785178-stack/simdata/releases/download/dialer/dialer.apk"

    const val DIALER_APK_FILE_NAME: String = "dialer.apk"

    fun isRequiredDialerAvailable(context: Context): Boolean {
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:*019"))
        return callIntent.resolveActivity(context.packageManager) != null
    }
}
