package com.sortfold.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sortfold.app.MainActivity
import com.sortfold.app.R

object ProgressNotifications {

    const val CHANNEL_PROGRESS = "sort_progress"
    const val CHANNEL_DONE = "sort_done"
    const val CHANNEL_UPDATES = "app_updates"

    const val ACTION_PAUSE = "com.sortfold.app.action.PAUSE"
    const val ACTION_CANCEL = "com.sortfold.app.action.CANCEL"
    const val EXTRA_JOB_ID = "jobId"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun openAppIntent(context: Context, jobId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            jobId?.let { putExtra(EXTRA_JOB_ID, it) }
        }
        return PendingIntent.getActivity(
            context, (jobId ?: -1L).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(context: Context, action: String, jobId: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_JOB_ID, jobId)
        }
        return PendingIntent.getBroadcast(
            context, (action + jobId).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun progress(context: Context, jobId: Long, done: Int, total: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_sortfold)
            .setContentTitle(context.getString(R.string.notif_progress_title))
            .setContentText(
                if (total > 0) context.getString(R.string.notif_progress_x_of_y, done, total)
                else context.getString(R.string.notif_progress_working),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context, jobId))
            .setProgress(total, done, total <= 0)
            .addAction(0, context.getString(R.string.notif_action_pause), actionIntent(context, ACTION_PAUSE, jobId))
            .addAction(0, context.getString(R.string.notif_action_cancel), actionIntent(context, ACTION_CANCEL, jobId))
            .build()

    /**
     * B-03: byte-based progress with the current file name, so a long 3 GB
     * copy keeps moving in the notification. ETA appears only after 10 s.
     */
    fun progressBytes(
        context: Context,
        jobId: Long,
        bytesDone: Long,
        bytesTotal: Long,
        currentFile: String,
        eta: String?,
    ): Notification {
        val text = buildString {
            append(
                context.getString(
                    R.string.notif_progress_bytes,
                    android.text.format.Formatter.formatFileSize(context, bytesDone),
                    android.text.format.Formatter.formatFileSize(context, bytesTotal),
                ),
            )
            if (currentFile.isNotBlank()) {
                append(" \u00b7 ")
                append(currentFile)
            }
        }
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_sortfold)
            .setContentTitle(context.getString(R.string.notif_progress_title))
            .setContentText(text)
            .setSubText(eta)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context, jobId))
            .setProgress(bytesTotal.toInt(), bytesDone.toInt(), bytesTotal <= 0)
            .addAction(0, context.getString(R.string.notif_action_pause), actionIntent(context, ACTION_PAUSE, jobId))
            .addAction(0, context.getString(R.string.notif_action_cancel), actionIntent(context, ACTION_CANCEL, jobId))
            .build()
    }

    fun completed(context: Context, jobId: Long, titleRes: Int, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_sortfold)
            .setContentTitle(context.getString(titleRes))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, jobId))
            .build()

    /** "A new version is available" notice — belongs on the UPDATES channel. */
    fun updateAvailable(context: Context, version: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_sortfold)
            .setContentTitle(context.getString(R.string.notif_update_title))
            .setContentText(context.getString(R.string.notif_update_text, version))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, null))
            .build()
}
