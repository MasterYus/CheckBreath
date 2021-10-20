package ru.myus.checkbreath

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import ru.myus.checkbreath.R
import ru.myus.checkbreath.ui.SessionListAdapter
import ru.myus.checkbreath.ui.SessionListAdapter.Companion.formatTime
import ru.myus.checkbreath.ui.SwipeToDeleteCallback
import ru.myus.checkbreath.util.AudioCaptureService
import ru.myus.checkbreath.util.TinyDB
import java.util.*
import java.util.Collections.sort
import kotlin.collections.ArrayList

class ListFragment : Fragment() {
    private lateinit var behavior: BottomSheetBehavior<ConstraintLayout>
    var callback:ListFragmentCallback? = null
    val sessionList = ArrayList<AudioCaptureService.Session>()
    lateinit var adapter:SessionListAdapter
    lateinit var tinyDB: TinyDB

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_list, container, false)
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as ListFragmentCallback
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tinyDB = TinyDB(requireContext())
        updateList()
        val dimmer = view.findViewById<View>(R.id.dimmer)
        adapter = SessionListAdapter( this@ListFragment.requireActivity().window.decorView.findViewById(R.id.main_root),sessionList)
        with(view.findViewById<RecyclerView>(R.id.list_recycler)) {
            this@ListFragment.adapter.listener = View.OnClickListener {
                val itemPosition: Int = getChildLayoutPosition(it)
                callback?.onSessionSelected(sessionList[itemPosition])
            }
            layoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)
            adapter = this@ListFragment.adapter
            ItemTouchHelper(SwipeToDeleteCallback(this.context,adapter as SessionListAdapter)).attachToRecyclerView(this)
            addOnScrollListener(object: RecyclerView.OnScrollListener(){
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    callback?.onRecycleViewScrolled()
                }
            })
        }
        adapter.registerAdapterDataObserver(object :RecyclerView.AdapterDataObserver(){
            override fun onChanged() {
                super.onChanged()
                callback?.onSessionCountChanged()
            }
        })
        callback?.onSessionCountChanged()
    }

    fun updateList(){
        if(sessionList.isNotEmpty()) sessionList.clear()
        tinyDB.getListString(sessionsKey).forEach {
            sessionList.add(AudioCaptureService.Session.fromJson(it))
        }
        sort(sessionList, compareBy{-it.startTime})
    }

    fun getTodaySessionsCount():Int{
        return sessionList.filter { formatTime(it.startTime,"dd.MM.yyyy") ==
                formatTime(System.currentTimeMillis(),"dd.MM.yyyy") }.size
    }

    fun saveList(){
        if(sessionList.isNotEmpty()){
            val array = arrayListOf<String>()
            sessionList.forEach {
                array.add(it.toJson())
            }
            tinyDB.putListString(sessionsKey,array)
        }
    }

    fun addSession(session:AudioCaptureService.Session){
        sessionList.add(0,session)
        adapter.notifyItemInserted(0)
        callback?.onSessionCountChanged()
    }

    override fun onPause() {
        saveList()
        super.onPause()
    }

    interface ListFragmentCallback{
        fun onRecycleViewScrolled()
        fun onSessionSelected(session: AudioCaptureService.Session)
        fun onSessionCountChanged()
    }

    companion object{
        final val sessionsKey = "sessions"
    }
}