package com.example.contesttracker

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ScheduleAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ScheduleItem>()
    private val collapsedGroups = mutableSetOf<String>()
    private var allContests = listOf<ContestModel>()

    sealed class ScheduleItem {
        data class Header(val date: String, val isCollapsed: Boolean) : ScheduleItem()
        data class Contest(val contest: ContestModel) : ScheduleItem()
    }

    fun submitContests(contests: List<ContestModel>) {
        allContests = contests
        updateItems()
    }

    private fun updateItems() {
        items.clear()
        val groups = allContests.groupBy { 
            val millis = ContestTimeUtils.startTimeMillis(it.start) ?: 0
            val date = Date(millis)
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        }

        groups.toSortedMap().forEach { (dateKey, contestsInGroup) ->
            val displayDate = getDisplayDate(dateKey)
            val isCollapsed = collapsedGroups.contains(dateKey)
            items.add(ScheduleItem.Header(displayDate, isCollapsed))
            if (!isCollapsed) {
                contestsInGroup.forEach { items.add(ScheduleItem.Contest(it)) }
            }
        }
        notifyDataSetChanged()
    }

    private fun getDisplayDate(dateKey: String): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val tomorrow = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() + 86400000))
        return when (dateKey) {
            today -> "TODAY"
            tomorrow -> "TOMORROW"
            else -> dateKey.uppercase()
        }
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ScheduleItem.Header -> 0
        is ScheduleItem.Contest -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_group, parent, false))
        } else {
            ContestViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_contest, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is ScheduleItem.Header) {
            holder.bind(item)
        } else if (holder is ContestViewHolder && item is ScheduleItem.Contest) {
            holder.bind(item.contest)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.groupTitle)
        fun bind(header: ScheduleItem.Header) {
            title.text = header.date
            itemView.setOnClickListener {
                if (collapsedGroups.contains(header.date)) collapsedGroups.remove(header.date)
                else collapsedGroups.add(header.date)
                updateItems()
            }
        }
    }

    inner class ContestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.contestName)
        private val time: TextView = view.findViewById(R.id.contestTime)
        private val platform: TextView = view.findViewById(R.id.platformName)
        private val logo: ImageView = view.findViewById(R.id.platformLogo)

        fun bind(contest: ContestModel) {
            name.text = contest.name
            platform.text = contest.platform.displayName
            logo.imageTintList = null
            logo.setImageResource(contest.platform.logoResId)
            
            val startMillis = ContestTimeUtils.startTimeMillis(contest.start) ?: 0
            time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis))
        }
    }
}
