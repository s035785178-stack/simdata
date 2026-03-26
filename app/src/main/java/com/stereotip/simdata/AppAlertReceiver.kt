package com.stereotip.simdata

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stereotip.simdata.util.AppPrefs

class AppAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        runChecksNow(context.applicationContext)
    }

    companion object {

        private const val LOW_BALANCE_ID = 4101
        private const val STALE_CHECK_ID = 4102
        private const val EXPIRY_30_ID = 4103
        private const val EXPIRY_5_ID = 4104

        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val FOURTEEN_DAYS_MS = 14L * DAY_MS

        fun runChecksNow(context: Context) {
            checkLowBalance(context)
            checkStaleCheck(context)
            checkPackageExpiry(context)
        }

        private fun checkLowBalance(context: Context) {
            val balance = AppPrefs.getBalanceMb(context) ?: return
            if (balance >= 2048) return

            val now = System.currentTimeMillis()
            val last = AppPrefs.getLastLowBalanceAlertAt(context)
            if (now - last < DAY_MS) return

            NotificationHelper.showNotification(
                context = context,
                notificationId = LOW_BALANCE_ID,
                title = "יתרת הגלישה נמוכה",
                text = "נותרו פחות מ־2GB בחבילה. מומלץ לבדוק את מצב החבילה או לחדש גלישה."
            )
            AppPrefs.setLastLowBalanceAlertAt(context, now)
        }

        private fun checkStaleCheck(context: Context) {
            val updated = AppPrefs.getUpdated(context)
            if (updated <= 0L) return

            val now = System.currentTimeMillis()
            if (now - updated < FOURTEEN_DAYS_MS) return

            val last = AppPrefs.getLastStaleCheckAlertAt(context)
            if (now - last < DAY_MS) return

            NotificationHelper.showNotification(
                context = context,
                notificationId = STALE_CHECK_ID,
                title = "לא בוצעה בדיקת גלישה זמן רב",
                text = "עברו יותר מ־14 ימים מאז הבדיקה האחרונה. מומלץ לבצע בדיקה לרענון הנתונים."
            )
            AppPrefs.setLastStaleCheckAlertAt(context, now)
        }

        private fun checkPackageExpiry(context: Context) {
            val daysLeft = PackageExpiryManager.daysUntilFinalExpiry(context) ?: return
            val now = System.currentTimeMillis()

            if (daysLeft <= 5) {
                val last = AppPrefs.getLastExpiry5AlertAt(context)
                if (now - last >= DAY_MS) {
                    NotificationHelper.showNotification(
                        context = context,
                        notificationId = EXPIRY_5_ID,
                        title = "חבילת הגלישה עומדת להסתיים",
                        text = "נותרו 5 ימים או פחות לסיום החבילה. מומלץ לחדש בהקדם כדי להימנע מהפסקת שירות."
                    )
                    AppPrefs.setLastExpiry5AlertAt(context, now)
                }
                return
            }

            if (daysLeft <= 30) {
                val last = AppPrefs.getLastExpiry30AlertAt(context)
                if (now - last >= DAY_MS) {
                    NotificationHelper.showNotification(
                        context = context,
                        notificationId = EXPIRY_30_ID,
                        title = "חבילת הגלישה מסתיימת בקרוב",
                        text = "נותרו כ־30 ימים לסיום החבילה. מומלץ להיערך לחידוש מראש."
                    )
                    AppPrefs.setLastExpiry30AlertAt(context, now)
                }
            }
        }
    }
}