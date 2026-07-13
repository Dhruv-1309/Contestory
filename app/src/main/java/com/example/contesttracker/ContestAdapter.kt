package com.example.contesttracker

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ContestAdapter(
    private val onOpenContest: (String) -> Unit
) : RecyclerView.Adapter<ContestAdapter.ContestViewHolder>() {

    private val contests = mutableListOf<ContestModel>()
    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            notifyItemRangeChanged(0, contests.size, COUNTDOWN_PAYLOAD)
            countdownHandler.postDelayed(this, 1_000L)
        }
    }

    init {
        countdownHandler.post(countdownRunnable)
    }

    fun submitList(newContests: List<ContestModel>) {
        contests.clear()
        contests.addAll(newContests)
        notifyDataSetChanged()
    }

    fun stopCountdown() {
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contest, parent, false)
        return ContestViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContestViewHolder, position: Int) {
        holder.bind(contests[position])
    }

    override fun onBindViewHolder(holder: ContestViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(COUNTDOWN_PAYLOAD)) {
            holder.updateCountdown(contests[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = contests.size

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        stopCountdown()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    inner class ContestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val platformBadgeContainer: View = itemView.findViewById(R.id.platformBadgeContainer)
        private val platformBadgeText: TextView = itemView.findViewById(R.id.platformBadgeText)
        private val platformLogo: ImageView = itemView.findViewById(R.id.platformLogo)
        private val liveBadge: View = itemView.findViewById(R.id.liveBadge)
        private val contestNameText: TextView = itemView.findViewById(R.id.contestNameText)
        private val durationText: TextView = itemView.findViewById(R.id.durationText)
        private val timerIcon: ImageView = itemView.findViewById(R.id.timerIcon)
        private val hoursBox: TextView = itemView.findViewById(R.id.hoursBox)
        private val minutesBox: TextView = itemView.findViewById(R.id.minutesBox)
        private val secondsBox: TextView = itemView.findViewById(R.id.secondsBox)
        private val timeRangeText: TextView = itemView.findViewById(R.id.timeRangeText)
        private val openButton: MaterialButton = itemView.findViewById(R.id.openContestButton)

        fun bind(contest: ContestModel) {
            // Repository guarantees platform is non-null, but guard here as
            // belt-and-suspenders defence against any future data path change.
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
            
            val startMillis = ContestTimeUtils.startTimeMillis(contest.start) ?: 0
            val endMillis = startMillis + (contest.durationSeconds * 1000)
            val now = System.currentTimeMillis()
            
            liveBadge.isVisible = now in startMillis..endMillis
            
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            val timeLabel = if (now < startMillis) "Starts " else "Ends at "
            val targetTime = if (now < startMillis) startMillis else endMillis
            timeRangeText.text = "$timeLabel${sdf.format(Date(targetTime))}"
            
            updateCountdown(contest)

            openButton.setOnClickListener {
                contest.url?.let { onOpenContest(it) }
            }
        }

        fun updateCountdown(contest: ContestModel) {
            val startMillis = ContestTimeUtils.startTimeMillis(contest.start) ?: return
            val now = System.currentTimeMillis()
            val diff = (startMillis - now).coerceAtLeast(0)
            
            val h = TimeUnit.MILLISECONDS.toHours(diff)
            val m = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
            val s = TimeUnit.MILLISECONDS.toSeconds(diff) % 60
            
            hoursBox.text = "%02d".format(h)
            minutesBox.text = "%02d".format(m)
            secondsBox.text = "%02d".format(s)
            
            // Rotate the stopwatch icon dynamically to simulate a ticking hand (6 degrees per second)
            timerIcon.rotation = s * 6f
        }
    }

    companion object {
        private const val COUNTDOWN_PAYLOAD = "countdown"
    }
}

