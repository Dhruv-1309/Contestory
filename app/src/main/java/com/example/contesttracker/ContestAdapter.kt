package com.example.contesttracker

import android.app.AlarmManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ContestAdapter(
    private val onOpenContest: (String) -> Unit,
    private val onReminderToggle: ((ContestModel, Boolean) -> Unit)? = null
) : ListAdapter<ContestModel, ContestAdapter.ContestViewHolder>(ContestDiffCallback()) {

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var isCountdownActive = false

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (isCountdownActive && currentList.isNotEmpty()) {
                notifyItemRangeChanged(0, currentList.size, COUNTDOWN_PAYLOAD)
                countdownHandler.postDelayed(this, 1_000L)
            } else {
                isCountdownActive = false
            }
        }
    }

    fun startCountdown() {
        if (!isCountdownActive && currentList.isNotEmpty()) {
            isCountdownActive = true
            countdownHandler.post(countdownRunnable)
        }
    }

    fun stopCountdown() {
        isCountdownActive = false
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    override fun submitList(list: List<ContestModel>?) {
        super.submitList(list) {
            if (isCountdownActive && currentList.isEmpty()) {
                stopCountdown()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contest, parent, false)
        return ContestViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContestViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ContestViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(COUNTDOWN_PAYLOAD)) {
            holder.updateCountdown(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        stopCountdown()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    inner class ContestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val platformBadgeContainer: View = itemView.findViewById(R.id.platformBadgeContainer)
        private val platformBadgeText: TextView = itemView.findViewById(R.id.platformBadgeText)
        private val platformLogo: ImageView = itemView.findViewById(R.id.platformLogo)
        private val liveBadge: View = itemView.findViewById(R.id.liveBadge)
        private val contestBellButton: ImageButton = itemView.findViewById(R.id.contestBellButton)
        private val contestNameText: TextView = itemView.findViewById(R.id.contestNameText)
        private val durationText: TextView = itemView.findViewById(R.id.durationText)
        private val timerIcon: ImageView = itemView.findViewById(R.id.timerIcon)
        private val hoursBox: TextView = itemView.findViewById(R.id.hoursBox)
        private val minutesBox: TextView = itemView.findViewById(R.id.minutesBox)
        private val secondsBox: TextView = itemView.findViewById(R.id.secondsBox)
        private val timeRangeText: TextView = itemView.findViewById(R.id.timeRangeText)
        private val openButton: MaterialButton = itemView.findViewById(R.id.openContestButton)

        fun bind(contest: ContestModel) {
            val platform = contest.platform ?: return
            val context = itemView.context

            platformBadgeText.text = platform.displayName
            platformLogo.imageTintList = null
            platformLogo.setImageResource(platform.logoResId)

            val (bgColor, textColor) = when (platform) {
                Platform.CODEFORCES -> R.color.platform_codeforces_bg to R.color.platform_codeforces_text
                Platform.LEETCODE   -> R.color.platform_leetcode_bg to R.color.platform_leetcode_text
                Platform.ATCODER    -> R.color.platform_atcoder_bg to R.color.platform_atcoder_text
                Platform.CODECHEF   -> R.color.platform_codechef_bg to R.color.platform_codechef_text
            }
            
            val resolvedTextColor = ContextCompat.getColor(context, textColor)
            platformBadgeContainer.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bgColor))
            platformBadgeText.setTextColor(resolvedTextColor)
            
            contestNameText.text = contest.name
            durationText.text = ContestTimeUtils.formatDuration(contest.durationSeconds)
            
            val startMillis = contest.startMillis
            val endMillis = contest.endMillis
            val now = System.currentTimeMillis()
            
            val isLive = now in startMillis..endMillis
            liveBadge.isVisible = isLive
            
            // Bell toggle on Home card for upcoming contests
            if (now < startMillis) {
                contestBellButton.isVisible = true
                val isReminderEnabled = ReminderPreferences.isEnabled(context, contest.id)
                contestBellButton.setImageResource(
                    if (isReminderEnabled) R.drawable.ic_bell_on else R.drawable.ic_bell_off
                )
                contestBellButton.setOnClickListener {
                    val newEnabled = !ReminderPreferences.isEnabled(context, contest.id)
                    if (newEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val alarmManager = context.getSystemService(AlarmManager::class.java)
                        if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                            return@setOnClickListener
                        }
                    }
                    ReminderPreferences.setEnabled(context, contest.id, newEnabled)
                    contestBellButton.setImageResource(
                        if (newEnabled) R.drawable.ic_bell_on else R.drawable.ic_bell_off
                    )
                    onReminderToggle?.invoke(contest, newEnabled)
                }
            } else {
                contestBellButton.isVisible = false
                contestBellButton.setOnClickListener(null)
            }

            val targetTime = if (now < startMillis) startMillis else endMillis
            val formattedTime = TIME_RANGE_FMT.format(Date(targetTime))
            val timeLabelRes = if (now < startMillis) R.string.starts_at else R.string.ends_at
            timeRangeText.text = context.getString(timeLabelRes, formattedTime)
            
            updateCountdown(contest)

            if (contest.url != null) {
                openButton.isVisible = true
                openButton.setOnClickListener { onOpenContest(contest.url) }
            } else {
                openButton.isVisible = false
                openButton.setOnClickListener(null)
            }
        }

        fun updateCountdown(contest: ContestModel) {
            val startMillis = contest.startMillis
            val endMillis = contest.endMillis
            val now = System.currentTimeMillis()
            
            // Live contests count down to contest end time; upcoming contests count down to start time!
            val diff = when {
                now < startMillis -> (startMillis - now).coerceAtLeast(0)
                now in startMillis..endMillis -> (endMillis - now).coerceAtLeast(0)
                else -> 0L
            }
            
            val h = TimeUnit.MILLISECONDS.toHours(diff)
            val m = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
            val s = TimeUnit.MILLISECONDS.toSeconds(diff) % 60
            
            hoursBox.text = "%02d".format(h)
            minutesBox.text = "%02d".format(m)
            secondsBox.text = "%02d".format(s)
            
            timerIcon.rotation = s * 6f
        }
    }

    companion object {
        private const val COUNTDOWN_PAYLOAD = "countdown"
        private val TIME_RANGE_FMT = SimpleDateFormat("h:mm a", Locale.getDefault())
    }
}

class ContestDiffCallback : DiffUtil.ItemCallback<ContestModel>() {
    override fun areItemsTheSame(oldItem: ContestModel, newItem: ContestModel): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ContestModel, newItem: ContestModel): Boolean {
        return oldItem == newItem
    }
}
