package com.example.contesttracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Manages a repeating daily alarm that triggers [DailyRefreshReceiver].
 *
 * Uses [AlarmManager.setInexactRepeating] with [AlarmManager.INTERVAL_DAY] so
 * Android can batch it with other device wake-ups — this is battery-efficient
 * and still guarantees the receiver fires roughly once every 24 hours.
 *
 * Call [schedule] from:
 *  - [MainActivity.onCreate] — ensures the alarm exists while the app is in use.
 *  - [BootReceiver.onReceive] — re-registers the alarm after a device reboot
 *    (repeating alarms are cleared on reboot).
 */
object DailyRefreshScheduler {

    private const val TAG = "DailyRefreshScheduler"
    private const val REQUEST_CODE = 9_001

    /**
     * Schedules (or re-confirms) the daily refresh alarm.
     * Safe to call multiple times — [FLAG_UPDATE_CURRENT] replaces any
     * existing pending intent rather than creating a duplicate.
     */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)!!

        // First firing: 24 h from now.  Subsequent firings: every 24 h.
        val firstFireAt = System.currentTimeMillis() + AlarmManager.INTERVAL_DAY
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, firstFireAt, AlarmManager.INTERVAL_DAY, pi)

        Log.d(TAG, "Daily refresh alarm scheduled (fires in ~24 h, then every ~24 h).")
    }

    /** Cancels the daily refresh alarm (e.g. when notifications are globally disabled). */
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
