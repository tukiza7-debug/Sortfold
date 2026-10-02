package com.sortfold.app.error

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sortfold.app.SortfoldApp
import com.sortfold.app.data.db.ErrorEntity
import com.sortfold.app.data.db.SortfoldDatabase
import java.util.concurrent.TimeUnit

/**
 * Central sink for caught errors and crashes. Everything lands in Room so the
 * in-app Error Library can show, filter, export and clean it up.
 */
class ErrorRepository(private val context: Context, private val db: SortfoldDatabase) {

    suspend fun log(
        module: String,
        severity: Severity,
        type: String,
        message: String,
        stackTrace: String? = null,
        jobId: Long? = null,
    ) {
        db.errorDao().insert(
            ErrorEntity(
                timestamp = System.currentTimeMillis(),
                module = module,
                severity = severity.name,
                type = type.take(120),
                message = message.take(4000),
                stackTrace = stackTrace?.take(16_000),
                jobId = jobId,
                appVersion = appVersion(),
                androidVersion = Build.VERSION.RELEASE ?: "?",
                deviceModel = deviceModel(),
                versionCode = versionCode(),
                buildId = com.sortfold.app.BuildConfig.GIT_SHA,
            ),
        )
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    private fun versionCode(): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }.getOrDefault(0L)

    private fun deviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    enum class Severity { INFO, WARNING, ERROR, CRASH }

    /** Daily retention cleanup: 30 days for errors, undo history uses the user setting. */
    class CleanupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val container = (applicationContext as SortfoldApp).container
            val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 86_400_000L
            container.database.errorDao().deleteOlderThan(cutoff)
            val days = container.settingsRepository.snapshot().undoRetentionDays
            val undoCutoff = System.currentTimeMillis() - days * 86_400_000L
            container.database.moveLogDao().deleteLogsForJobsOlderThan(undoCutoff)
            container.database.sortJobDao().deleteOlderThan(undoCutoff)
            return Result.success()
        }

        companion object {
            const val RETENTION_DAYS = 30
            const val UNIQUE_NAME = "error-cleanup"

            fun schedule(context: Context) {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build(),
                )
            }
        }
    }
}
