package ru.myus.checkbreath.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.myus.checkbreath.R

import com.google.android.material.snackbar.Snackbar
import ru.myus.checkbreath.ListFragment
import ru.myus.checkbreath.util.AudioCaptureService
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*
import kotlin.collections.ArrayList

class SessionListAdapter(private val view:View,private val sessions: ArrayList<AudioCaptureService.Session>) :
    RecyclerView.Adapter<SessionListAdapter.SessionViewHolder>() {

    var listener:View.OnClickListener? = null
    private lateinit var mRecentlyDeletedItem: AudioCaptureService.Session
    private var mRecentlyDeletedItemPosition: Int = 0

    fun deleteItem(position: Int) {
        mRecentlyDeletedItem = sessions.get(position)
        mRecentlyDeletedItemPosition = position
        sessions.removeAt(position)
        notifyItemRemoved(position)
        showUndoSnackbar()
    }

    private fun showUndoSnackbar() {
        val snackbar: Snackbar = Snackbar.make(
            view, R.string.snack_bar_text,
            Snackbar.LENGTH_LONG
        )
        snackbar.setAction(R.string.snack_bar_undo) { v -> undoDelete() }
        snackbar.show()
    }

    private fun undoDelete() {
        sessions.add(
            mRecentlyDeletedItemPosition,
            mRecentlyDeletedItem
        )
        notifyItemInserted(mRecentlyDeletedItemPosition)
    }

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var nameTextView: TextView? = null
        var dateTextView: TextView? = null
        var timeTextView: TextView? = null

        init {
            nameTextView = itemView.findViewById(R.id.item_title_name)
            dateTextView = itemView.findViewById(R.id.item_title_date)
            timeTextView = itemView.findViewById(R.id.item_title_time)
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val itemView =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.session_list_item, parent, false)
        itemView.setOnClickListener(listener)
        return SessionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        with(sessions[position]){
            holder.nameTextView?.text = this.name
            holder.dateTextView?.text = formatTime(this.startTime,"dd.MM.yyyy HH:mm")
            holder.timeTextView?.text = formatTime(this.endTime - this.startTime,"mm:ss")
        }
    }



    override fun getItemCount() = sessions.size
    companion object{
        fun formatTime(time:Long, pattern: String):String = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
    }
}