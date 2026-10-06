package com.sortfold.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sortfold.app.data.db.SortfoldDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * 1.2.0 Part A: Room v2 -> v3 migration. Opens a database shaped exactly like
 * the v2 schema with raw SQL, seeds user data, then lets Room run
 * MIGRATION_2_3 and verifies the data survived and the new columns/index work.
 * Upgrades must never be destructive.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DbMigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("migration-test.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    /** Raw SQL schema identical to the v2 entities (no capacity/index additions). */
    private fun createV2Schema(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sort_jobs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "treeUri TEXT NOT NULL, destTreeUri TEXT NOT NULL, modesCsv TEXT NOT NULL, " +
                "duplicatePolicy TEXT NOT NULL, status TEXT NOT NULL, totalFiles INTEGER NOT NULL, " +
                "doneFiles INTEGER NOT NULL, totalBytes INTEGER NOT NULL, doneBytes INTEGER NOT NULL, " +
                "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isAuto INTEGER NOT NULL, " +
                "message TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS move_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, jobId INTEGER NOT NULL, seq INTEGER NOT NULL, " +
                "sourceDocId TEXT NOT NULL, displayName TEXT NOT NULL, mime TEXT, destFolder TEXT NOT NULL, " +
                "destDocId TEXT, destName TEXT NOT NULL, sizeBytes INTEGER NOT NULL, status TEXT NOT NULL, " +
                "detail TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS error_reports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, module TEXT NOT NULL, " +
                "severity TEXT NOT NULL, type TEXT NOT NULL, message TEXT NOT NULL, stackTrace TEXT, jobId INTEGER, " +
                "appVersion TEXT NOT NULL, androidVersion TEXT NOT NULL, deviceModel TEXT NOT NULL, " +
                "versionCode INTEGER NOT NULL DEFAULT 0, buildId TEXT NOT NULL DEFAULT '')",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS auto_rules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, treeUri TEXT NOT NULL, " +
                "modesCsv TEXT NOT NULL, dateGranularity TEXT NOT NULL, duplicatePolicy TEXT NOT NULL, " +
                "enabled INTEGER NOT NULL, lastRunAt INTEGER)",
        )
    }

    @Test
    fun `migration 2 to 3 keeps user data and adds capacity columns`() = runBlocking {
        // --- shape and seed a v2 database with raw SQL ---
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        createV2Schema(raw)
        raw.execSQL(
            "INSERT INTO sort_jobs (treeUri, destTreeUri, modesCsv, duplicatePolicy, status, totalFiles, doneFiles, " +
                "totalBytes, doneBytes, createdAt, updatedAt, isAuto, message) " +
                "VALUES ('content://tree/x', 'content://tree/x', 'FILE_TYPE', 'SKIP', 'DONE', 2, 2, 100, 100, 1, 1, 0, NULL)",
        )
        raw.execSQL(
            "INSERT INTO move_logs (jobId, seq, sourceDocId, displayName, mime, destFolder, destDocId, destName, " +
                "sizeBytes, status, detail) VALUES (1, 0, 'primary:a.jpg', 'a.jpg', 'image/jpeg', 'Images', 'uri', 'a.jpg', 10, 'MOVED', NULL)",
        )
        raw.execSQL(
            "INSERT INTO auto_rules (name, treeUri, modesCsv, dateGranularity, duplicatePolicy, enabled, lastRunAt) " +
                "VALUES ('Pics', 'content://tree/x', 'FILE_TYPE', 'MONTH', 'SKIP', 1, NULL)",
        )
        // Room must see a v2 database, not a v0 one, or it skips the migration.
        raw.version = 2
        val v2JobCount = raw.rawQuery("SELECT COUNT(*) FROM sort_jobs", null).use { it.moveToFirst(); it.getInt(0) }
        val v2LogCount = raw.rawQuery("SELECT COUNT(*) FROM move_logs", null).use { it.moveToFirst(); it.getInt(0) }
        val v2RuleCount = raw.rawQuery("SELECT COUNT(*) FROM auto_rules", null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, v2JobCount)
        assertEquals(1, v2LogCount)
        assertEquals(1, v2RuleCount)
        raw.close()

        // --- let Room open + migrate + validate the schema ---
        val db = Room.databaseBuilder(context, SortfoldDatabase::class.java, "migration-test.db")
            .addMigrations(SortfoldDatabase.MIGRATION_2_3_FOR_TESTS())
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
        try {
            // Data intact.
            val job = db.sortJobDao().byId(1)
            assertTrue("job row must survive the migration", job != null)
            assertEquals("FILE_TYPE", job!!.modesCsv)
            assertEquals(null, job.capacityBytes) // new column defaults to SQL NULL
            val logs = db.moveLogDao().byJob(1)
            assertEquals(1, logs.size)
            assertEquals("a.jpg", logs[0].displayName)
            val rules = db.autoRuleDao().enabledRules()
            assertEquals(1, rules.size)
            assertEquals("Part", rules[0].capacityPrefix)
            assertEquals("SEQUENTIAL", rules[0].capacityOrder)

            // New columns are usable.
            db.sortJobDao().updatePlanColumns(1, 5, 500, "FILE_TYPE,CAPACITY", "RENAME", 2_000_000_000L)
            assertEquals(2_000_000_000L, db.sortJobDao().byId(1)!!.capacityBytes)

            // The (jobId, status) index exists and backs the keyset paging query.
            val index = db.openHelper.writableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_move_logs_jobId_status'",
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            assertEquals("index_move_logs_jobId_status", index)
            val page = db.moveLogDao().byJobStatusAfterSeq(1, "MOVED", -1, 500) // keyset is exclusive
            assertEquals(1, page.size)
        } finally {
            db.close()
        }
    }
}
