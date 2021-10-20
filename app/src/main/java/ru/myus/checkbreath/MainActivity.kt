package ru.myus.checkbreath

import android.annotation.SuppressLint
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import ru.myus.checkbreath.util.AudioCaptureService
import ru.myus.checkbreath.util.AudioCaptureService.Session.Companion.getBriefString
import android.content.Intent
import android.net.Uri


class MainActivity : AppCompatActivity(),
    SessionFragment.ActivitySessionStateListener,
    ListFragment.ListFragmentCallback {

    private lateinit var modalBehavior: BottomSheetBehavior<ConstraintLayout>
    private lateinit var dimmer: View
    private lateinit var bottomList: ConstraintLayout
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fab: FloatingActionButton
    lateinit var recordIcon: AnimatedVectorDrawable
    lateinit var sessionSheetbehavior:BottomSheetBehavior<FragmentContainerView>
    lateinit var listFragment: ListFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        listFragment = findViewById<FragmentContainerView>(R.id.listFragment).getFragment()
        val bottomList = findViewById<FragmentContainerView>(R.id.backdrop)
        sessionSheetbehavior = BottomSheetBehavior.from(bottomList)
        bottomNavigationView = findViewById<BottomNavigationView>(R.id.navigation)
        val fragment = bottomList.getFragment<SessionFragment>()
        recordIcon = ContextCompat.getDrawable(this,R.drawable.ic_anim_record) as AnimatedVectorDrawable
        fab = findViewById<FloatingActionButton>(R.id.fab).also {
            it.setImageDrawable(recordIcon)
            it.setOnClickListener { view ->
                bottomNavigationView.selectedItemId = R.id.navigation_home
                fragment.toogleSession()
            }
        }
        with(sessionSheetbehavior){
            isDraggable = true
            isHideable = true
            addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback(){
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState){
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            fab.show()
                            bottomNavigationView.selectedItemId = R.id.navigation_list
                        }
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    if(sessionSheetbehavior.state == BottomSheetBehavior.STATE_EXPANDED){
                    }
                }
            })
        }
        with(bottomNavigationView){
            setOnItemSelectedListener {
                when (it.itemId){
                    R.id.navigation_home -> {
                        sessionSheetbehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        fab.hide()
                    }
                    R.id.navigation_list -> {
                        sessionSheetbehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        fab.show()
                    }
                }
                true
            }
        }
        configureModal()
    }

    fun recountSessions(){
        findViewById<TextView>(R.id.session_counter).text = listFragment.adapter.itemCount.toString()
        findViewById<TextView>(R.id.session_counter_today).text = listFragment.getTodaySessionsCount().toString()
    }


    fun configureModal(){
        bottomList = findViewById(R.id.modal)
        dimmer = findViewById(R.id.dimmer)
        dimmer.setOnTouchListener { view, motionEvent ->
            if(motionEvent.action == MotionEvent.ACTION_DOWN) {
                modalBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                dimmer.visibility = View.GONE
            }
            dimmer.performClick()
            false
        }
        modalBehavior = BottomSheetBehavior.from(bottomList)
        with(modalBehavior){
            isDraggable = false
            isHideable = true
            addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback(){
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState){
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            dimmer.visibility = View.GONE
                        }
                        BottomSheetBehavior.STATE_EXPANDED ->{
                            dimmer.visibility = View.VISIBLE
                            sessionSheetbehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        }
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) { }
            })
            state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    fun composeEmail(session: AudioCaptureService.Session) {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:")
        intent.putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.email_subject,session.name))
        intent.putExtra(Intent.EXTRA_TEXT, getBriefString(this,session));
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    fun openSessionModal(session: AudioCaptureService.Session){
        bottomList.findViewById<TextView>(R.id.session_label_modal).text = session.name
        bottomList.findViewById<ExtendedFloatingActionButton>(R.id.email_button).setOnClickListener {
            composeEmail(session)
        }
        bottomList.findViewById<TextView>(R.id.session_info_txt).text = getBriefString(this,session)
        modalBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        return if (id == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onSessionStarted(fragment: SessionFragment, service: AudioCaptureService) {
        recordIcon.start()
    }

    override fun onSessionEnd(fragment: SessionFragment) {
        recordIcon.stop()
        recordIcon.reset()
    }

    override fun onSessionSaved(session: AudioCaptureService.Session) {
        listFragment.addSession(session)
    }

    override fun onRecycleViewScrolled() {
        //sessionSheetbehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    override fun onSessionSelected(session: AudioCaptureService.Session) {
        openSessionModal(session)
    }

    override fun onSessionCountChanged() {
        recountSessions()
    }
}