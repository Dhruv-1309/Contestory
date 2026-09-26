package com.example.contesttracker

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * Foreground service for the audible five-minute contest alarm.
 *
 * Plays the device's default alarm tone in a looping [MediaPlayer] (replacing
 * the original one-shot [android.media.Ringtone] that went silent after a
 * single play), vibrates in a repeating pattern, and automatically stops after
 * five minutes or when the user taps the Stop action in the notification.
 *
 * Fixes applied:
 *  - CRITICAL: replaced Ringtone with looping MediaPlayer so the alarm
 *    actually rings for the full 5 minutes instead of stopping after one play.
 *  - CRASH: guard against alarmId == 0 (startForeground(0, …) throws on API 33+).
 *  - DEPRECATION: stopForeground(STOP_FOREGROUND_REMOVE) on API 33+.
 */
class ContestAlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val timeout = Runnable { stopAlarm() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle Stop action from the notification button.
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        // A null intent can arrive if the service is restarted by the OS after
        // being killed. Since we're START_NOT_STICKY this shouldn't happen, but
        // guard defensively so we never start a foreground service with no data.
        val contestName = intent?.getStringExtra(ContestAlarmReceiver.EXTRA_CONTEST_NAME)
            ?: run { stopSelf(); return START_NOT_STICKY }
        val platform = intent.getStringExtra(ContestAlarmReceiver.EXTRA_PLATFORM) ?: "Contest"

        // startForeground(0, …) throws IllegalArgumentException on API 33+.
        // Use a stable fallback ID if the extra is somehow missing.
        val alarmId = intent.getIntExtra(ContestAlarmReceiver.EXTRA_ALARM_ID, 0)
            .takeIf { it != 0 } ?: FALLBACK_NOTIFICATION_ID

        startForeground(alarmId, buildNotification(contestName, platform, alarmId))
        startSoundAndVibration()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, ALARM_DURATION_MS)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        releasePlayer()
        vibrator?.cancel()
        super.onDestroy()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Starts the alarm tone in a looping [MediaPlayer] and kicks off a
     * repeating vibration pattern.
     *
     * Using [MediaPlayer] instead of [android.media.Ringtone] is essential:
     * Ringtone.play() fires the sound once and returns — on most devices the
     * default alarm tone is ≤ 30 s, so the alarm went completely silent
     * shortly after starting. [MediaPlayer.isLooping] keeps it going for the
     * full five-minute window.
     */
    private fun startSoundAndVibration() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@ContestAlarmService, uri)
                isLooping = true   // ← the critical fix
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Repeating vibration: 700 ms on, 500 ms off (index 0 = repeat from start).
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
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
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contestName starts in 5 minutes. Tap Stop when you are ready.")
            )
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
        releasePlayer()
        vibrator?.cancel()
        // stopForeground(boolean) was deprecated in API 33; use the int overload.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: IllegalStateException) { /* already stopped */ }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        internal const val ACTION_STOP = "com.example.contesttracker.action.STOP_CONTEST_ALARM"
        private const val ALARM_DURATION_MS = 5 * 60 * 1000L
        private const val FALLBACK_NOTIFICATION_ID = 9_001
    }
}
