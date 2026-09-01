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
        data class Header(val dateKey: String, val isCollapsed: Boolean) : ScheduleItem()
        data class Contest(val contest: ContestModel) : ScheduleItem()
    }

    fun submitContests(contests: List<ContestModel>) {
        allContests = contests
        updateItems()
    }

    private fun updateItems() {
        items.clear()
        // BUG-C3 fix: use the cached formatter from the companion object instead
        // of allocating a new SimpleDateFormat instance for every contest.
        val groups = allContests.groupBy { contest ->
            val millis = ContestTimeUtils.startTimeMillis(contest.start) ?: 0
            DATE_KEY_FMT.format(Date(millis))
        }

        groups.toSortedMap().forEach { (dateKey, contestsInGroup) ->
            val isCollapsed = collapsedGroups.contains(dateKey)
            items.add(ScheduleItem.Header(dateKey, isCollapsed))
            if (!isCollapsed) {
                contestsInGroup.forEach { items.add(ScheduleItem.Contest(it)) }
            }
        }
        notifyDataSetChanged()
    }

    private fun getDisplayDate(context: android.content.Context, dateKey: String): String {
        // BUG-C3 fix: reuse the companion-object formatter.
        // BUG-Q3 fix: use Calendar to add 1 day instead of +86_400_000 ms which
        // is wrong during DST transitions (a day can be 23 or 25 hours).
        val today = DATE_KEY_FMT.format(Date())
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        val tomorrow = DATE_KEY_FMT.format(cal.time)
        return when (dateKey) {
            today    -> context.getString(R.string.today)
            tomorrow -> context.getString(R.string.tomorrow)
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
            val context = itemView.context
            val displayText = getDisplayDate(context, header.dateKey)
            title.text = displayText
            
            // Expose collapse state for accessibility
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
            // Repository filters unknowns; guard here for the cached-data path.
            val p = contest.platform ?: return
            platform.text = p.displayName
            logo.imageTintList = null
            logo.setImageResource(p.logoResId)

            val startMillis = ContestTimeUtils.startTimeMillis(contest.start) ?: 0
            time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis))

            // BUG-U1 fix: only show the bell toggle for contests that haven't
            // started yet. Once a contest is live or has ended, all notification
            // windows (1h, 15m, start) are in the past — toggling the bell does
            // nothing because scheduleAll() skips contests where startMillis <= now.
            // Hiding it avoids a confusing non-functional button.
            val hasStarted = System.currentTimeMillis() >= startMillis
            if (showReminderToggle && !hasStarted) {
                bellButton.isVisible = true
                bindBellState(contest)
            } else {
                bellButton.isVisible = false
            }

            // BUG-U4 fix: remove click listener when URL is absent so the row
            // doesn't provide tap feedback for an action that does nothing.
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
                val context = itemView.context
                val newEnabled = !ReminderPreferences.isEnabled(context, contest.id)
                
                if (newEnabled) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
                        if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                            return@setOnClickListener // Let the user grant permission before saving
                        }
                    }
                }

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

        // BUG-C3 fix: shared formatter avoids creating a new SimpleDateFormat
        // instance for every contest in updateItems() and getDisplayDate().
        // SimpleDateFormat is not thread-safe, but ScheduleAdapter runs entirely
        // on the main thread so this is safe to share as a class-level instance.
        private val DATE_KEY_FMT = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    }
}
