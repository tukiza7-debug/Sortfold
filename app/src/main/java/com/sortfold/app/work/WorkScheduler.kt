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
import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.core.mover.StorageSafety
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
                if (settings.notificationsEnabled) {
                    val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.notify(4242, ProgressNotifications.updateAvailable(applicationContext, result.release.version))
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
                val config = SortConfig(
                    modes = rule.modesCsv.split(',').mapNotNull { runCatching { SortMode.valueOf(it) }.getOrNull() }.toSet(),
                    dateGranularity = runCatching { DateGranularity.valueOf(rule.dateGranularity) }.getOrDefault(DateGranularity.MONTH),
                    duplicatePolicy = runCatching { DuplicatePolicy.valueOf(rule.duplicatePolicy) }.getOrDefault(DuplicatePolicy.SKIP),
                    treePath = rule.name,
                )
                val plan = RuleEngine.plan(scan.files, config)
                val items = plan.filter { it.action != com.sortfold.app.core.model.PlanAction.SKIP_DUPLICATE }
                if (items.isEmpty()) {
                    db.autoRuleDao().setLastRun(rule.id, System.currentTimeMillis())
                    continue
                }
                // Same storage gate as the manual wizard: an unattended run
                // must never fill the disk.
                val planBytes = items.sumOf { it.sizeBytes }
                if (StorageSafety.isInsufficientStorage(applicationContext, planBytes)) {
                    container.errorRepository.log(
                        "auto-sort", ErrorRepository.Severity.WARNING, "insufficient-storage",
                        "Rule '${rule.name}' skipped: not enough free storage for ${planBytes} bytes",
                    )
                    continue
                }
                val now = System.currentTimeMillis()
                val jobId = db.sortJobDao().insert(
                    SortJobEntity(
                        treeUri = rule.treeUri, destTreeUri = rule.treeUri,
                        modesCsv = rule.modesCsv, duplicatePolicy = rule.duplicatePolicy,
                        status = "RUNNING", totalFiles = items.size,
                        doneFiles = 0, totalBytes = planBytes, doneBytes = 0,
                        createdAt = now, updatedAt = now, isAuto = true,
                    ),
                )
                db.moveLogDao().insertAll(
                    items.mapIndexed { i, p ->
                        MoveLogEntity(
                            jobId = jobId, seq = i, sourceDocId = p.documentId,
                            displayName = p.displayName, mime = null,
                            destFolder = p.destinationFolder, destDocId = null,
                            destName = p.destinationName, sizeBytes = p.sizeBytes,
                            status = "PLANNED",
                            detail = when (p.action) {
                                com.sortfold.app.core.model.PlanAction.RENAME -> "renamed:${p.displayName}"
                                com.sortfold.app.core.model.PlanAction.REPLACE -> "replace"
                                else -> null
                            },
                        )
                    },
                )
                SortWorker.enqueue(applicationContext, jobId)
                db.autoRuleDao().setLastRun(rule.id, System.currentTimeMillis())
            } catch (e: Exception) {
                container.errorRepository.log(
                    "auto-sort", ErrorRepository.Severity.ERROR,
                    e.javaClass.simpleName, "Rule '${rule.name}' failed: ${e.message}", e.stackTraceToString(),
                )
            }
        }
        return Result.success()
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

    fun undoLast(context: Context, jobId: Long, onDone: (UndoManager.UndoResult) -> Unit) {
        scope.launch {
            val container = (context.applicationContext as SortfoldApp).container
            val result = UndoManager(context, container.database).undoJob(jobId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(result) }
        }
    }
}
