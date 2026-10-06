package com.sortfold.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SortJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: SortJobEntity): Long

    @Update
    suspend fun update(job: SortJobEntity)

    @Query("SELECT * FROM sort_jobs WHERE id = :id")
    suspend fun byId(id: Long): SortJobEntity?

    @Query("SELECT * FROM sort_jobs WHERE id = :id")
    fun observeById(id: Long): Flow<SortJobEntity?>

    @Query("SELECT * FROM sort_jobs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<SortJobEntity>>

    @Query("SELECT * FROM sort_jobs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SortJobEntity>

    @Query("UPDATE sort_jobs SET totalFiles = :totalFiles, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updatePlan(id: Long, totalFiles: Int, totalBytes: Long)

    /** B-13: rebuilding a preview refreshes every plan-related column. */
    @Query(
        "UPDATE sort_jobs SET totalFiles = :totalFiles, totalBytes = :totalBytes, " +
            "modesCsv = :modesCsv, duplicatePolicy = :duplicatePolicy, capacityBytes = :capacityBytes WHERE id = :id",
    )
    suspend fun updatePlanColumns(
        id: Long,
        totalFiles: Int,
        totalBytes: Long,
        modesCsv: String,
        duplicatePolicy: String,
        capacityBytes: Long?,
    )

    @Query("UPDATE sort_jobs SET status = :status, doneFiles = :doneFiles, doneBytes = :doneBytes, updatedAt = :updatedAt, message = :message WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, doneFiles: Int, doneBytes: Long, updatedAt: Long, message: String?)

    @Query("DELETE FROM sort_jobs WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}

@Dao
interface MoveLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<MoveLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MoveLogEntity): Long

    @Update
    suspend fun update(log: MoveLogEntity)

    @Query("SELECT * FROM move_logs WHERE jobId = :jobId ORDER BY seq ASC")
    suspend fun byJob(jobId: Long): List<MoveLogEntity>

    @Query("SELECT * FROM move_logs WHERE jobId = :jobId AND status = :status ORDER BY seq ASC LIMIT :limit OFFSET :offset")
    suspend fun byJobStatusPaged(jobId: Long, status: String, limit: Int, offset: Int): List<MoveLogEntity>

    /**
     * B-17 keyset paging: rows after [afterSeq] in seq order. Combined with
     * the (jobId, status) index this keeps the worker's scan O(pages).
     */
    @Query(
        "SELECT * FROM move_logs WHERE jobId = :jobId AND status = :status AND seq > :afterSeq " +
            "ORDER BY seq ASC LIMIT :limit",
    )
    suspend fun byJobStatusAfterSeq(jobId: Long, status: String, afterSeq: Int, limit: Int): List<MoveLogEntity>

    /** B-07: put every FAILED row back into the queue for a retry pass. */
    @Query("UPDATE move_logs SET status = 'PLANNED', detail = NULL WHERE jobId = :jobId AND status = 'FAILED'")
    suspend fun requeueFailed(jobId: Long): Int

    @Query("SELECT COUNT(*) FROM move_logs WHERE jobId = :jobId AND status = 'FAILED'")
    fun observeFailedCount(jobId: Long): Flow<Int>

    @Query("SELECT * FROM move_logs WHERE jobId = :jobId ORDER BY seq ASC LIMIT :limit")
    fun observeByJobPaged(jobId: Long, limit: Int): Flow<List<MoveLogEntity>>

    @Query("SELECT COUNT(*) FROM move_logs WHERE jobId = :jobId AND status = :status")
    suspend fun countByStatus(jobId: Long, status: String): Int

    @Query("UPDATE move_logs SET status = :status, detail = :detail, destDocId = :destDocId WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, destDocId: String?, detail: String?)

    /** MOVED rows also record the name actually on disk (B-08). */
    @Query("UPDATE move_logs SET status = :status, detail = :detail, destDocId = :destDocId, destName = :destName WHERE id = :id")
    suspend fun updateStatusWithDest(id: Long, status: String, destDocId: String?, destName: String, detail: String?)

    @Query("DELETE FROM move_logs WHERE jobId IN (SELECT id FROM sort_jobs WHERE createdAt < :cutoff)")
    suspend fun deleteLogsForJobsOlderThan(cutoff: Long): Int

    @Query("DELETE FROM move_logs WHERE jobId = :jobId")
    suspend fun deleteForJob(jobId: Long): Int

    @Query("DELETE FROM move_logs")
    suspend fun clearAll()
}

@Dao
interface ErrorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(error: ErrorEntity): Long

    @Query("SELECT * FROM error_reports ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ErrorEntity>>

    @Query("SELECT * FROM error_reports WHERE id = :id")
    suspend fun byId(id: Long): ErrorEntity?

    @Query("SELECT * FROM error_reports WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun since(since: Long): List<ErrorEntity>

    /** All entries identical to the given one (same module + type + message). */
    @Query(
        "SELECT * FROM error_reports WHERE module = :module AND type = :type AND message = :message " +
            "ORDER BY timestamp DESC",
    )
    fun observeOccurrences(module: String, type: String, message: String): Flow<List<ErrorEntity>>

    @Query("DELETE FROM error_reports WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM error_reports WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM error_reports")
    suspend fun clearAll(): Int
}

@Dao
interface AutoRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AutoRuleEntity): Long

    @Update
    suspend fun update(rule: AutoRuleEntity)

    @Query("DELETE FROM auto_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM auto_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<AutoRuleEntity>>

    @Query("SELECT * FROM auto_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<AutoRuleEntity>

    @Query("UPDATE auto_rules SET lastRunAt = :runAt WHERE id = :id")
    suspend fun setLastRun(id: Long, runAt: Long)
}
