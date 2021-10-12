package ru.myus.checkbreath

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import ru.myus.checkbreath.R
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.motion.widget.TransitionAdapter
import android.os.Looper
import ru.myus.checkbreath.SplashActivity
import android.content.Intent
import android.os.Handler
import ru.myus.checkbreath.MainActivity

class SplashActivity : AppCompatActivity() {
    companion object {
        var SPLASH_DELAY = 800
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_splash)
        val motionLayout = findViewById<MotionLayout>(R.id.splash_root)
        motionLayout.setTransitionListener(object : TransitionAdapter() {
            override fun onTransitionCompleted(motionLayout: MotionLayout, i: Int) {
                if (i == R.id.splash_showing) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        motionLayout.setTransition(R.id.trans_2)
                        motionLayout.transitionToEnd()
                    }, SPLASH_DELAY.toLong())
                } else if (i == R.id.end) {
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    startActivity(intent)
                    finish()
                }
            }
        })
    }
}