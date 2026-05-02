package com.stereotip.simdata

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AutoBalanceService : Service() {

    companion object {
        private const val CHANNEL_ID = "simdata_background_service"
        private const val NOTIFICATION_ID = 1019
        private const val BOOT_CHECK_DELAY_MS = 10000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var bootCheckStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildServiceNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runBootCheck = intent?.getBooleanExtra("run_boot_balance_check", false) == true

        if (runBootCheck && !bootCheckStarted) {
            bootCheckStarted = true

            handler.postDelayed({
                startBalanceCheckFromService()
            }, BOOT_CHECK_DELAY_MS)
        }

        return START_STICKY
    }

    private fun startBalanceCheckFromService() {
        try {
            val balanceIntent = Intent(this, BalanceActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("auto_start_check", true)
            }

            startActivity(balanceIntent)
        } catch (_: Exception) {
        }
    }

    private fun buildServiceNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1019,
            openIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("יתרת גלישה פועל")
            .setContentText("האפליקציה פעילה לבדיקת מצב חבילת הגלישה")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "שירות יתרת גלישה",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "התראה קבועה להפעלת בדיקות יתרת גלישה ברקע"
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}