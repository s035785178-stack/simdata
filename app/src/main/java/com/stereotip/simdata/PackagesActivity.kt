package com.stereotip.simdata

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.QrUtils
import java.net.URLEncoder

class PackagesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_packages)

        findViewById<Button>(R.id.btnTrial).setOnClickListener {
            showPackageQr("4GB לחודשיים", "40₪")
        }
        findViewById<Button>(R.id.btnLong).setOnClickListener {
            showPackageQr("36GB ל-60 חודשים", "400₪")
        }
        findViewById<Button>(R.id.btnFeatured).setOnClickListener {
            showPackageQr("100GB לשנתיים", "250₪")
        }
        findViewById<Button>(R.id.btnBackPackages).setOnClickListener { finish() }
    }

    private fun showPackageQr(pkg: String, price: String) {
        val line = AppPrefs.getLineNumber(this) ?: "לא זוהה"
        val msg = "שלום, אני רוצה לחדש חבילת גלישה\n\nמספר מנוי: $line\nחבילה: $pkg\nמחיר: $price"
        val wa = "https://wa.me/972559911336?text=${URLEncoder.encode(msg, "UTF-8")}"
        val bitmap = QrUtils.createQrBitmap(wa)
        QrDialogFragment.newInstance(bitmap, "סרקו לחידוש מהנייד").show(supportFragmentManager, "qr_package")
    }
}
