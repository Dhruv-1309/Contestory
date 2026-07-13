package com.example.contesttracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for both the Schedule tab and the Reminders tab.
 *
 * @param showReminderToggle When true, each contest row displays a bell
 *   [ImageButton] that lets the user opt a contest in or out of reminders.
 *   When false (default, Schedule tab), the bell is [View.GONE] and the row
 *   behaves exactly as before this feature was added.
 */
class ScheduleAdapter(
    private val showReminderToggle: Boolean = false,
    private val onContestClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ScheduleItem>()
    private val collapsedGroups = mutableSetOf<String>()
    private var allContests = listOf<ContestModel>()

    sealed class ScheduleItem {
        data class Header(val dateKey: String, val displayText: String, val isCollapsed: Boolean) : ScheduleItem()
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
            items.add(ScheduleItem.Header(dateKey, displayDate, isCollapsed))
            if (!isCollapsed) {
                contestsInGroup.forEach { items.add(ScheduleItem.Contest(it)) }
            }
        }
        notifyDataSetChanged()
    }

    private fun getDisplayDate(dateKey: String): String {
        val today    = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val tomorrow = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() + 86_400_000))
        return when (dateKey) {
            today    -> "TODAY"
            tomorrow -> "TOMORROW"
            else     -> dateKey.uppercase()
        }
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ScheduleItem.Header  -> VIEW_TYPE_HEADER
        is ScheduleItem.Contest -> VIEW_TYPE_CONTEST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_schedule_group, parent, false)
            )
        } else {
            ContestViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_schedule_contest, parent, false)
            )
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

    // ── ViewHolders ──────────────────────────────────────────────────────────

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.groupTitle)

        fun bind(header: ScheduleItem.Header) {
            title.text = header.displayText
            itemView.setOnClickListener {
                if (collapsedGroups.contains(header.dateKey)) collapsedGroups.remove(header.dateKey)
                else collapsedGroups.add(header.dateKey)
                updateItems()
            }
        }
    }

    inner class ContestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name:       TextView    = view.findViewById(R.id.contestName)
        private val time:       TextView    = view.findViewById(R.id.contestTime)
        private val platform:   TextView    = view.findViewById(R.id.platformName)
        private val logo:       ImageView   = view.findViewById(R.id.platformLogo)
        private val bellButton: ImageButton = view.findViewById(R.id.bellButton)

        fun bind(contest: ContestModel) {
            name.text = contest.name
            // Repository filters unknowns; guard here for the cached-data path.
            val p = contest.platform ?: return
            platform.text = p.displayName
            logo.imageTintList = null
            logo.setImageResource(p.logoResId)

            val startMillis = ContestTimeUtils.startTimeMillis(contest.start) ?: 0
            time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis))

            if (showReminderToggle) {
                bellButton.isVisible = true
                bindBellState(contest)
            } else {
                bellButton.isVisible = false
            }

            itemView.setOnClickListener {
                contest.url?.let { url -> onContestClick?.invoke(url) }
            }
        }

        private fun bindBellState(contest: ContestModel) {
            val context = itemView.context
            val enabled = ReminderPreferences.isEnabled(context, contest.id)
            updateBellIcon(enabled)

            bellButton.setOnClickListener {
                val newEnabled = !ReminderPreferences.isEnabled(context, contest.id)
                ReminderPreferences.setEnabled(context, contest.id, newEnabled)
                updateBellIcon(newEnabled)

                // Immediately reschedule so the alarm change takes effect without
                // waiting for the next API refresh or app restart.
                val scheduler = NotificationScheduler(context)
                val contests  = scheduler.getCachedContests()
                if (contests.isNotEmpty()) {
                    scheduler.scheduleAll(contests)
                }
            }
        }

        private fun updateBellIcon(enabled: Boolean) {
            bellButton.setImageResource(
                if (enabled) R.drawable.ic_bell_on else R.drawable.ic_bell_off
            )
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER  = 0
        private const val VIEW_TYPE_CONTEST = 1
    }
}
