package com.sortfold.app.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sortfold.app.R
import com.sortfold.app.SortfoldApp
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.error.ErrorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Executes one sort job. Long-running foreground worker (dataSync type).
 *
 * Resume model: planned moves live in Room; this worker walks them row by row
 * and persists progress after every file. Pause, cancel, app close, process
 * death or Doze can stop it at any file boundary; a fresh worker continues
 * from the first PLANNED row. A file is only ever counted after the source
 * deletion succeeded, so nothing is half-moved.
 */
class SortWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    enum class RequestedState { PAUSED, CANCELLED }

    private val container get() = (applicationContext as SortfoldApp).container
    private val db get() = container.database
    private val mover = Mover(applicationContext)
    private val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
        as android.app.NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getLong(KEY_JOB_ID, -1L)
        if (jobId <= 0) return@withContext Result.failure()
        val job = db.sortJobDao().byId(jobId)
            ?: return@withContext Result.failure()

        try {
            setForeground(foregroundInfo(jobId, job.doneFiles, job.totalFiles))
        } catch (_: Exception) {
            // Foreground can be refused (e.g. FGS restrictions); continue in background.
            container.errorRepository.log(
                "worker", ErrorRepository.Severity.WARNING, "foreground-refused",
                "Foreground service not granted for job #$jobId",
            )
        }
        db.sortJobDao().updateProgress(jobId, "RUNNING", job.doneFiles, job.doneBytes, System.currentTimeMillis(), null)

        runJob(job)
    }

    private suspend fun runJob(job: SortJobEntity): Result {
        val jobId = job.id
        val treeUri = android.net.Uri.parse(job.treeUri)
        var done = job.doneFiles
        var doneBytes = job.doneBytes
        var failed = 0

        val planned = db.moveLogDao().byJobStatusPaged(jobId, "PLANNED", Int.MAX_VALUE, 0)
        val total = job.totalFiles
        var lastNotified = 0L

        val rootDocId = runCatching { Mover.treeRootDocId(treeUri) }.getOrNull()
            ?: return finalizeFailure(jobId, "tree-unavailable")

        val folderCache = HashMap<String, String>()

        for (row in planned) {
            if (isStopped) {
                // Receiver or system stopped us. Finish this file first; the
                // loop exits at the next boundary without touching it.
                break
            }
            val segments = row.destFolder.split('/').filter { it.isNotEmpty() }
            val destFolderDocId = folderCache.getOrPut(row.destFolder) {
                runCatching { mover.ensureFolder(treeUri, rootDocId, segments) }.getOrElse { "" }
            }
            if (destFolderDocId.isEmpty()) {
                failed++
                db.moveLogDao().updateStatus(row.id, "FAILED", null, "folder-create-failed")
                continue
            }
            val outcome = runCatching {
                mover.moveOne(treeUri, row.sourceDocId, destFolderDocId, row.displayName, row.mime, actionOf(row.detail, row))
            }.getOrElse { Mover.Outcome.Failed(it.message ?: "unexpected") }

            when (outcome) {
                is Mover.Outcome.Moved -> {
                    done++
                    doneBytes += row.sizeBytes
                    db.moveLogDao().updateStatus(row.id, "MOVED", outcome.destUri.toString(), row.detail)
                }
                is Mover.Outcome.SkippedDuplicate -> {
                    done++
                    db.moveLogDao().updateStatus(row.id, "SKIPPED", null, "duplicate")
                }
                is Mover.Outcome.Failed -> {
                    failed++
                    db.moveLogDao().updateStatus(row.id, "FAILED", null, outcome.detail)
                    container.errorRepository.log(
                        "mover", ErrorRepository.Severity.ERROR, outcome.detail, "Move failed: ${row.displayName}", jobId = jobId,
                    )
                }
            }
            db.sortJobDao().updateProgress(jobId, "RUNNING", done, doneBytes, System.currentTimeMillis(), null)
            val now = System.currentTimeMillis()
            if (now - lastNotified > 250) {
                lastNotified = now
                notifyProgress(jobId, done, total)
            }
        }

        return finalize(jobId, done, doneBytes, failed, total)
    }

    private fun actionOf(detail: String?, row: MoveLogEntity): PlanAction = when {
        detail?.startsWith("renamed:") == true -> PlanAction.RENAME
        detail == "replace" -> PlanAction.REPLACE
        else -> PlanAction.MOVE
    }

    private suspend fun finalize(jobId: Long, done: Int, doneBytes: Long, failed: Int, total: Int): Result {
        val requested = requestedState.remove(jobId)
        val status = when {
            requested == RequestedState.CANCELLED -> "CANCELLED"
            requested == RequestedState.PAUSED -> "PAUSED"
            isStopped -> {
                // System stopped the worker (Doze, app kill, reboot).
                container.settingsRepository.setJobKilledBySystem(true)
                "PAUSED"
            }
            failed > 0 -> if (done + failed >= total && failed == total) "FAILED" else "PARTIAL"
            else -> "DONE"
        }
        val message = when (status) {
            "CANCELLED" -> applicationContext.getString(R.string.job_msg_cancelled)
            "PAUSED" -> applicationContext.getString(R.string.job_msg_paused)
            "PARTIAL" -> applicationContext.getString(R.string.job_msg_partial, failed)
            else -> null
        }
        db.sortJobDao().updateProgress(jobId, status, done, doneBytes, System.currentTimeMillis(), message)

        val title = when (status) {
            "DONE" -> R.string.notif_done_title
            "PARTIAL" -> R.string.notif_partial_title
            "FAILED" -> R.string.notif_failed_title
            else -> null
        }
        if (title != null) {
            val text = applicationContext.getString(R.string.notif_done_summary, done, total)
            nm.notify(jobId.toInt(), ProgressNotifications.completed(applicationContext, jobId, title, text))
        }
        return Result.success(
            workDataOf(
                KEY_JOB_ID to jobId,
                "status" to status,
                "done" to done,
                "failed" to failed,
            ),
        )
    }

    private suspend fun finalizeFailure(jobId: Long, detail: String): Result {
        db.sortJobDao().updateProgress(jobId, "FAILED", 0, 0, System.currentTimeMillis(), detail)
        container.errorRepository.log("worker", ErrorRepository.Severity.ERROR, detail, "Job #$jobId failed early: $detail", jobId = jobId)
        nm.notify(jobId.toInt(), ProgressNotifications.completed(applicationContext, jobId, R.string.notif_failed_title, detail))
        return Result.failure()
    }

    private fun notifyProgress(jobId: Long, done: Int, total: Int) {
        try {
            nm.notify(jobId.toInt(), ProgressNotifications.progress(applicationContext, jobId, done, total))
        } catch (_: SecurityException) {
            // Notification permission revoked mid-run: keep sorting silently.
        }
    }

    private fun foregroundInfo(jobId: Long, done: Int, total: Int): ForegroundInfo {
        val notification: Notification = ProgressNotifications.progress(applicationContext, jobId, done, total)
        // minSdk is 29, so the typed foreground info form is always available.
        return ForegroundInfo(jobId.toInt(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val KEY_JOB_ID = "jobId"
        private val requestedState = ConcurrentHashMap<Long, RequestedState>()

        fun requestStop(jobId: Long, state: RequestedState) {
            requestedState[jobId] = state
        }

        fun workName(jobId: Long) = "sort-job-$jobId"

        fun enqueue(context: Context, jobId: Long) {
            val request = OneTimeWorkRequestBuilder<SortWorker>()
                .setInputData(Data.Builder().putLong(KEY_JOB_ID, jobId).build())
                .addTag(SORT_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(jobId), ExistingWorkPolicy.REPLACE, request)
        }

        const val SORT_TAG = "sort-work"
    }
}
