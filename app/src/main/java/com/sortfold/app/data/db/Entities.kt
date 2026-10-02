package com.sortfold.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One sorting operation (a run of the wizard or an auto-sort rule). */
@Entity(tableName = "sort_jobs")
data class SortJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val destTreeUri: String,
    val modesCsv: String,
    val duplicatePolicy: String,
    val status: String, // PLANNED, RUNNING, PAUSED, DONE, PARTIAL, FAILED, CANCELLED, UNDOING, UNDONE
    val totalFiles: Int,
    val doneFiles: Int,
    val totalBytes: Long,
    val doneBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isAuto: Boolean = false,
    val message: String? = null,
)

/** Per-file move log; the undo stack and the job history live here. */
@Entity(tableName = "move_logs")
data class MoveLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val seq: Int,
    val sourceDocId: String,
    val displayName: String,
    val mime: String?,
    val destFolder: String,
    val destDocId: String?,      // null until moved
    val destName: String,
    val sizeBytes: Long,
    val status: String,          // PLANNED, MOVED, SKIPPED, FAILED, UNDONE
    val detail: String? = null,  // failure reason or "renamed:orig"
)

/** Entry of the Error Library. */
@Entity(tableName = "error_reports")
data class ErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val module: String,          // e.g. scanner, mover, worker, settings, ui
    val severity: String,        // INFO, WARNING, ERROR, CRASH
    val type: String,            // short machine type, e.g. SecurityException
    val message: String,
    val stackTrace: String?,
    val jobId: Long?,
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String,
)

/** A user-configured auto-sort rule. */
@Entity(tableName = "auto_rules")
data class AutoRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val treeUri: String,
    val modesCsv: String,
    val dateGranularity: String,
    val duplicatePolicy: String,
    val enabled: Boolean,
    val lastRunAt: Long? = null,
)
