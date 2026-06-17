package com.example.contesttracker.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Installs a downloaded APK using [FileProvider] and [Intent.ACTION_VIEW].
 *
 * On Android 8+ (API 26+), checks for the REQUEST_INSTALL_PACKAGES permission
 * and guides the user to Settings if it is not granted.
 */
object ApkInstaller {

    /**
     * Attempts to install [apkFile].
     * Call this from an Activity context so system dialogs can appear.
     */
    fun install(activity: Activity, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, "Update file not found. Please try again.", Toast.LENGTH_LONG).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                showPermissionExplanationDialog(activity, apkFile)
                return
            }
        }

        launchInstaller(activity, apkFile)
    }

    // -----------------------------------------------------------------------

    private fun launchInstaller(activity: Activity, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                activity,
                "Could not open installer: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showPermissionExplanationDialog(activity: Activity, apkFile: File) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Permission Required")
            .setMessage(
                "To install updates, CodeCountdown needs the " +
                "\"Install Unknown Apps\" permission.\n\n" +
                "Tap Open Settings → enable \"Allow from this source\" → " +
                "then come back and tap Check for Updates."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
