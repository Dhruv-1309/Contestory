package com.example.contesttracker

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for both the Schedule tab and the Reminders tab.
 *
 * @param showReminderToggle When true, each contest row displays a bell
 *   [ImageButton] that lets the user opt a contest in or out of reminders.
 * @param onContestClick Invoked when a contest row is tapped.
 * @param onReminderToggle Invoked when the user toggles a contest's reminder bell.
 */
class ScheduleAdapter(
    private val showReminderToggle: Boolean = true,
    private val onContestClick: ((String) -> Unit)? = null,
    private val onReminderToggle: ((ContestModel, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ScheduleItem>()
    private val collapsedGroups = mutableSetOf<String>()
    private var allContests = listOf<ContestModel>()

    sealed class ScheduleItem {
        data class Header(val dateKey: String, val isCollapsed: Boolean) : ScheduleItem()
        data class Contest(val contest: ContestModel) : ScheduleItem()
    }

    fun submitContests(contests: List<ContestModel>) {
        allContests = contests
        updateItems()
    }

    fun refreshItems() {
        notifyDataSetChanged()
    }

    private fun updateItems() {
        val newItems = mutableListOf<ScheduleItem>()
        val groups = allContests.groupBy { contest ->
            DATE_KEY_FMT.format(Date(contest.startMillis))
        }

        groups.toSortedMap().forEach { (dateKey, contestsInGroup) ->
            val isCollapsed = collapsedGroups.contains(dateKey)
            newItems.add(ScheduleItem.Header(dateKey, isCollapsed))
            if (!isCollapsed) {
                contestsInGroup.forEach { newItems.add(ScheduleItem.Contest(it)) }
            }
        }

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val old = items[oldPos]; val new = newItems[newPos]
                return when {
                    old is ScheduleItem.Header  && new is ScheduleItem.Header  -> old.dateKey == new.dateKey
                    old is ScheduleItem.Contest && new is ScheduleItem.Contest -> old.contest.id == new.contest.id
                    else -> false
                }
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = items[oldPos] == newItems[newPos]
        })

        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    private fun getDisplayDate(context: android.content.Context, dateKey: String): String {
        val today = DATE_KEY_FMT.format(Date())
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrow = DATE_KEY_FMT.format(cal.time)
        return when (dateKey) {
            today    -> context.getString(R.string.today)
            tomorrow -> context.getString(R.string.tomorrow)
            else     -> {
                try {
                    val parsed = DATE_KEY_FMT.parse(dateKey)
                    if (parsed != null) DISPLAY_DATE_FMT.format(parsed).uppercase() else dateKey.uppercase()
                } catch (_: Exception) {
                    dateKey.uppercase()
                }
            }
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
            val context = itemView.context
            val displayText = getDisplayDate(context, header.dateKey)
            title.text = displayText
            
            itemView.contentDescription = displayText
            androidx.core.view.ViewCompat.setStateDescription(
                itemView,
                context.getString(if (header.isCollapsed) R.string.state_collapsed else R.string.state_expanded)
            )

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
            val p = contest.platform ?: return
            platform.text = p.displayName
            logo.imageTintList = null
            logo.setImageResource(p.logoResId)

            val startMillis = contest.startMillis
            time.text = TIME_FMT.format(Date(startMillis))

            val hasStarted = System.currentTimeMillis() >= startMillis
            if (showReminderToggle && !hasStarted) {
                bellButton.isVisible = true
                bindBellState(contest)
            } else {
                bellButton.isVisible = false
            }

            if (contest.url != null) {
                itemView.setOnClickListener { onContestClick?.invoke(contest.url) }
            } else {
                itemView.setOnClickListener(null)
            }
        }

        private fun bindBellState(contest: ContestModel) {
            val context = itemView.context
            val enabled = ReminderPreferences.isEnabled(context, contest.id)
            updateBellIcon(enabled)

            bellButton.setOnClickListener {
                val newEnabled = !ReminderPreferences.isEnabled(context, contest.id)
                
                if (newEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
                    if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                        return@setOnClickListener
                    }
                }

                ReminderPreferences.setEnabled(context, contest.id, newEnabled)
                updateBellIcon(newEnabled)
                onReminderToggle?.invoke(contest, newEnabled)
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

        private val DATE_KEY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val DISPLAY_DATE_FMT = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}
