package com.example.contesttracker

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service for the audible five-minute contest alarm.
 *
 * Plays the device's default alarm tone in a looping [MediaPlayer], vibrates in
 * a repeating pattern that bypasses DND mode, and automatically stops after five
 * minutes or when the user taps Stop in the notification.
 */
class ContestAlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private val timeout = Runnable { stopAlarm() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle Stop action from the notification button.
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val contestName = intent?.getStringExtra(ContestAlarmReceiver.EXTRA_CONTEST_NAME)
            ?: run { stopSelf(); return START_NOT_STICKY }
        val platform = intent.getStringExtra(ContestAlarmReceiver.EXTRA_PLATFORM) ?: "Contest"
        val url = intent.getStringExtra(ContestAlarmReceiver.EXTRA_URL) ?: ""

        val alarmId = intent.getIntExtra(ContestAlarmReceiver.EXTRA_ALARM_ID, 0)
            .takeIf { it != 0 } ?: FALLBACK_NOTIFICATION_ID

        // Acquire WakeLock to keep CPU active while alarm rings
        val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Contestory:AlarmWakeLock")?.apply {
            setReferenceCounted(false)
            acquire(ALARM_DURATION_MS)
        }

        val notification = buildNotification(contestName, platform, alarmId, url)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                alarmId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(alarmId, notification)
        }

        startSoundAndVibration()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, ALARM_DURATION_MS)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        releasePlayer()
        cancelVibrator()
        releaseWakeLock()
        super.onDestroy()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun startSoundAndVibration() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@ContestAlarmService, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runCatching {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (fallbackUri != null) {
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(this@ContestAlarmService, fallbackUri)
                        isLooping = true
                        prepare()
                        start()
                    }
                }
            }
        }

        // Repeating vibration with ALARM attributes so DND doesn't suppress it
        val pattern = longArrayOf(0, 700, 500)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator ?: (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val effect = VibrationEffect.createWaveform(pattern, 0)
            vibrator?.vibrate(effect, alarmAttributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun cancelVibrator() {
        vibrator?.cancel()
    }

    private fun buildNotification(contestName: String, platform: String, alarmId: Int, url: String): Notification {
        val clickIntent = if (url.isNotBlank()) {
            val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl))
        } else {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            alarmId,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NotificationChannelManager.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ $platform contest starts in 5 minutes")
            .setContentText(contestName)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contestName starts in 5 minutes. Tap Stop when you are ready.")
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Stop", stopPendingIntent(alarmId))
            .build()
    }

    private fun stopPendingIntent(alarmId: Int): PendingIntent = PendingIntent.getService(
        this,
        alarmId,
        Intent(this, ContestAlarmService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun stopAlarm() {
        releasePlayer()
        cancelVibrator()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
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
