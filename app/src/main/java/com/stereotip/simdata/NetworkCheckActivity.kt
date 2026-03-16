package com.stereotip.simdata

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.Formatter
import com.stereotip.simdata.util.QrUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.net.URLEncoder

class NetworkCheckActivity : AppCompatActivity() {
    private lateinit var tvResults: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network)
        tvResults = findViewById(R.id.tvNetworkResults)
        val send = findViewById<Button>(R.id.btnSendNetwork)
        val refresh = findViewById<Button>(R.id.btnRefreshNetwork)
        val back = findViewById<Button>(R.id.btnBackNetwork)

        refresh.setOnClickListener { renderCheck() }
        back.setOnClickListener { finish() }
        send.setOnClickListener {
            val report = tvResults.text.toString()
            val msg = "בקשת תמיכה StereoTip\n\n$report"
            val wa = "https://wa.me/972559911336?text=${URLEncoder.encode(msg, "UTF-8")}"
            val bitmap = QrUtils.createQrBitmap(wa)
            val dialog = QrDialogFragment.newInstance(bitmap, "סרקו לשליחת הנתונים")
            dialog.show(supportFragmentManager, "qr")
        }
        renderCheck()
    }

    private fun renderCheck() {
        val res = TelephonyUtils.checkNetwork(this)
        tvResults.text = buildString {
            appendLine("📱 מספר קו: ${res.lineNumber}")
            appendLine("📶 SIM: ${res.simStatus}")
            appendLine("📡 רשת: ${res.networkType}")
            appendLine("📊 נתונים סלולריים: ${res.mobileDataStatus}")
            appendLine("🌍 נדידת נתונים: ${res.roamingStatus}")
            appendLine("⚙ APN: ${res.apnStatus}")
            appendLine("🌐 אינטרנט: ${res.internetStatus}")
            appendLine("🕒 עודכן: ${Formatter.formatDateTime(res.updatedAt)}")
        }
    }
}
