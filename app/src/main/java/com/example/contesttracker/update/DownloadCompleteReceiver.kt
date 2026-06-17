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

/**
 * Manifest-registered receiver that fires when the system DownloadManager
 * finishes any download.
 *
 * Flow:
 *   1. Confirm the completed download ID matches our pending APK download.
 *   2. Verify the APK with SHA-256 (if a hash was stored).
 *   3. Post a "Ready to Install" notification whose tap action launches
 *      MainActivity with the APK path — works whether the app is in the
 *      foreground or background, and on all supported API levels.
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
        // 1. Check DownloadManager status
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
        // 2. SHA-256 verification
        // ----------------------------------------------------------------
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Toast.makeText(context, "Downloaded file not found.", Toast.LENGTH_LONG).show()
            return
        }

        if (expectedSha256.isNotBlank()) {
            val valid = SecurityVerifier.verify(apkFile, expectedSha256)
            if (!valid) {
                SecurityVerifier.deleteCorruptFile(apkFile)
                Toast.makeText(
                    context,
                    "⚠️ Security check failed: APK integrity mismatch. Download aborted.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        // ----------------------------------------------------------------
        // 3. Show "Ready to Install" notification
        // ----------------------------------------------------------------
        showInstallNotification(context, apkPath)
    }

    // -----------------------------------------------------------------------

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
