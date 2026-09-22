package com.example.contesttracker

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * Foreground service for the audible five-minute contest alarm. It uses the
 * user's configured alarm tone, vibrates, and stops after five minutes or when
 * the user taps Stop in the persistent notification.
 */
class ContestAlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val timeout = Runnable { stopAlarm() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val contestName = intent?.getStringExtra(ContestAlarmReceiver.EXTRA_CONTEST_NAME) ?: return START_NOT_STICKY
        val platform = intent.getStringExtra(ContestAlarmReceiver.EXTRA_PLATFORM) ?: "Contest"
        val alarmId = intent.getIntExtra(ContestAlarmReceiver.EXTRA_ALARM_ID, 0)

        startForeground(alarmId, buildNotification(contestName, platform, alarmId))
        startSoundAndVibration()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, ALARM_DURATION_MS)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }

    private fun startSoundAndVibration() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)?.also { it.play() }
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 700, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun buildNotification(contestName: String, platform: String, alarmId: Int) =
        NotificationCompat.Builder(this, NotificationChannelManager.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$platform contest starts in 5 minutes")
            .setContentText(contestName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$contestName starts in 5 minutes. Tap Stop when you are ready."))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(0, "Stop", stopPendingIntent(alarmId))
            .build()

    private fun stopPendingIntent(alarmId: Int): PendingIntent = PendingIntent.getService(
        this,
        alarmId,
        Intent(this, ContestAlarmService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    companion object {
        private const val ACTION_STOP = "com.example.contesttracker.action.STOP_CONTEST_ALARM"
        private const val ALARM_DURATION_MS = 5 * 60 * 1000L
    }
}
