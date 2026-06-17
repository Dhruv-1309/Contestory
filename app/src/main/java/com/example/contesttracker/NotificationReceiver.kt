package com.example.contesttracker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val contestName = intent.getStringExtra("contest_name") ?: return
        val platform = intent.getStringExtra("platform") ?: "Contest"
        val url = intent.getStringExtra("url") ?: ""
        val body = intent.getStringExtra("body") ?: ""
        val notificationId = intent.getIntOf("notification_id", 0)

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val pendingIntent = PendingIntent.getActivity(
            context, 
            notificationId, 
            browserIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$platform contest starting soon!")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(ContextCompat.getColor(context, R.color.accent_purple))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    private fun Intent.getIntOf(key: String, defaultValue: Int): Int {
        return if (hasExtra(key)) getIntExtra(key, defaultValue) else defaultValue
    }
}
