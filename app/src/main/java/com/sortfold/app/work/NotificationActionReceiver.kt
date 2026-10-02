package com.sortfold.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles Pause / Cancel actions on the progress notification. Both stop the
 * worker; the difference is the final job status. Moved files are always kept
 * and can be undone.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra(ProgressNotifications.EXTRA_JOB_ID, -1L)
        if (jobId <= 0) return
        val requested = when (intent.action) {
            ProgressNotifications.ACTION_PAUSE -> SortWorker.RequestedState.PAUSED
            ProgressNotifications.ACTION_CANCEL -> SortWorker.RequestedState.CANCELLED
            else -> return
        }
        SortWorker.requestStop(jobId, requested)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(SortWorker.workName(jobId))
            } finally {
                result.finish()
            }
        }
    }
}
