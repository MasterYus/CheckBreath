package ru.myus.checkbreath.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import ru.myus.checkbreath.R
import java.util.*
import android.view.TextureView
import java.util.concurrent.ConcurrentLinkedQueue


class SoundView(context: Context, attributeSet: AttributeSet) : TextureView(context,attributeSet),TextureView.SurfaceTextureListener {
    var barNum = 50
    var sidePadding = 0.0f
    var topPadding = 0.0f
    var centerPadding = 0.0f
    var barGap = 5.0f
    var barWidth = 0.0f
    var lineWidth = 0.0f
    var barType = BarType.DOUBLED
    var customSizes = false

    @Volatile private var barList = ConcurrentLinkedQueue<BarHolder>()
    private var thread:SoundDataUpdateThread? = null
    private var drawThread:DrawThread? = null

    enum class BarType{
        SINGLE_LINE,
        DOUBLED
    }

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    private val linePaint = indicatorPaintInactive()

    fun animateNext(percentage: Float){
        thread?.animateNext(percentage)
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
        //invalidateBars()
    }

    private fun invalidateBars(){
        if(barList.isNotEmpty()) barList.clear()
        for (i in 0 until barNum){
            barList.add(BarHolder(i,barType))
        }
    }

    fun doDraw(canvas: Canvas?) {
        canvas?.let{
            canvas.drawColor(Color.WHITE,PorterDuff.Mode.CLEAR)
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
    inner class BarHolder(index:Int, var barType: BarType){
        private var line:Path = Path()
        private var barPath:Path = Path()
        private var draft:Path = Path()
        private val barPaint = indicatorPaintInactive()
        private val shadowBarPaint = indicatorPaintInactive()
        private val mMatrix = Matrix();
        private var xPos = 0.0f
        private var barAnim:ValueAnimator? = null
        private lateinit var pathMeasure: PathMeasure
        var value:Float = 0.0f

        var index = index
            set(value){
                field = value
                recomputePaths()
            }

        init {
            computeDraftDimensions()
        }

        private fun computeDraftDimensions() {
            xPos = sidePadding + (barWidth)/2 + (barWidth+barGap)*(index).toFloat()
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
            //animateToValue( 0.2f + nextFloat() * 1.0f)
            //animateToValue( 0.1f )
        }

        private fun recomputePaths(){
            val startXpos = xPos
            val endXpos = sidePadding + (barWidth)/2 + (barWidth+barGap)*(index).toFloat()
            ValueAnimator.ofFloat(startXpos,endXpos).apply {
                duration = 185
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    translateX(xPos-animation.animatedValue as Float)
                }
                startDelay = 0
                start()
            }
        }

        private fun translateX(delta: Float){
            mMatrix.reset()
            mMatrix.postTranslate(-delta,0f)
            barPath.transform(mMatrix)
            draft.transform(mMatrix)
            xPos -= delta
            //invalidate()
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
                    //invalidate()
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

    inner class DrawThread(private val surfaceTexture: SurfaceTexture, private val textureView: SoundView ) : Thread() {
        private var myThreadRun = false

        fun setRunning(b: Boolean) {
            myThreadRun = b
        }

        override fun run() {
            while (myThreadRun) {
                var c: Canvas? = null
                try {
                    c = textureView.lockCanvas(null)
                    //Log.e("DRAW","DRAW")
                    synchronized(surfaceTexture) { textureView.doDraw(c) }
                } finally {
                    if (c != null) {
                        textureView.unlockCanvasAndPost(c)
                    }
                }
            }
        }
    }

    inner class SoundDataUpdateThread(private val surfaceTexture: SurfaceTexture, soundView: SoundView ) : HandlerThread("DrawThread"),Handler.Callback {
        private val mySurfaceView: SoundView = soundView
        private lateinit var mReceiver:Handler
        var c: Canvas? = null

        //val MSG_DRAW = 100
        val MSG_NEW_DATA = 101

        override fun onLooperPrepared() {
            mReceiver = Handler(looper, this)
        }

        override fun quit(): Boolean {
            mReceiver.removeCallbacksAndMessages(null);
            return super.quit()
        }

        fun animateNext(percentage: Float){
            mReceiver.sendMessage(
                Message.obtain(mReceiver,MSG_NEW_DATA,percentage))
        }

        override fun handleMessage(msg: Message): Boolean {
            when (msg.what){
                MSG_NEW_DATA -> {
                    with(barList) {
                        if (isNotEmpty() && size >= barNum) {
                            remove()
                        }
                        forEach { barHolder ->
                            barHolder.index--
                        }
                        add(BarHolder(barNum - 1, BarType.DOUBLED).also {
                            it.animateToValue(msg.obj as Float)
                        })
                    }
                }
            }
            return true
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        drawThread = DrawThread(surface, this)
        drawThread?.setRunning(true)
        drawThread?.start()

        thread = SoundDataUpdateThread(surface, this)
        thread!!.start()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {

    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        thread?.quit()
        thread = null

        var retry = true
        drawThread?.setRunning(false)
        while (retry) {
            try {
                drawThread?.join()
                retry = false
            } catch (e: InterruptedException) {
            }
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }
}