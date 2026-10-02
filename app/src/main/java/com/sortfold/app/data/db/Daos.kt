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

    @Query("UPDATE sort_jobs SET status = :status, doneFiles = :doneFiles, doneBytes = :doneBytes, updatedAt = :updatedAt, message = :message WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, doneFiles: Int, doneBytes: Long, updatedAt: Long, message: String?)

    @Query("DELETE FROM sort_jobs WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT * FROM sort_jobs ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastJob(): SortJobEntity?
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

    @Query("SELECT * FROM move_logs WHERE jobId = :jobId ORDER BY seq ASC LIMIT :limit")
    fun observeByJobPaged(jobId: Long, limit: Int): Flow<List<MoveLogEntity>>

    @Query("SELECT COUNT(*) FROM move_logs WHERE jobId = :jobId AND status = :status")
    suspend fun countByStatus(jobId: Long, status: String): Int

    @Query("UPDATE move_logs SET status = :status, detail = :detail, destDocId = :destDocId WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, destDocId: String?, detail: String?)

    @Query("DELETE FROM move_logs WHERE jobId IN (SELECT id FROM sort_jobs WHERE createdAt < :cutoff)")
    suspend fun deleteLogsForJobsOlderThan(cutoff: Long): Int

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

    @Query("DELETE FROM error_reports WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM error_reports WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM error_reports")
    suspend fun clearAll(): Int

    @Query("SELECT COUNT(*) FROM error_reports")
    suspend fun count(): Int
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

    @Query("SELECT * FROM auto_rules WHERE id = :id")
    suspend fun byId(id: Long): AutoRuleEntity?
}
