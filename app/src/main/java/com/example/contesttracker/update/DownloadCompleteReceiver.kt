package com.example.contesttracker.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manifest-registered receiver that fires when the system DownloadManager
 * finishes any download.
 *
 * Flow:
 *   1. Confirm the completed download ID matches our pending APK download.
 *   2. Extend the receiver's process lifetime with goAsync().
 *   3. On Dispatchers.IO: verify the APK with SHA-256.
 *   4. On success: post a "Ready to Install" notification.
 *   5. Always call pendingResult.finish() to release the process budget.
 *
 * SHA-256 verification reads the entire APK file and is O(file size) in both
 * time and I/O. Running it on the main thread would block the UI and risk an
 * ANR. goAsync() extends the receiver's lifecycle so this work can safely
 * run on a background dispatcher.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val pending     = ApkDownloader.getPendingDownload(context) ?: return
        val (pendingId, apkPath, expectedSha256) = pending

        if (completedId != pendingId) return   // Not our download

        ApkDownloader.clearDownloadState(context)

        // ----------------------------------------------------------------
        // 1. Check DownloadManager status (fast Cursor query — main thread ok)
        // ----------------------------------------------------------------
        val dm     = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(completedId))
        var status = DownloadManager.STATUS_FAILED
        if (cursor.moveToFirst()) {
            val col = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (col >= 0) status = cursor.getInt(col)
        }
        cursor.close()

        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            Toast.makeText(context, "Download failed. Please try again.", Toast.LENGTH_LONG).show()
            return
        }

        // ----------------------------------------------------------------
        // 2. Extend the receiver's process lifetime before any blocking work.
        //    All remaining work runs on Dispatchers.IO; pendingResult.finish()
        //    is called in the finally block regardless of outcome.
        // ----------------------------------------------------------------
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apkFile = File(apkPath)

                if (!apkFile.exists()) {
                    showToast(context, "Downloaded file not found.")
                    return@launch
                }

                // SHA-256 verification: reads the full APK file — must be off the main thread.
                // SecurityVerifier fails closed on a blank hash (enforced by ISSUE-002 fix).
                val valid = SecurityVerifier.verify(apkFile, expectedSha256)
                if (!valid) {
                    SecurityVerifier.deleteCorruptFile(apkFile)
                    showToast(context, "⚠️ Security check failed: APK integrity mismatch. Download aborted.")
                    return@launch
                }

                // Verification passed — show the install notification.
                // showInstallNotification() only touches NotificationManager, which is
                // thread-safe, so no context switch to Main is required here.
                showInstallNotification(context, apkPath)

            } finally {
                // Always release the receiver's process budget.
                // Omitting this would leak the wakelock held by goAsync().
                pendingResult.finish()
            }
        }
    }

    // -----------------------------------------------------------------------

    /**
     * Posts a "Ready to Install" notification.
     * NotificationManagerCompat.notify() is thread-safe and may be called from
     * any thread, so no dispatcher switch is needed.
     */
    private fun showInstallNotification(context: Context, apkPath: String) {
        ensureNotificationChannel(context)

        // Build PendingIntent that opens MainActivity with the APK path extra
        val launchIntent = (context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(context.packageName)
            }).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(ApkDownloader.EXTRA_INSTALL_APK_PATH, apkPath)
        }

        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent, piFlags
        )

        val notification = NotificationCompat.Builder(context, UpdateConfig.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update Ready to Install")
            .setContentText("Tap to install the latest version of CodeCountdown")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("The latest version of CodeCountdown has been downloaded and verified. Tap to install it now.")
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // On API 33+ the POST_NOTIFICATIONS permission is already declared in the manifest
        // and granted at app startup. We check at runtime to be safe.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // Fallback: launch the activity directly (works if app is in foreground)
                runCatching { context.startActivity(launchIntent) }
                return
            }
        }

        NotificationManagerCompat.from(context)
            .notify(UpdateConfig.NOTIFICATION_ID_UPDATE_READY, notification)
    }

    /**
     * Shows a Toast on the main thread. Required because Toast.makeText() must
     * be called from a Looper thread; this receiver's coroutine runs on IO.
     */
    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(UpdateConfig.NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    UpdateConfig.NOTIFICATION_CHANNEL_ID,
                    UpdateConfig.NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for available app updates"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }
}
