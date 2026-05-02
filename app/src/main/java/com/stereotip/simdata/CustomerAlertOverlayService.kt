package com.stereotip.simdata

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class CustomerAlertOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlayView != null) return START_NOT_STICKY

        val title = intent?.getStringExtra("title") ?: "התראה"
        val message = intent?.getStringExtra("message") ?: ""

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_customer_alert, null)

        val tvTitle = overlayView!!.findViewById<TextView>(R.id.tvAlertTitle)
        val tvMessage = overlayView!!.findViewById<TextView>(R.id.tvAlertMessage)
        val btnClose = overlayView!!.findViewById<Button>(R.id.btnCloseAlert)
        val btnRenew = overlayView!!.findViewById<Button>(R.id.btnRenewPackage)

        tvTitle.text = title
        tvMessage.text = message

        btnClose.setOnClickListener {
            stopSelf()
        }

        btnRenew.setOnClickListener {
            val packagesIntent = Intent(this, PackagesActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(packagesIntent)
            stopSelf()
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(overlayView, params)
        } catch (_: Exception) {
            overlayView = null
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (_: Exception) {
            overlayView = null
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}