package ru.myus.checkbreath.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import ru.myus.checkbreath.R
import java.util.*
import android.view.View


class SoundView(context: Context, attributeSet: AttributeSet) : View(context,attributeSet) {
    var barNum = 50
    var sidePadding = 0.0f
    var topPadding = 0.0f
    var centerPadding = 0.0f
    var barGap = 5.0f
    var barWidth = 0.0f
    var lineWidth = 0.0f
    var barType = BarType.DOUBLED
    var customSizes = false
    var animator:ValueAnimator? = null

    private val barList = LinkedList<BarHolder>()

    enum class BarType{
        SINGLE_LINE,
        DOUBLED
    }

    init {
    }

    private val linePaint = indicatorPaintInactive()

    fun animateNext(percentage: Float){
        nextValue(percentage)
    }

    private fun nextValue(percentage: Float){
        post{
            with(barList) {
                if (isNotEmpty() && size > barNum+1) {
                    remove(first)
                }
                animator?.let{
                    if(it.isRunning) {
                        it.end()
                    }
                }
                animator = ValueAnimator.ofFloat(0f,1.0f).apply {
                    duration = 200
                    interpolator = LinearInterpolator()
                    forEach { barHolder ->
                        barHolder.index--
                        addUpdateListener (barHolder)
                        addListener(barHolder)
                    }
                    addUpdateListener {
                        invalidate()
                    }
                    start()
                }
                add(BarHolder(barNum-1, BarType.DOUBLED).also {
                    it.animateToValue(percentage)
                })
            }
        }
    }

    private fun indicatorPaintInactive():Paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.indicator_inactive_dark)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        isDither = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if(!customSizes){
            sidePadding = 0.0f
            barWidth = (w - 2*sidePadding + barGap)/barNum.toFloat()-barGap
            topPadding = w*0.2f
            lineWidth = barWidth/2
            centerPadding = lineWidth*2
        }
        linePaint.strokeWidth = lineWidth
    }

    private fun invalidateBars(){
        if(barList.isNotEmpty()) barList.clear()
        for (i in 0 until barNum){
            barList.add(BarHolder(i,barType))
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let{
            when (barType){
                BarType.DOUBLED -> {
                    if (centerPadding > 0) it.drawLine(sidePadding,height/2.toFloat(),
                            width-sidePadding,height/2.toFloat(),linePaint)
                }
                BarType.SINGLE_LINE -> TODO()
            }
            for (bar in barList){
                bar.draw(canvas)
            }
        }
    }

    /**
     * Helper class for visualizing bars in sound wave
     @param index indicates position of line bar in soundline diagram
     @param parent represents parent view the bar to be drawn on
     */
    inner class BarHolder(index:Int, var barType: BarType):ValueAnimator.AnimatorUpdateListener, AnimatorListenerAdapter() {
        private var line:Path = Path()
        private var barPath:Path = Path()
        private var draft:Path = Path()
        private val barPaint = indicatorPaintInactive()
        private val shadowBarPaint = indicatorPaintInactive()
        private val mMatrix = Matrix()
        var xPos = 0.0f
        private var oldxPos = 0f
        private var barAnim:ValueAnimator? = null
        private lateinit var pathMeasure: PathMeasure
        var value:Float = 0.0f
        var index = index

        init {
            computeDraftDimensions()
        }

        private fun computeXpos(){
            xPos = sidePadding + (barWidth)/2 + (barWidth+barGap)*(index).toFloat()
        }

        private fun computeDraftDimensions() {
            computeXpos()
            when (barType){
                BarType.DOUBLED -> {
                    with(line){
                        moveTo(xPos,height/2.toFloat() + centerPadding)
                        lineTo(xPos, height - topPadding)
                        //Log.e("pos","x: $xPos y:$height barw:$barWidth )")
                    }
                }
                BarType.SINGLE_LINE -> TODO()
            }
            with(barPaint){
                strokeWidth = barWidth.also { shadowBarPaint.strokeWidth = it }
                barPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat()-topPadding,
                    ContextCompat.getColor(context, R.color.accent_variant),
                    ContextCompat.getColor(context, R.color.accent), Shader.TileMode.MIRROR)
                color = ContextCompat.getColor(context, R.color.white)
            }
        }

        fun translateX(pos:Float){
            mMatrix.reset()
            mMatrix.postTranslate(pos-xPos,0f)
            barPath.transform(mMatrix)
            draft.transform(mMatrix)
            xPos = pos
        }

        override fun onAnimationUpdate(animation: ValueAnimator) {
            translateX(oldxPos-animation.animatedValue as Float *(barGap+barWidth))
        }

        override fun onAnimationStart(animation: Animator?) {
            oldxPos = xPos
        }

        override fun onAnimationEnd(animation: Animator) {
            animation.removeListener(this)
            (animation as ValueAnimator).removeUpdateListener(this)
            computeXpos()
            translateX(xPos)
        }

        fun animateToValue(percentage:Float){
            value = percentage
            // get line footprint measure
            pathMeasure = PathMeasure(line, false)
            // get draft to animate current value
            draft.reset()
            pathMeasure.getSegment(0.0f, pathMeasure.length * value, draft, true)
            if (barAnim?.isRunning == true) {
                barAnim!!.cancel()
            }
            barPath.reset()
            barAnim = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                duration = 700
                repeatCount = 1
                interpolator = AccelerateDecelerateInterpolator()
            }
            barAnim?.let {
                it.addUpdateListener { animation ->
                    val fraction = animation.animatedValue as Float
                    pathMeasure = PathMeasure(draft,false)
                    pathMeasure.getSegment(0.0f, pathMeasure.length * fraction, barPath, true)
                    invalidate()
                }
                it.start()
            }
        }

        fun draw(canvas: Canvas?){
            when (barType){
                BarType.DOUBLED -> {
                    canvas!!.drawPath(barPath,shadowBarPaint)
                    canvas.drawPath(rotatePath(barPath),barPaint)
                }
                BarType.SINGLE_LINE -> TODO()
            }
        }

        private fun rotatePath(path: Path):Path= Path(path).apply {
            mMatrix.reset()
            mMatrix.postRotate((180).toFloat(), xPos, height/2.toFloat())
            transform(mMatrix)
        }
    }
}