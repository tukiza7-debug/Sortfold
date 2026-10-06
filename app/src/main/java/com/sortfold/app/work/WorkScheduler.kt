package com.sortfold.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sortfold.app.R
import com.sortfold.app.SortfoldApp
import com.sortfold.app.core.history.UndoManager
import com.sortfold.app.core.model.CapacityOrder
import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.core.mover.StorageSafety
import com.sortfold.app.core.rules.CapacityPacker
import com.sortfold.app.core.rules.RuleEngine
import com.sortfold.app.core.rules.SortConfig
import com.sortfold.app.core.scanner.MediaScanner
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.data.repo.UpdateRepository
import com.sortfold.app.error.ErrorRepository
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Daily check of the latest GitHub release; notification on new version. */
class UpdateCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SortfoldApp).container
        val settings = container.settingsRepository.snapshot()
        val result = container.updateRepository.check(force = false, lastCheckAt = settings.lastUpdateCheckAt)
        when (result) {
            is UpdateRepository.CheckResult.UpdateAvailable -> {
                container.settingsRepository.setLastUpdateCheckAt(System.currentTimeMillis())
                // B-14: notify once per new version — the daily run used to
                // re-notify for the same release forever.
                if (settings.notificationsEnabled && result.release.version != settings.lastNotifiedVersion) {
                    val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.notify(4242, ProgressNotifications.updateAvailable(applicationContext, result.release.version))
                    container.settingsRepository.setLastNotifiedVersion(result.release.version)
                }
            }
            is UpdateRepository.CheckResult.Failure -> {
                container.errorRepository.log(
                    module = "updater",
                    severity = ErrorRepository.Severity.INFO,
                    type = result.error.javaClass.simpleName,
                    message = "Background update check failed: ${result.error.message}",
                )
            }
            else -> {
                container.settingsRepository.setLastUpdateCheckAt(System.currentTimeMillis())
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "update-check"
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS).build(),
            )
        }

        /** The user turned automatic checks off: the scheduled work must go. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}

/**
 * Daily pass over enabled auto-sort rules: scans each watched folder, plans
 * with the rule's modes and applies the moves as an auto job.
 *
 * 1.2.0: rules can use the CAPACITY mode. The worker never restarts numbering
 * at Part 01 — existing `<prefix> NN` folders in each target group are
 * measured first and passed to the engine via existingFolderUsage, so new
 * files top up the last partial folder and continue after the highest number.
 * Files already inside part folders are never scanned (the scanner is
 * non-recursive), so they are never re-sorted.
 */
class AutoSortWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SortfoldApp).container
        val db = container.database
        val scanner = MediaScanner(applicationContext)
        val mover = Mover(applicationContext)
        val settings = container.settingsRepository.snapshot()

        for (rule in db.autoRuleDao().enabledRules()) {
            try {
                val treeUri = android.net.Uri.parse(rule.treeUri)
                val scan = scanner.scan(treeUri)
                val capacityBytes = rule.capacityBytes?.takeIf { it > 0 }
                val config = SortConfig(
                    modes = rule.modesCsv.split(',').mapNotNull { runCatching { SortMode.valueOf(it) }.getOrNull() }.toSet(),
                    dateGranularity = runCatching { DateGranularity.valueOf(rule.dateGranularity) }.getOrDefault(DateGranularity.MONTH),
                    duplicatePolicy = runCatching { DuplicatePolicy.valueOf(rule.duplicatePolicy) }.getOrDefault(DuplicatePolicy.SKIP),
                    treePath = rule.name,
                    capacityBytes = capacityBytes,
                    capacityOrder = runCatching { CapacityOrder.valueOf(rule.capacityOrder) }.getOrDefault(CapacityOrder.SEQUENTIAL),
                    capacityPrefix = CapacityPacker.sanitizePrefix(rule.capacityPrefix),
                )

                // Auto-sort top-up: measure existing part folders per group and
                // learn the real names already inside every destination folder.
                val scan1 = measureExisting(treeUri, mover, scan.files, config)
                val effectiveConfig = if (config.capacityActive) config.copy(existingFolderUsage = scan1.usage) else config
                val existingNames = if (config.capacityActive) {
                    scan1.names + plainFolderNames(treeUri, mover, scan.files, effectiveConfig)
                } else {
                    plainFolderNames(treeUri, mover, scan.files, effectiveConfig)
                }
                val plan = RuleEngine.plan(scan.files, effectiveConfig, existingNames)
                val items = plan.filter { it.action != PlanAction.SKIP_DUPLICATE }
                if (items.isEmpty()) {
                    db.autoRuleDao().setLastRun(rule.id, System.currentTimeMillis())
                    continue
                }

                // B-05: the real peak need is the largest file (zero when
                // moveDocument works), not the plan total.
                val largestFile = scan.files.maxOfOrNull { it.sizeBytes } ?: 0L
                val verdict = StorageSafety.evaluate(
                    applicationContext, treeUri, largestFile, mover.supportsMove(treeUri),
                )
                if (verdict == StorageSafety.SpaceVerdict.INSUFFICIENT) {
                    container.errorRepository.log(
                        "auto-sort", ErrorRepository.Severity.WARNING, "insufficient-storage",
                        "Rule '${rule.name}' skipped: not enough free storage for a ${largestFile}-byte file",
                    )
                    continue
                }
                val planBytes = items.sumOf { it.sizeBytes }
                val now = System.currentTimeMillis()
                val jobId = db.sortJobDao().insert(
                    SortJobEntity(
                        treeUri = rule.treeUri, destTreeUri = rule.treeUri,
                        modesCsv = rule.modesCsv, duplicatePolicy = rule.duplicatePolicy,
                        status = "RUNNING", totalFiles = items.size,
                        doneFiles = 0, totalBytes = planBytes, doneBytes = 0,
                        createdAt = now, updatedAt = now, isAuto = true,
                        capacityBytes = capacityBytes,
                    ),
                )
                db.moveLogDao().insertAll(
                    items.mapIndexed { i, p ->
                        MoveLogEntity(
                            jobId = jobId, seq = i, sourceDocId = p.documentId,
                            displayName = p.displayName, mime = p.mime,
                            destFolder = p.destinationFolder, destDocId = null,
                            destName = p.destinationName, sizeBytes = p.sizeBytes,
                            status = "PLANNED",
                            detail = when (p.action) {
                                PlanAction.RENAME -> "renamed:${p.displayName}"
                                PlanAction.REPLACE -> "replace"
                                else -> null
                            },
                        )
                    },
                )
                SortWorker.enqueue(applicationContext, jobId)
                db.autoRuleDao().setLastRun(rule.id, System.currentTimeMillis())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                container.errorRepository.log(
                    "auto-sort", ErrorRepository.Severity.ERROR,
                    e.javaClass.simpleName, "Rule '${rule.name}' failed: ${e.message}", e.stackTraceToString(),
                )
            }
        }
        return Result.success()
    }

    /** Existing part folders (usage) plus the names already inside them. */
    private class ExistingState {
        val usage = HashMap<String, Long>()
        val names = HashMap<String, Set<String>>()
    }

    /**
     * For every group path the plan would use, list existing part folders
     * (non-recursive), sum their direct file sizes and remember their names,
     * so numbering continues and collisions inside topped-up folders resolve.
     */
    private fun measureExisting(
        treeUri: android.net.Uri,
        mover: Mover,
        files: List<com.sortfold.app.core.model.MediaFile>,
        config: SortConfig,
    ): ExistingState {
        val state = ExistingState()
        if (!config.capacityActive) return state
        val prefix = CapacityPacker.sanitizePrefix(config.capacityPrefix)
        val rootDocId = runCatching { Mover.treeRootDocId(treeUri) }.getOrNull() ?: return state
        for (base in RuleEngine.baseFolders(files, config)) {
            val segments = base.split('/').filter { it.isNotEmpty() }
            val folderDocId = if (segments.isEmpty()) rootDocId else mover.resolveFolder(treeUri, rootDocId, segments) ?: continue
            for (entry in mover.listEntries(treeUri, folderDocId)) {
                if (!entry.isDirectory) continue
                if (CapacityPacker.parseIndex(entry.name, prefix) == null) continue
                val direct = mover.listEntries(treeUri, entry.docId)
                val full = if (base.isEmpty()) entry.name else "$base/${entry.name}"
                state.usage[full] = (state.usage[full] ?: 0L) + direct.filter { !it.isDirectory }.sumOf { it.sizeBytes }
                state.names[full] = direct.map { it.name }.toSet()
            }
        }
        return state
    }

    /**
     * B-01: real file names of each plain (non-part) destination folder,
     * cached per folder; folders that do not exist yet are empty.
     */
    private fun plainFolderNames(
        treeUri: android.net.Uri,
        mover: Mover,
        files: List<com.sortfold.app.core.model.MediaFile>,
        config: SortConfig,
    ): Map<String, Set<String>> {
        val out = HashMap<String, Set<String>>()
        val rootDocId = runCatching { Mover.treeRootDocId(treeUri) }.getOrNull() ?: return out
        for (base in RuleEngine.baseFolders(files, config)) {
            if (base in out) continue
            val segments = base.split('/').filter { it.isNotEmpty() }
            val folderDocId = if (segments.isEmpty()) rootDocId else mover.resolveFolder(treeUri, rootDocId, segments)
            out[base] = if (folderDocId == null) emptySet() else mover.listNames(treeUri, folderDocId)
        }
        return out
    }

    companion object {
        const val UNIQUE_NAME = "auto-sort"
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AutoSortWorker>(1, TimeUnit.DAYS).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /** One-shot run triggered by "Run now" on a rule. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_NAME-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoSortWorker>().build(),
            )
        }
    }
}

/** Central scheduling entry: called on app start and when settings change. */
object WorkScheduler {

    const val UPDATE_CHECK_NAME = UpdateCheckWorker.UNIQUE_NAME

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())

    /**
     * Applies the scheduling policy implied by the current settings.
     * Suspending and synchronous so callers (and tests) observe the result;
     * [scheduleAll] just launches it on the app scope.
     */
    suspend fun applyPolicy(context: Context, settings: com.sortfold.app.data.prefs.AppSettings) {
        ErrorRepository.CleanupWorker.schedule(context)
        if (settings.autoCheckUpdates) UpdateCheckWorker.schedule(context) else UpdateCheckWorker.cancel(context)
        if (settings.autoSortEnabled) AutoSortWorker.schedule(context) else AutoSortWorker.cancel(context)
    }

    fun scheduleAll(context: Context) {
        val settings = (context.applicationContext as SortfoldApp).container.settingsRepository
        scope.launch { applyPolicy(context, settings.snapshot()) }
    }

    /** Resumes a paused/partial job from its last completed file. */
    fun resumeJob(context: Context, jobId: Long) {
        SortWorker.enqueue(context, jobId)
    }

    /** B-07: re-queue every FAILED row of a job and run it again. */
    suspend fun retryFailed(context: Context, jobId: Long): Int {
        val container = (context.applicationContext as SortfoldApp).container
        val requeued = container.database.moveLogDao().requeueFailed(jobId)
        if (requeued > 0) SortWorker.enqueue(context, jobId)
        return requeued
    }

    fun undoLast(context: Context, jobId: Long, onDone: (UndoManager.UndoResult) -> Unit) {
        scope.launch {
            val container = (context.applicationContext as SortfoldApp).container
            val result = UndoManager(context, container.database).undoJob(jobId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(result) }
        }
    }
}
