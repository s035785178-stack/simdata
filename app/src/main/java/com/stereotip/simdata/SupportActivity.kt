package com.stereotip.simdata

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.Formatter
import com.stereotip.simdata.util.QrUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.net.URLEncoder

class SupportActivity : AppCompatActivity() {
    private lateinit var ivQr: ImageView
    private lateinit var tvQrTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)
        ivQr = findViewById(R.id.ivQr)
        tvQrTitle = findViewById(R.id.tvQrTitle)

        findViewById<Button>(R.id.btnSendData).setOnClickListener { createDiagnosticsQr() }
        findViewById<Button>(R.id.btnRegularSupport).setOnClickListener { createBasicSupportQr() }
        findViewById<Button>(R.id.btnBackSupport).setOnClickListener { finish() }
    }

    private fun createBasicSupportQr() {
        val line = AppPrefs.getLineNumber(this) ?: TelephonyUtils.getLineNumber(this)
        val msg = "שלום, אני צריך תמיכה במולטימדיה\n\nמספר מנוי: $line"
        setQr("סרקו לפנייה בוואטסאפ", msg)
    }

    private fun createDiagnosticsQr() {
        val network = TelephonyUtils.checkNetwork(this)
        val bal = AppPrefs.getBalanceMb(this)?.let { Formatter.mbToDisplay(it) } ?: "לא בוצעה בדיקה"
        val valid = AppPrefs.getValid(this) ?: "---"
        val msg = "בקשת תמיכה StereoTip\n\nמספר קו: ${network.lineNumber}\nיתרה אחרונה: $bal\nתוקף: $valid\n\nSIM: ${network.simStatus}\nרשת: ${network.networkType}\nנתונים סלולריים: ${network.mobileDataStatus}\nנדידה: ${network.roamingStatus}\nAPN: ${network.apnStatus}\nאינטרנט: ${network.internetStatus}"
        setQr("סרקו לשליחת נתוני המכשיר", msg)
    }

    private fun setQr(title: String, message: String) {
        tvQrTitle.text = title
        val wa = "https://wa.me/972559911336?text=${URLEncoder.encode(message, "UTF-8")}"
        val bitmap: Bitmap = QrUtils.createQrBitmap(wa)
        ivQr.setImageBitmap(bitmap)
    }
}
