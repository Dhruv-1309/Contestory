package com.example.contesttracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts the audible five-minute contest alarm scheduled by [NotificationScheduler]. */
class ContestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val contestName = intent.getStringExtra(EXTRA_CONTEST_NAME) ?: return
        val serviceIntent = Intent(context, ContestAlarmService::class.java).apply {
            putExtra(EXTRA_CONTEST_NAME, contestName)
            putExtra(EXTRA_PLATFORM, intent.getStringExtra(EXTRA_PLATFORM) ?: "Contest")
            putExtra(EXTRA_ALARM_ID, intent.getIntExtra(EXTRA_ALARM_ID, 0))
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val EXTRA_CONTEST_NAME = "contest_alarm_name"
        const val EXTRA_PLATFORM = "contest_alarm_platform"
        const val EXTRA_ALARM_ID = "contest_alarm_id"
    }
}
