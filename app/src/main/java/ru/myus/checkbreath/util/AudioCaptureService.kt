package ru.myus.checkbreath.util

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.IBinder
import android.os.Binder
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import ru.myus.checkbreath.R
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.timerTask
import kotlin.math.pow
import kotlin.math.sqrt


class AudioCaptureService : Service() {

    private var audioBuffer: ByteArray? = null
    private var recorder:AudioRecord? = null
    private val source = MediaRecorder.AudioSource.MIC
    private val sampleRate = 16000 //41000
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val minBufSize = 8192//AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)*5
    private var status = true
    private var mBinderListener: BoundAudioServiceListener? = null
    var mainThread:Thread? = null
    lateinit var timer: Timer
    var isSessionRunning = false
    var mCurrentSession:Session? = null

    var sampleCounter = 0

    private var frame: ByteArray? = null
    var sessionTimeMs:Long = 0
    private val mBinder = ServiceBinder()

    override fun onCreate() {
        super.onCreate()
    }

    fun startSession(listener: Session.SessionStateCallback){
        if (!isSessionRunning){
            startTimer()
            status = true
            startCapture()
            mCurrentSession = Session(System.currentTimeMillis(),listener)
            isSessionRunning = true
        }
    }

    fun stopSession(){
        if(isSessionRunning){
            timer.cancel()
            stopThread()
            isSessionRunning = false
            mCurrentSession?.stop()
        }
    }

    fun startTimer(){
        sessionTimeMs = 0
        timer = Timer("SessionTimer", false).also {
            it.scheduleAtFixedRate(timerTask {
                sessionTimeMs += 1
                mBinderListener?.onTimerTick(sessionTimeMs)
            }, 100, 100)
        }
    }

    private fun startCapture(){
        tensorFlowProcessing()
    }

    fun tensorFlowProcessing(){
        val classifier = AudioClassifier.createFromFile(this, MODEL_FILE)
        val audioTensor = classifier.createInputTensorAudio()
        var mBreathing = false
        // Initialize the audio recorder
        //val RMSbuffer = ByteArray(1)
        val record = classifier.createAudioRecord()
        record.startRecording()
        mainThread = Thread {
            try {
                while (status) {
                    val startTime = System.currentTimeMillis()

                    // Load the latest audio sample
                    audioTensor.load(record)
                    val output = classifier.classify(audioTensor)

                    // Filter out results above a certain threshold, and sort them descendingly
                    val filteredModelOutput = output[0].categories.filter {
                        it.score > MINIMUM_DISPLAY_THRESHOLD
                    }.sortedBy {
                        -it.score
                    }
                    val finishTime = System.currentTimeMillis()

                    Log.d("Processing", "Latency = ${finishTime - startTime}ms")
                    Log.d("Processed", "processFrame: ${filteredModelOutput.joinToString(" ")}")
                    if(filteredModelOutput.isNotEmpty())
                    with(filteredModelOutput[0].index) {
                        mBreathing = if (this == 36 || this == 38){
                            if (!mBreathing){
                                registerBreath()
                            }
                            true
                        } else {
                            false
                        }
                    }
                        mBinderListener?.onAudioPacketRecorded(audioTensor.tensorBuffer.buffer.array(),record)
                        Thread.sleep(300)
                }
                record.release()
                classifier.close()
            } catch (e: IOException) {
                Log.e("Recognize thread", "IOException");
                e.printStackTrace();
            }
        }.apply { start() }
    }

    data class Session(var startTime:Long, val listener: SessionStateCallback?){
        
        enum class BreathType(val stringRes:Int){
            INHALE(R.string.inhale),
            EXHALE(R.string.exhale)
        }

        constructor() : this(0,null) {

        }
        
        var index:Long = 0
        var endTime:Long = 0
        val list = ArrayList<Pair<BreathType,Long>>()
        var name = ""
        
        init{
            listener?.onSessionStarted(this)
        }
        
        fun addBreath(type:BreathType,time: Long){
            list.add(Pair(type,time))
        }
        
        fun getLastBreathType():BreathType = if (list.isEmpty()) BreathType.EXHALE else list.last().first
        
        interface SessionStateCallback{
            fun onSessionStarted(session: Session)
            fun onSessionStopped(session: Session)
        }
        
        fun toJson():String{
            var json = JSONObject()
            val array = JSONArray()
            list.forEach{
                array.put(JSONObject().apply {
                    put("type",it.first.name)
                    put("time",it.second)
                })
            }
            json.put("index",index)
            json.put("start_time",startTime)
            json.put("end_time",endTime)
            json.put("name",name)
            json.put("hales",array)
            return json.toString()
        }
        
        fun stop(){
            endTime = System.currentTimeMillis()
            listener?.onSessionStopped(this)
        }

        companion object {
            fun getBriefString(context: Context,session: Session):String{
                var info = ""
                session.list.forEach { pair ->
                    info += context.getString(pair.first.stringRes)+ "\n"
                    val currIndex = session.list.indexOf(pair)
                    if( currIndex <= session.list.size - 2){
                        info += "time between: ${(session.list[currIndex+1].second - session.list[currIndex].second).toDouble()/10} seconds\n"
                    }
                }
                return info
            }
            fun fromJson(string: String):Session = Session().apply{
                with(JSONObject(string)){
                    val array = getJSONArray("hales")
                    for( i in 0 until array.length()){
                        (array[i] as JSONObject).let{
                            list.add(Pair(BreathType.valueOf(it.getString("type")),it.getLong("time")))
                        }
                    }
                    index = getLong("index")
                    startTime = getLong("start_time")
                    endTime = getLong("end_time")
                    name = getString("name")
                }
            }
        }
    }

    fun registerBreath(){
        mCurrentSession?.let{
            it.addBreath( if (it.getLastBreathType() == Session.BreathType.INHALE)
                Session.BreathType.EXHALE else Session.BreathType.INHALE,
                sessionTimeMs
            )
            mBinderListener?.onSessionBreathDetected(it)
        }
    }

    private fun stopThread(){
        if (status) {
            status = false
            mainThread?.join()
            recorder?.release()
            frame = null
            mainThread = null
        }
    }

    interface BoundAudioServiceListener {
        fun onAudioPacketRecorded(data: ByteArray,recorder:AudioRecord)
        fun onTimerTick(time:Long)
        fun onSessionBreathDetected(session: Session)
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSession()
    }

    inner class ServiceBinder : Binder() {
        val service: AudioCaptureService
            get() = this@AudioCaptureService
        fun setListener(listener: BoundAudioServiceListener) {
            mBinderListener = listener
        }
    }

    companion object {
        private const val MODEL_FILE = "yamnet.tflite"
        private const val MINIMUM_DISPLAY_THRESHOLD: Float = 0.3f

        fun getRMS(pcmData:ByteArray):Double {
            var sum = 0.0
            for(i in pcmData.indices){
                sum += pcmData[i].toDouble().pow(2)
            }
            val mAlpha = 0.9;   val mGain = 0.1;
            val rms = sqrt(sum / pcmData.size)
            return if(mGain * rms > 0.0) 20.0 * Math.log10(mGain * rms)
            else -999.99
        }
        fun toFloats(pcms: ShortArray): FloatArray? {
            val floaters = FloatArray(pcms.size)
            for (i in pcms.indices) {
                floaters[i] = pcms[i].toFloat()
            }
            return floaters
        }
        fun toShortArray(byteArray: ByteArray):ShortArray = ShortArray(byteArray.size / 2).also {
            with(ByteBuffer.wrap(byteArray)){
                asShortBuffer().get(it)
                order(ByteOrder.LITTLE_ENDIAN)
                asShortBuffer()[it]
            }
        }
    }
}