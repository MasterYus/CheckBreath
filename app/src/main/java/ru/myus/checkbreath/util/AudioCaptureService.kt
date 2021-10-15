package ru.myus.checkbreath.util

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.IBinder
import android.os.Binder
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.thread
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
    private lateinit var mBinderListener: BoundAudioServiceListener
    var mainThread:Thread? = null
    lateinit var timer: Timer
    var isSessionRunning = false

    var sampleCounter = 0
    private var frame: ByteArray? = null

    val mfcc=MFCC(1000,sampleRate.toDouble(),12)
    val energy = Energy()

    var sessionTimeMs:Long = 0

    private val mBinder = ServiceBinder()
    override fun onCreate() {
        super.onCreate()
        startSession()
    }

    fun startSession(){
        if (!isSessionRunning){
            startTimer()
            status = true
            startCapture()
            isSessionRunning = true
        }
    }

    fun stopSession(){
        if(isSessionRunning){
            timer.cancel()
            stopThread()
            isSessionRunning = false
        }
    }

    fun startTimer(){
        sessionTimeMs = 0
        timer = Timer("SessionTimer", false).also {
            it.scheduleAtFixedRate(timerTask {
                sessionTimeMs += 1
                mBinderListener.onTimerTick(sessionTimeMs)
            }, 100, 100)
        }
    }

    private fun startCapture(){
        mainThread = Thread {
            try {
                audioBuffer = ByteArray(minBufSize)
                Log.d("audioRecord", "Buffer created of size $minBufSize");

                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBufSize
                );
                Log.d("audioRecord", "Recorder initialized");
                recorder!!.startRecording();

                while (status) {
                    recorder!!.read(audioBuffer!!, 0,minBufSize)
                    frame = if (frame == null && getRMS(audioBuffer!!) > 20){
                        audioBuffer!!.clone()
                    } else {
                        frame?.plus(audioBuffer!!)
                    }
                    frame?.let {
                        if (it.size >= 32768){
                            processFrame()
                            frame = null
                        }
                    }
                    mBinderListener.onAudioPacketRecorded(audioBuffer!!, recorder!!)
                }

            } catch (e: IOException) {
                Log.e("audioRecord", "IOException");
                e.printStackTrace();
            }
        }.apply { start() }
    }

    fun processFrame(){
        val data = toFloats(toShortArray(frame!!))
        val result = mfcc.process(data)
        Log.e("Processed", "processFrame: ${result.joinToString(" ")}")
        Log.e("Processed", "processFrame: ${energy.calcEnergy(data)}")
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
        fun getRMS(pcmData:ByteArray):Double{
            var sum = 0.0
            for(i in pcmData.indices){
                sum += pcmData[i].toDouble().pow(2)
            }
            //RMS amplitude
            return sqrt(sum / pcmData.size)
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