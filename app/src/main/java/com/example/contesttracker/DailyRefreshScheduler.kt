package com.example.contesttracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Manages periodic background alarms that trigger [DailyRefreshReceiver].
 *
 * Runs every ~12 hours to keep contest schedules and alarms up to date,
 * even when the user has not opened the app on that day.
 *
 * Uses [AlarmManager.setAndAllowWhileIdle] on API 23+ so the device wakes
 * from Doze mode to refresh contests and alarms.
 */
object DailyRefreshScheduler {

    private const val TAG = "DailyRefreshScheduler"
    private const val REQUEST_CODE = 9_001
    private const val REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 hours

    /**
     * Schedules (or re-arms) the background refresh alarm.
     * Safe to call multiple times — [FLAG_UPDATE_CURRENT] replaces any
     * existing pending intent rather than creating a duplicate.
     */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildPendingIntent(context, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) ?: return

        val nextFireAt = System.currentTimeMillis() + REFRESH_INTERVAL_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFireAt, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, nextFireAt, pi)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule idle-allow daily refresh alarm, falling back to repeating", e)
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextFireAt, AlarmManager.INTERVAL_HALF_DAY, pi)
        }

        Log.d(TAG, "Background refresh alarm scheduled (fires in ~12 h).")
    }

    /** Cancels the daily refresh alarm (e.g. when notifications are globally disabled). */
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildPendingIntent(context, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            ?: return  // Already cancelled — nothing to do.
        am.cancel(pi)
        pi.cancel()
        Log.d(TAG, "Daily refresh alarm cancelled.")
    }

    private fun buildPendingIntent(context: Context, flags: Int): PendingIntent? {
        val intent = Intent(context, DailyRefreshReceiver::class.java)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
