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
import java.io.IOException
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
    private val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)*5
    private var status = true
    private lateinit var mBinderListener: BoundAudioServiceListener
    lateinit var mainThread:Thread;
    var sessionTimeMs:Long = 0

    private val mBinder = ServiceBinder()
    override fun onCreate() {
        super.onCreate()
        startCapture()
        startTimer()
    }

    fun startTimer(){
        Timer("SessionTimer", false).scheduleAtFixedRate( timerTask {
            sessionTimeMs += 1
            mBinderListener.onTimerTick(sessionTimeMs)
        },100,100)
    }


    private fun startCapture(){
        mainThread = Thread {
            try {
                audioBuffer = ByteArray(minBufSize);
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
                    recorder!!.read(audioBuffer!!, 0, audioBuffer!!.size);
                    mBinderListener.onAudioPacketRecorded(audioBuffer!!, recorder!!)
                }

            } catch (e: IOException) {
                Log.e("audioRecord", "IOException");
                e.printStackTrace();
            }
        }.apply { start() }
    }

    fun stopThread(){
        if (status) {
            mainThread.join()
            status = false
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
        stopThread()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopThread()
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
    }
}