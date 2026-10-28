package com.example.contesttracker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Starts the audible five-minute contest alarm scheduled by [NotificationScheduler]. */
class ContestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationChannelManager.createNotificationChannel(context)

        val contestName = intent.getStringExtra(EXTRA_CONTEST_NAME) ?: return
        val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: "Contest"
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0).takeIf { it != 0 } ?: 9_001
        val url = intent.getStringExtra(EXTRA_URL) ?: ""

        val serviceIntent = Intent(context, ContestAlarmService::class.java).apply {
            putExtra(EXTRA_CONTEST_NAME, contestName)
            putExtra(EXTRA_PLATFORM, platform)
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_URL, url)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot start ContestAlarmService in foreground ($e). Showing fallback notification.", e)
            showFallbackNotification(context, contestName, platform, alarmId, url)
        }
    }

    private fun showFallbackNotification(
        context: Context,
        contestName: String,
        platform: String,
        alarmId: Int,
        url: String
    ) {
        val clickIntent = if (url.isNotBlank()) {
            val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl))
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alarmId,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ $platform contest starts in 5 minutes!")
            .setContentText(contestName)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contestName starts in 5 minutes. Get ready!")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(ContextCompat.getColor(context, R.color.accent_purple))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(alarmId, builder.build())
    }

    companion object {
        private const val TAG = "ContestAlarmReceiver"
        const val EXTRA_CONTEST_NAME = "contest_alarm_name"
        const val EXTRA_PLATFORM = "contest_alarm_platform"
        const val EXTRA_ALARM_ID = "contest_alarm_id"
        const val EXTRA_URL = "contest_alarm_url"
    }
}
