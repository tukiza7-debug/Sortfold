package com.sortfold.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Notification permission helper.
 *
 * 1.2.0 (B-18): the media read permissions are gone — the scanner only ever
 * reads through SAF tree grants, so READ_MEDIA_* / READ_EXTERNAL_STORAGE were
 * requested without any feature behind them. The related permission UI was
 * removed with them; folder access keeps working with zero runtime
 * permissions. POST_NOTIFICATIONS stays for progress/crash notices.
 */
object MediaPermissions {

    fun notificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

    fun hasNotifications(context: Context): Boolean {
        val p = notificationPermission() ?: return true
        return granted(context, p)
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
