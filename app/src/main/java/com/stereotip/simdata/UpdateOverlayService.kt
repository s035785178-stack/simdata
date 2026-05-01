package com.stereotip.simdata

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class UpdateOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "יש לאשר הצגה מעל אפליקציות אחרות", Toast.LENGTH_LONG).show()

            val intentOverlay = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(intentOverlay)
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlayView != null) {
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra("title").orEmpty().ifBlank { "עדכון זמין" }
        val version = intent?.getStringExtra("version").orEmpty()
        val message = intent?.getStringExtra("message").orEmpty().ifBlank {
            "שיפורים ותיקונים כלליים באפליקציה"
        }
        val forceUpdate = intent?.getBooleanExtra("force", false) == true

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_update, null)

        val tvTitle = overlayView!!.findViewById<TextView>(R.id.tvOverlayTitle)
        val tvVersion = overlayView!!.findViewById<TextView>(R.id.tvOverlayVersion)
        val tvMessage = overlayView!!.findViewById<TextView>(R.id.tvOverlayMessage)
        val btnUpdate = overlayView!!.findViewById<Button>(R.id.btnUpdateNow)
        val btnClose = overlayView!!.findViewById<Button>(R.id.btnClose)

        tvTitle.text = title
        tvVersion.text = if (version.isNotBlank()) "גרסה $version" else "גרסה חדשה"
        tvMessage.text = message

        btnUpdate.setOnClickListener {
            val updateIntent = Intent(this, UpdateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(updateIntent)
            stopSelf()
        }

        if (forceUpdate) {
            btnClose.visibility = View.GONE
        } else {
            btnClose.visibility = View.VISIBLE
            btnClose.setOnClickListener {
                stopSelf()
            }
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