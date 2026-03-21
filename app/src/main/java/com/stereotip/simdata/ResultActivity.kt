package com.stereotip.simdata

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultActivity : AppCompatActivity() {

    private lateinit var tvIcon: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSub: TextView
    private lateinit var tvTimer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        tvIcon = findViewById(R.id.tvIcon)
        tvTitle = findViewById(R.id.tvTitle)
        tvSub = findViewById(R.id.tvSub)
        tvTimer = findViewById(R.id.tvTimer)

        val success = intent.getBooleanExtra("success", true)

        if (success) {
            tvIcon.text = "✔"
            tvIcon.setTextColor(0xFF2ECC71.toInt())
            tvTitle.text = "ההרשמה בוצעה בהצלחה!"
            tvSub.text = "לא לשכוח להפעיל אחריות מוצר בעמוד הבא 🛡️"
        } else {
            tvIcon.text = "✖"
            tvIcon.setTextColor(0xFFE74C3C.toInt())
            tvTitle.text = "ההרשמה לא בוצעה"
            tvSub.text = "כי אין אינטרנט למכשיר.\nאנא התחבר ל-Wi-Fi או הגדר APN"
        }

        playNiceAnimation()
        startCountdown()
    }

    private fun playNiceAnimation() {
        tvIcon.scaleX = 0.2f
        tvIcon.scaleY = 0.2f
        tvIcon.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(tvIcon, TextView.SCALE_X, 0.2f, 1.15f, 1f)
        val scaleY = ObjectAnimator.ofFloat(tvIcon, TextView.SCALE_Y, 0.2f, 1.15f, 1f)
        val alpha = ObjectAnimator.ofFloat(tvIcon, TextView.ALPHA, 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 700
            interpolator = OvershootInterpolator(2f)
            start()
        }
    }

    private fun startCountdown() {
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000L).toInt()
                tvTimer.text = "אתם מועברים אוטומטית בעוד $seconds"
            }

            override fun onFinish() {
                val intent = Intent(this@ResultActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }
        }.start()
    }
}
