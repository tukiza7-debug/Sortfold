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
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.error.ErrorRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Executes one sort job. Long-running foreground worker (dataSync type).
 *
 * Resume model: planned moves live in Room; this worker walks them row by row
 * (keyset-paged, B-17) and persists progress after every file. A row is marked
 * IN_FLIGHT with its planned destination name before the move starts, so a
 * process death mid-file is reconciled on the next run (B-11).
 *
 * Stop model (B-02): pause/cancel set a cooperative flag and the current file
 * finishes and is logged before the loop stops — the coroutine is not killed
 * mid-file by the app. CancellationException is never swallowed; per-file DB
 * writes and finalize run in NonCancellable, and no job is ever left RUNNING.
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
        } finally {
            withContext(NonCancellable) {
                // B-02: a cancellation before/inside runJob must still leave a
                // terminal row state — never a job stuck as RUNNING.
                val j = db.sortJobDao().byId(jobId)
                if (j != null && j.status == "RUNNING") {
                    val requested = requestedState.remove(jobId)
                    val status = if (requested == RequestedState.CANCELLED) "CANCELLED" else "PAUSED"
                    if (requested == null) {
                        // No user request behind this stop: the system killed us
                        // (Doze, app kill, FGS dataSync timeout — B-18).
                        container.settingsRepository.setJobKilledBySystem(true)
                    }
                    val message = if (requested == null) {
                        applicationContext.getString(R.string.job_msg_system_timeout)
                    } else {
                        applicationContext.getString(R.string.job_msg_paused)
                    }
                    db.sortJobDao().updateProgress(
                        jobId, status, j.doneFiles, j.doneBytes,
                        System.currentTimeMillis(), message,
                    )
                } else {
                    requestedState.remove(jobId)
                }
            }
        }
    }

    /**
     * Android 15+ caps dataSync foreground services at ~6 h per day. When the
     * window expires the system stops the worker (onStopped -> isStopped),
     * which this class resolves to PAUSED with the "paused by system"
     * message and the existing one-tap resume flow (B-18).
     */

    private suspend fun runJob(job: SortJobEntity): Result {
        val jobId = job.id
        val treeUri = android.net.Uri.parse(job.treeUri)
        var done = 0
        var doneBytes = job.doneBytes
        // B-07: rows that already failed in a previous run count from the DB,
        // otherwise a resumed job finishes DONE with failed files.
        var failed = db.moveLogDao().countByStatus(jobId, "FAILED")
        val total = job.totalFiles

        // B-11: finish or roll back rows the last run left IN_FLIGHT.
        reconcileInFlight(treeUri, jobId)

        var lastNotified = 0L
        var lastDbProgress = 0L
        // B-03: byte-accurate progress and ETA (after 10 s of data).
        var copyStartedAt = 0L
        var etaText: String? = null

        val rootDocId = runCatching { Mover.treeRootDocId(treeUri) }.getOrNull()
            ?: return finalizeFailure(jobId, "tree-unavailable")

        val folderCache = HashMap<String, String>()
        val policy = runCatching { DuplicatePolicy.valueOf(job.duplicatePolicy) }.getOrDefault(DuplicatePolicy.SKIP)
        // B-06: one name set per destination folder, loaded once and kept
        // up-to-date after every move — never a list per file (O(n²) gone).
        val nameCache = HashMap<String, MutableSet<String>>()
        fun namesFor(folderDocId: String): MutableSet<String> =
            nameCache.getOrPut(folderDocId) { mover.listNames(treeUri, folderDocId).toMutableSet() }

        // B-17: keyset-paged walk over the PLANNED queue. The cursor starts at
        // -1 because the keyset is EXCLUSIVE — starting at 0 would silently
        // skip the very first planned row.
        var lastSeq = -1
        loop@ while (true) {
            val page = db.moveLogDao().byJobStatusAfterSeq(jobId, "PLANNED", lastSeq, PAGE_SIZE)
            if (page.isEmpty()) break
            for (row in page) {
                lastSeq = row.seq
                // B-02: cooperative stop — the current file finished; stop here.
                if (requestedState.containsKey(jobId) || isStopped) break@loop

                val segments = row.destFolder.split('/').filter { it.isNotEmpty() }
                val destFolderDocId = folderCache.getOrPut(row.destFolder) {
                    runCatching { mover.ensureFolder(treeUri, rootDocId, segments) }.getOrElse { "" }
                }
                if (destFolderDocId.isEmpty()) {
                    failed++
                    db.moveLogDao().updateStatus(row.id, "FAILED", null, "folder-create-failed")
                    continue
                }

                // B-11: claim the row before touching the provider, so a crash
                // mid-file is reconcilable instead of looking like a duplicate.
                db.moveLogDao().updateStatus(row.id, "IN_FLIGHT", null, null)

                var currentFileCopied = 0L
                val outcome = try {
                    mover.moveOne(
                        treeUri, row.sourceDocId, destFolderDocId, row.displayName, row.mime,
                        actionOf(row.detail, policy),
                        existingNames = namesFor(destFolderDocId),
                        progress = { copied ->
                            currentFileCopied = copied
                            val now = System.currentTimeMillis()
                            if (now - lastDbProgress > 250) {
                                lastDbProgress = now
                                if (copyStartedAt == 0L) copyStartedAt = now
                                val bytesDone = doneBytes + currentFileCopied
                                etaText = etaIfReady(now - copyStartedAt, bytesDone, job.totalBytes)
                                withContext(NonCancellable) {
                                    db.sortJobDao().updateProgress(jobId, "RUNNING", done, bytesDone, now, null)
                                }
                            }
                        },
                    )
                } catch (ce: CancellationException) {
                    // B-02/B-03: never swallow cancellation; Mover already
                    // removed the partial destination in NonCancellable. The
                    // row stays IN_FLIGHT and the next run reconciles it.
                    throw ce
                } catch (e: Exception) {
                    Mover.Outcome.Failed(e.message ?: "unexpected")
                }

                when (outcome) {
                    is Mover.Outcome.Moved -> {
                        done++
                        doneBytes += row.sizeBytes
                        namesFor(destFolderDocId) += outcome.destName
                        nameCache[Mover.parentDocIdOf(row.sourceDocId)]?.remove(row.displayName)
                        // B-10: a REPLACE that really destroyed a file is
                        // recorded as such — undo knows the original is gone.
                        val loggedDetail = if (row.detail == "replace") "replaced:${row.displayName}" else row.detail
                        withContext(NonCancellable) {
                            // The provider may have renamed the file; the log
                            // must show the name that is actually on disk.
                            db.moveLogDao().updateStatusWithDest(
                                row.id, "MOVED", outcome.destUri.toString(), outcome.destName, loggedDetail,
                            )
                        }
                    }
                    is Mover.Outcome.SkippedDuplicate -> {
                        done++
                        withContext(NonCancellable) {
                            db.moveLogDao().updateStatus(row.id, "SKIPPED", null, "duplicate")
                        }
                    }
                    is Mover.Outcome.Failed -> {
                        failed++
                        withContext(NonCancellable) {
                            db.moveLogDao().updateStatus(row.id, "FAILED", null, outcome.detail)
                        }
                        container.errorRepository.log(
                            "mover", ErrorRepository.Severity.ERROR, outcome.detail, "Move failed: ${row.displayName}", jobId = jobId,
                        )
                    }
                }
                val now = System.currentTimeMillis()
                withContext(NonCancellable) {
                    db.sortJobDao().updateProgress(jobId, "RUNNING", done, doneBytes, now, null)
                }
                if (now - lastNotified > 250) {
                    lastNotified = now
                    notifyProgress(jobId, doneBytes + currentFileCopied, job.totalBytes, row.displayName, etaText)
                }
            }
            if (page.size < PAGE_SIZE) break
        }

        return finalize(jobId, done, doneBytes, failed, total)
    }

    /** ETA text once 10 s of data has flowed; null before that (B-03). */
    private fun etaIfReady(elapsedMs: Long, bytesDone: Long, totalBytes: Long): String? {
        if (elapsedMs < 10_000 || bytesDone <= 0 || totalBytes <= 0) return null
        val rate = bytesDone.toDouble() / (elapsedMs / 1000.0)
        if (rate <= 0) return null
        val remaining = (totalBytes - bytesDone).coerceAtLeast(0)
        if (remaining == 0L) return null
        val seconds = (remaining / rate).toLong()
        val mins = seconds / 60
        val hours = mins / 60
        return when {
            hours >= 1 -> applicationContext.getString(R.string.eta_hours, hours, mins % 60)
            mins >= 1 -> applicationContext.getString(R.string.eta_minutes, mins)
            else -> applicationContext.getString(R.string.eta_seconds, seconds)
        }
    }

    /**
     * B-11: rows left IN_FLIGHT by a dead process. Compare the planned
     * destination with the source: equal sizes -> finish the delete; a partial
     * or missing destination -> clean it and put the row back in the queue.
     */
    private suspend fun reconcileInFlight(treeUri: android.net.Uri, jobId: Long) {
        val rows = db.moveLogDao().byJobStatusPaged(jobId, "IN_FLIGHT", Int.MAX_VALUE, 0)
        if (rows.isEmpty()) return
        val rootDocId = runCatching { Mover.treeRootDocId(treeUri) }.getOrNull() ?: return
        for (row in rows) {
            val segments = row.destFolder.split('/').filter { it.isNotEmpty() }
            val folderDocId = runCatching { mover.resolveFolder(treeUri, rootDocId, segments) }.getOrNull()
            val child = folderDocId?.let { mover.findChild(treeUri, it, row.destName) }
            val sourceSize = mover.documentSize(treeUri, row.sourceDocId)
            when {
                child == null -> db.moveLogDao().updateStatus(row.id, "PLANNED", null, null)
                sourceSize >= 0 && child.sizeBytes == sourceSize -> {
                    // The copy finished; only the source delete is missing.
                    val deleted = mover.deleteDocument(treeUri, row.sourceDocId)
                    if (deleted) {
                        db.moveLogDao().updateStatus(
                            row.id, "MOVED",
                            documentUriString(treeUri, child.docId), null,
                        )
                    } else {
                        db.moveLogDao().updateStatus(row.id, "PLANNED", null, null)
                    }
                }
                sourceSize < 0 && child.sizeBytes == row.sizeBytes -> {
                    // Source already gone and the copy is complete: it moved.
                    db.moveLogDao().updateStatus(
                        row.id, "MOVED",
                        documentUriString(treeUri, child.docId), null,
                    )
                }
                else -> {
                    // Partial copy: remove and re-queue.
                    mover.deleteDocument(treeUri, child.docId)
                    db.moveLogDao().updateStatus(row.id, "PLANNED", null, null)
                }
            }
        }
    }

    private fun documentUriString(treeUri: android.net.Uri, docId: String): String =
        android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()

    /**
     * B-01: the planned detail is the primary signal, but the job's duplicate
     * policy is the safety net — a row planned as plain MOVE under a REPLACE
     * policy must still replace when a new file appeared in the folder.
     */
    private fun actionOf(detail: String?, policy: DuplicatePolicy): PlanAction = when {
        detail?.startsWith("renamed:") == true -> PlanAction.RENAME
        detail == "replace" || detail == "replaces" -> PlanAction.REPLACE
        else -> when (policy) {
            DuplicatePolicy.RENAME -> PlanAction.RENAME
            DuplicatePolicy.REPLACE -> PlanAction.REPLACE
            DuplicatePolicy.SKIP -> PlanAction.MOVE
        }
    }

    /**
     * Terminal state. B-07: done/failed are computed from the database, not
     * only from counters — resumed jobs report the truth.
     */
    private suspend fun finalize(jobId: Long, done: Int, doneBytes: Long, failed: Int, total: Int): Result {
        val dbDone = db.moveLogDao().countByStatus(jobId, "MOVED") + db.moveLogDao().countByStatus(jobId, "SKIPPED")
        val dbFailed = db.moveLogDao().countByStatus(jobId, "FAILED")
        val requested = requestedState.remove(jobId)
        val status = when {
            requested == RequestedState.CANCELLED -> "CANCELLED"
            requested == RequestedState.PAUSED -> "PAUSED"
            isStopped -> {
                // System stopped the worker (Doze, app kill, FGS timeout).
                container.settingsRepository.setJobKilledBySystem(true)
                "PAUSED"
            }
            dbFailed > 0 -> if (dbDone + dbFailed >= total && dbFailed >= total) "FAILED" else "PARTIAL"
            else -> "DONE"
        }
        val message = when (status) {
            "CANCELLED" -> applicationContext.getString(R.string.job_msg_cancelled)
            "PAUSED" -> applicationContext.getString(R.string.job_msg_paused)
            "PARTIAL" -> applicationContext.getString(R.string.job_msg_partial, dbFailed)
            else -> null
        }
        withContext(NonCancellable) {
            db.sortJobDao().updateProgress(jobId, status, dbDone, doneBytes, System.currentTimeMillis(), message)
        }

        val title = when (status) {
            "DONE" -> R.string.notif_done_title
            "PARTIAL" -> R.string.notif_partial_title
            "FAILED" -> R.string.notif_failed_title
            else -> null
        }
        if (title != null) {
            val text = applicationContext.getString(R.string.notif_done_summary, dbDone, total)
            nm.notify(jobId.toInt(), ProgressNotifications.completed(applicationContext, jobId, title, text))
        }
        return Result.success(
            workDataOf(
                KEY_JOB_ID to jobId,
                "status" to status,
                "done" to dbDone,
                "failed" to dbFailed,
            ),
        )
    }

    private suspend fun finalizeFailure(jobId: Long, detail: String): Result {
        db.sortJobDao().updateProgress(jobId, "FAILED", 0, 0, System.currentTimeMillis(), detail)
        container.errorRepository.log("worker", ErrorRepository.Severity.ERROR, detail, "Job #$jobId failed early: $detail", jobId = jobId)
        nm.notify(jobId.toInt(), ProgressNotifications.completed(applicationContext, jobId, R.string.notif_failed_title, detail))
        return Result.failure()
    }

    /** B-03: progress is byte-based plus the current file name, ETA after 10 s. */
    private suspend fun notifyProgress(jobId: Long, bytesDone: Long, bytesTotal: Long, currentFile: String, eta: String?) {
        if (!container.settingsRepository.snapshot().notificationsEnabled) return
        try {
            nm.notify(
                jobId.toInt(),
                ProgressNotifications.progressBytes(applicationContext, jobId, bytesDone, bytesTotal, currentFile, eta),
            )
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
        const val PAGE_SIZE = 500
        private val requestedState = ConcurrentHashMap<Long, RequestedState>()

        /** B-02: cooperative stop — no coroutine is cancelled mid-file. */
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
