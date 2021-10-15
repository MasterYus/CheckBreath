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
import android.view.animation.DecelerateInterpolator


class SoundView(context: Context, attributeSet: AttributeSet) : View(context,attributeSet) {
    var barNum = 60
    var sidePadding = 0.0f
    var topPadding = 0.0f
    var centerPadding = 0.0f
    val drawCenterLine = true
    var barGap = 5.0f
    var barWidth = 0.0f
    var lineWidth = 0.0f
    var barType = BarType.DOUBLED
    var customSizes = false
    var animator:ValueAnimator? = null

    val valueAnimatorDuration:Long = 700
    val nextValueAnimatorDuration:Long = 250

    private val barList = LinkedList<BarHolder>()
    private val linePaint = indicatorPaintInactive()


    enum class BarType{
        SINGLE_LINE,
        DOUBLED
    }

    /**
     * Method for adding new value line to the soundline.
     * Can be called from non-ui threads.
     * @param percentage amplitude value in range from 0 to 1
     */
    fun animateNext(percentage: Float){
        post{
            with(barList) {
                animator?.let{
                    if(it.isRunning) {
                        it.end()
                    }
                }
                add(BarHolder(barNum, BarType.DOUBLED).also {
                    it.animateToValue(percentage)
                })
                animator = ValueAnimator.ofFloat(0f,1.0f).apply {
                    duration = nextValueAnimatorDuration
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
                if (isNotEmpty() && size > barNum+1) {
                    remove(first)
                }
            }
        }
    }

    private fun indicatorPaintInactive():Paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.sound_view_indicator)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        //isDither = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if(!customSizes){
            sidePadding = 0.0f
            barWidth = (w - 2*sidePadding + barGap)/barNum.toFloat()-barGap
            topPadding = w*0.1f
            lineWidth = barWidth/2
            centerPadding = lineWidth*2
        }
        linePaint.strokeWidth = lineWidth
    }

    /**
     * Absolutely unnecessary method for initializing scope of bar lines
     * TODO: remove or implement random init
     */
    fun invalidateBars(){
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
                    if (centerPadding > 0 && drawCenterLine) it.drawLine(sidePadding,height/2.toFloat(),
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
     * Method for clearing the soundline.
     * @param withAnimation toggles flush animation.
     */
    fun flush(withAnimation: Boolean){
        animator?.end()
        if(withAnimation){
            barList.forEach{
                it.animateToValue(0.0f)
            }
        } else {
            barList.clear()
            invalidate()
        }
    }

    /**
     * Helper class for visualizing bars in sound wave.
     * @param index indicates position of line bar in a soundline diagram
     * @param barType represents type of the bar line (doubled or TODO:single)
     */
    inner class BarHolder(var index: Int, var barType: BarType):ValueAnimator.AnimatorUpdateListener, AnimatorListenerAdapter() {
        private var line:Path = Path()
        private var barPath:Path = Path()
        private val barPaint = indicatorPaintInactive()
        private val shadowBarPaint = indicatorPaintInactive()
        private val mMatrix = Matrix()

        private var oldxPos = 0f
        var xPos = 0.0f
        var value:Float = 0.0f

        private var barAnim:ValueAnimator? = null
        private lateinit var pathMeasure: PathMeasure

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
                /*barPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat()-topPadding,
                    ContextCompat.getColor(context, R.color.accent_variant),
                    ContextCompat.getColor(context, R.color.accent), Shader.TileMode.MIRROR)
                 */
            }
        }

        private fun translateX(pos:Float){
            mMatrix.reset()
            mMatrix.postTranslate(pos-xPos,0f)
            barPath.transform(mMatrix)
            line.transform(mMatrix)
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

        /**
         * Method for animating current bar line
         * @param percentage amplitude value in range from 0 to 1
         */
        fun animateToValue(percentage:Float){
            if (barAnim?.isRunning == true) {
                barAnim!!.end()
                barAnim!!.removeAllListeners()
            }
            barPath.reset()
            barAnim = ValueAnimator.ofFloat(value, percentage).apply {
                duration = valueAnimatorDuration
                interpolator = AccelerateDecelerateInterpolator()
                //interpolator = DecelerateInterpolator()
            }
            value = percentage
            barAnim?.let {
                it.addUpdateListener { animation ->
                    barPath.reset()
                    val fraction = animation.animatedValue as Float
                    pathMeasure = PathMeasure(line,false)
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

        /**
         * Method for rotating Path by 180 degrees. Used for bar lines reflection (doubling)
         * @param path path to rotate using Matrix
         */
        private fun rotatePath(path: Path):Path= Path(path).apply {
            mMatrix.reset()
            mMatrix.postRotate((180).toFloat(), xPos, height/2.toFloat())
            transform(mMatrix)
        }
    }
}