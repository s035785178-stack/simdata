// 🔥 שים לב – זה רק החלק החשוב ששינינו (בתוך הקובץ שלך הקיים)
// אם אתה רוצה – אשלח לך גם גרסה מלאה 1:1 של כל הקובץ

private fun hasRealSmsAccess(): Boolean {
    return try {
        val cursor = contentResolver.query(
            android.provider.Telephony.Sms.CONTENT_URI,
            null, null, null, null
        )
        cursor?.close()
        true
    } catch (e: Exception) {
        false
    }
}

private fun hasRealPhoneAccess(): Boolean {
    return try {
        val line = TelephonyUtils.getLineNumber(this)
        line.isNotBlank()
    } catch (e: Exception) {
        false
    }
}

// 🔥 זה הקריטי
private fun hasAnyMissingStartupPermission(): Boolean {

    val phoneMissing = getMissingPermissions(phonePermissions()).isNotEmpty()
    val smsMissing = getMissingPermissions(smsPermissions()).isNotEmpty()
    val notificationMissing = getMissingPermissions(notificationPermissions()).isNotEmpty()

    val smsOk = !smsMissing || hasRealSmsAccess()
    val phoneOk = !phoneMissing || hasRealPhoneAccess()

    return !(smsOk && phoneOk && !notificationMissing)
}
