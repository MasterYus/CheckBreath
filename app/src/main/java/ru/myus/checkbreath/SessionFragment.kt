package ru.myus.checkbreath

import android.Manifest
import android.animation.LayoutTransition
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import ru.myus.checkbreath.ui.SoundView
import ru.myus.checkbreath.util.AudioCaptureService
import java.lang.Math.pow
import kotlin.math.pow
import kotlin.math.sqrt

class SessionFragment : Fragment() {
    private lateinit var mService: AudioCaptureService
    private var mBound: Boolean = false
    private lateinit var soundView: SoundView
    private lateinit var timerTxt:TextView
    private lateinit var msTxt:TextView

    private val mServerConn  = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as AudioCaptureService.ServiceBinder
            mService = binder.service
            binder.setListener(object : AudioCaptureService.BoundAudioServiceListener {
                override fun onAudioPacketRecorded(data: ByteArray,recorder:AudioRecord) {
                    //Log.e("RECORDED",data.toString())
                    updateSampleAmplitude(AudioCaptureService.getRMS(data))
                }

                override fun onTimerTick(time: Long) {
                    updateTime(time)
                }
            })
            mBound = true
            Log.e("RECORDED", "service stared")
        }

        override fun onServiceDisconnected(name:ComponentName ) {
            mBound = false
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (ContextCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (!isGranted) {
                    showPermMsg()
                }
            }.also {
                it.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_session, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.timerClickView).setOnClickListener {
            startService()
        }
        soundView = view.findViewById(R.id.soundView)
        timerTxt = view.findViewById(R.id.timer_txt)
        msTxt = view.findViewById(R.id.timer_ms)

        //NavHostFragment.findNavController(this@SessionFragment)
            //.navigate(R.id.action_FirstFragment_to_SecondFragment)
    }

    fun showPermMsg(){
        Toast.makeText(
            context,
            getString(R.string.action_require_mic),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateTime(time: Long){
        val minutes = time/600
        val seconds = (time-minutes*600)/10
        val ms = time - minutes*600 - seconds*10

        Handler(Looper.getMainLooper()).post {
            timerTxt.text = "$minutes:$seconds."
            msTxt.text = "$ms"
        }
    }

    private fun updateSampleAmplitude(data:Double){
        Handler(Looper.getMainLooper()).post {
            soundView.animateNext(
                (data/70).toFloat()
            )
        }
    }
    private fun startService(){
        if (ContextCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            showPermMsg()
        } else {
            Intent(activity, AudioCaptureService::class.java).also {
                context?.bindService(it, mServerConn, Context.BIND_AUTO_CREATE)
                context?.startService(it);
            }
        }
    }
}