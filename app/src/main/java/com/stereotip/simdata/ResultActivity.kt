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
        val phone = intent.getStringExtra("phone") ?: ""

        if (success) {
            tvIcon.text = "✔"
            tvTitle.text = "ההרשמה בוצעה בהצלחה!"
            tvSub.text = "ממשיכים להפעלת אחריות..."
        } else {
            tvIcon.text = "✖"
            tvTitle.text = "שגיאה בהרשמה"
            tvSub.text = "נסה שוב"
        }

        playAnimation()
        startCountdown(phone)
    }

    private fun playAnimation() {
        val scaleX = ObjectAnimator.ofFloat(tvIcon, "scaleX", 0.5f, 1f)
        val scaleY = ObjectAnimator.ofFloat(tvIcon, "scaleY", 0.5f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 400
            interpolator = OvershootInterpolator()
            start()
        }
    }

    private fun startCountdown(phone: String) {
        object : CountDownTimer(2500, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                tvTimer.text = "מעבר בעוד $sec..."
            }

            override fun onFinish() {
                val intent = Intent(this@ResultActivity, WarrantyPromptActivity::class.java)
                intent.putExtra("phone", phone)
                startActivity(intent)
                finish()
            }
        }.start()
    }
}