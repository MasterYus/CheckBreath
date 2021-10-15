package ru.myus.checkbreath.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import ru.myus.checkbreath.R
import kotlin.math.exp
import kotlin.math.min

class TimerView(context: Context, attributeSet: AttributeSet) : View(context,attributeSet) {
    private var radius:Float = 0.0f
    private var indicatorRadius = 0.0f
    private var currRadius = 0.0f
    private var circlePaint: Paint = Paint()
    private var outlinePaint: Paint = Paint()

    var animator: ValueAnimator = ValueAnimator.ofFloat(0.9f,1.0f)

    init {
        circlePaint.apply{
            color = ContextCompat.getColor(context, R.color.timer_layer)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        outlinePaint.apply{
            color = ContextCompat.getColor(context, R.color.timer_layer)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        with(animator){
            duration = 1000
            addUpdateListener {
                    currRadius = radius*animatedValue as Float
                    invalidate()
                    //Toast.makeText(context,"animate!",Toast.LENGTH_SHORT).show()
            }
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        if(viewTreeObserver.isAlive) {
            viewTreeObserver.addOnGlobalLayoutListener {
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (!animator.isRunning) animator.start()
                        viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            }
        }
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        radius = (min(w,h)/2*0.6).toFloat()
        currRadius = radius
        indicatorRadius = radius/25
        circlePaint.strokeWidth = radius/20
        /*circlePaint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            ContextCompat.getColor(context, R.color.accent),
            ContextCompat.getColor(context, R.color.accent_variant), Shader.TileMode.MIRROR);
         */
        invalidate()
    }


    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let{
            it.drawCircle((width/2).toFloat(),(height/2).toFloat(),currRadius*1.2f,outlinePaint)
            it.drawCircle((width/2).toFloat(),(height/2).toFloat(),currRadius,circlePaint)
        }
    }
}