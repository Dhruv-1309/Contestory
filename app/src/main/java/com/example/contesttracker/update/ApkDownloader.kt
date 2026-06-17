package com.example.contesttracker.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Wraps Android's [DownloadManager] to enqueue and track APK downloads.
 *
 * The download metadata (ID, APK path, expected SHA-256) is persisted to
 * SharedPreferences so [DownloadCompleteReceiver] can retrieve it even if
 * the app was killed between download start and completion.
 */
object ApkDownloader {

    private const val DOWNLOAD_PREFS = "apk_download_prefs"

    const val KEY_DOWNLOAD_ID       = "pending_download_id"
    const val KEY_DOWNLOAD_APK_PATH = "pending_apk_path"
    const val KEY_EXPECTED_SHA256   = "expected_sha256"

    /**
     * The Intent extra key used to pass the APK file path to MainActivity
     * after a successful download + verification.
     */
    const val EXTRA_INSTALL_APK_PATH = "extra_install_apk_path"

    /**
     * Enqueues the APK download and returns the DownloadManager download ID.
     *
     * @param downloadUrl   Direct URL to the APK asset on GitHub Releases.
     * @param fileName      Local file name to save as (e.g. "CodeCountdown-v1.1.0.apk").
     * @param expectedSha256 Hex SHA-256 string from the sidecar file; empty = skip verification.
     */
    fun download(
        context: Context,
        downloadUrl: String,
        fileName: String,
        expectedSha256: String = ""
    ): Long {
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("Downloading CodeCountdown update")
            setDescription("Please wait while the update is being downloaded…")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
            )
            setAllowedOverRoaming(false)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        // Persist metadata so the BroadcastReceiver can pick it up later
        context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .putString(KEY_DOWNLOAD_APK_PATH, getApkFile(context, fileName).absolutePath)
            .putString(KEY_EXPECTED_SHA256, expectedSha256)
            .apply()

        return downloadId
    }

    /** Returns the [File] where the APK will be stored. */
    fun getApkFile(context: Context, fileName: String): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

    /** Returns persisted download metadata, or null if nothing is pending. */
    fun getPendingDownload(context: Context): Triple<Long, String, String>? {
        val prefs = context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        val id    = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        val path  = prefs.getString(KEY_DOWNLOAD_APK_PATH, null)
        val sha   = prefs.getString(KEY_EXPECTED_SHA256, "") ?: ""
        return if (id == -1L || path == null) null else Triple(id, path, sha)
    }

    /** Clears persisted download state after a download is handled. */
    fun clearDownloadState(context: Context) {
        context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_DOWNLOAD_APK_PATH)
            .remove(KEY_EXPECTED_SHA256)
            .apply()
    }
}
