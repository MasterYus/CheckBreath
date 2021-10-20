package ru.myus.checkbreath

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.*
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.internal.TextWatcherAdapter
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ru.myus.checkbreath.ui.SoundView
import ru.myus.checkbreath.util.AudioCaptureService
import kotlin.math.*

class SessionFragment : Fragment() {
    private lateinit var timerWaveView: View
    private lateinit var mService: AudioCaptureService
    private var mBound: Boolean = false
    private lateinit var soundView: SoundView
    private lateinit var timerTxt:TextView
    private lateinit var msTxt:TextView

    private var listener:ActivitySessionStateListener? = null
    private var sessionCallback:AudioCaptureService.Session.SessionStateCallback = object: AudioCaptureService.Session.SessionStateCallback{
        override fun onSessionStarted(session: AudioCaptureService.Session) { }
        override fun onSessionStopped(session: AudioCaptureService.Session) {
            showSaveDialog(session)
        }
    }

    private val mServerConn  = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as AudioCaptureService.ServiceBinder
            mService = binder.service
            mService.startSession(sessionCallback)
            if (mService.isSessionRunning) listener?.onSessionStarted(this@SessionFragment,mService)

            binder.setListener(object : AudioCaptureService.BoundAudioServiceListener {
                override fun onAudioPacketRecorded(data: ByteArray,recorder:AudioRecord) {
                    updateSampleAmplitude(AudioCaptureService.getRMS(data))
                }

                override fun onTimerTick(time: Long) {
                    updateTime(time)
                }

                override fun onSessionBreathDetected(session: AudioCaptureService.Session) {
                    requireActivity().runOnUiThread{
                        Toast.makeText(context, session.getLastBreathType().name,Toast.LENGTH_SHORT).show()
                    }
                }

            })
            mBound = true
            Log.e("RECORDED", "service stared")
        }

        override fun onServiceDisconnected(name:ComponentName ) {
            mBound = false
        }
    }

    fun showSaveDialog(session: AudioCaptureService.Session){
        val builder: AlertDialog.Builder = AlertDialog.Builder(ContextThemeWrapper(context, R.style.AlertDialogTheme))
        builder.setTitle(R.string.title_save)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.item_dialog_layout,null)
        val input = view.findViewById<TextInputLayout>(R.id.textField)
        input.hint = getString(R.string.input_name)
        val inputTxt = view.findViewById<TextInputEditText>(R.id.textInput)
        inputTxt.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(view)
        builder.setPositiveButton("OK") { dialog, which ->
            session.name = inputTxt.text.toString().trim()
            listener?.onSessionSaved(session)
        }
        builder.setNegativeButton(getText(R.string.cancel)) { dialog, which -> dialog.cancel() }
        builder.setCancelable(false)
        inputTxt.requestFocus()
        val dialog = builder.create()
        inputTxt.addTextChangedListener(object :TextWatcherAdapter(){
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = s.isNotBlank()
            }
        })
        with(dialog){
            window?.setSoftInputMode(SOFT_INPUT_STATE_VISIBLE)
            show()
            getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }

    }

    interface ActivitySessionStateListener{
        fun onSessionStarted(fragment: SessionFragment, service: AudioCaptureService)
        fun onSessionEnd(fragment: SessionFragment)
        fun onSessionSaved(session: AudioCaptureService.Session)
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
        listener = requireActivity() as MainActivity
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_session, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        timerWaveView = view.findViewById(R.id.timerClickView)
        view.findViewById<View>(R.id.timerFabView).setOnClickListener {
            toogleSession()
        }

        soundView = view.findViewById(R.id.soundView)
        timerTxt = view.findViewById(R.id.timer_txt)
        msTxt = view.findViewById(R.id.timer_ms)

    }

    fun toogleSession(){
        if(mBound && mService.isSessionRunning){
            mService.stopSession()
            soundView.flush(true)
            listener?.onSessionEnd(this)
            timerWaveView.visibility = View.INVISIBLE
        } else if (mBound && !mService.isSessionRunning){
            mService.startSession(sessionCallback)
            listener?.onSessionStarted(this, mService)
            timerWaveView.visibility = View.VISIBLE
        } else {
            startService()
            timerWaveView.visibility = View.VISIBLE
        }
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
                ((data).toFloat()/20)
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