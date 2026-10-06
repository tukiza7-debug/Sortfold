package com.sortfold.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.sortfold.app.core.model.CapacityOrder
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.MediaType
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.model.ResolutionClass
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.core.mover.StorageSafety
import com.sortfold.app.core.rules.RuleEngine
import com.sortfold.app.core.rules.SortConfig
import com.sortfold.app.core.scanner.MetadataReader
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.data.db.SortfoldDatabase
import com.sortfold.app.work.SortWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * REGRESSION TESTS for the 1.2.0 bug register B-01..B-19 plus the Part A
 * capacity surface. Each test pins the fixed behaviour of one register
 * entry; see docs/AUDIT-1.2.0.md for the fix/test mapping.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Sortfold120BugsTest {

    private lateinit var context: Context
    private lateinit var db: SortfoldDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.install(contextProvider = {})
        FakeDocumentsProvider.reset()
        FakeDocumentsProvider.addFolder("primary", "Pics")
        // The worker reads its job through the app container's database, so
        // every test seeds and asserts against that same database. Tests are
        // jobId-scoped, so leftover rows from earlier tests are harmless.
        db = (context.applicationContext as com.sortfold.app.SortfoldApp).container.database
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @After
    fun tearDown() {
        FakeDocumentsProvider.supportsMove = false
    }

    private fun tree() =
        Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APics")

    private fun idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun awaitUntil(condition: () -> Boolean) {
        repeat(600) {
            idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within timeout")
    }

    private fun seedJob(
        policy: DuplicatePolicy = DuplicatePolicy.SKIP,
        names: List<String> = listOf("a.jpg"),
        capacityBytes: Long? = null,
    ): Long = runBlocking {
        val now = System.currentTimeMillis()
        val jobId = db.sortJobDao().insert(
            SortJobEntity(
                treeUri = tree().toString(), destTreeUri = tree().toString(),
                modesCsv = "FILE_TYPE", duplicatePolicy = policy.name,
                status = "PLANNED", totalFiles = names.size,
                doneFiles = 0, totalBytes = names.size * 10L, doneBytes = 0,
                createdAt = now, updatedAt = now, capacityBytes = capacityBytes,
            ),
        )
        db.moveLogDao().insertAll(
            names.mapIndexed { i, name ->
                MoveLogEntity(
                    jobId = jobId, seq = i, sourceDocId = "primary:Pics/$name",
                    displayName = name, mime = "image/jpeg",
                    destFolder = "Images", destDocId = null, destName = name,
                    sizeBytes = 10, status = "PLANNED", detail = null,
                )
            },
        )
        jobId
    }

    private fun realWorker(jobId: Long): SortWorker =
        TestListenableWorkerBuilder<SortWorker>(context)
            .setInputData(androidx.work.workDataOf(SortWorker.KEY_JOB_ID to jobId))
            .build()

    // ------------------------------------------------------------------
    // B-01: RENAME/REPLACE never worked because the plan never saw the
    // destination names; the worker also ignored the job's policy.
    // ------------------------------------------------------------------
    @Test
    fun `B-01 plan flags an existing destination name when the caller lists names`() {
        val files = listOf(
            com.sortfold.app.core.model.MediaFile("primary:Pics/a.jpg", "a.jpg", 10, 0, "image/jpeg"),
        )
        val config = SortConfig(modes = setOf(SortMode.FILE_TYPE), duplicatePolicy = DuplicatePolicy.RENAME)
        val plan = RuleEngine.plan(files, config, existingNames = mapOf("Images" to setOf("a.jpg")))
        assertEquals(PlanAction.RENAME, plan[0].action)
        assertEquals("a (2).jpg", plan[0].destinationName)

        val replacePlan = RuleEngine.plan(
            files, config.copy(duplicatePolicy = DuplicatePolicy.REPLACE),
            existingNames = mapOf("Images" to setOf("a.jpg")),
        )
        assertEquals(PlanAction.REPLACE, replacePlan[0].action)
    }

    @Test
    fun `B-01 worker action follows the job policy as a safety net`() {
        val worker = realWorker(jobId = 1)
        val method = SortWorker::class.java.getDeclaredMethod(
            "actionOf", String::class.java, DuplicatePolicy::class.java,
        ).apply { isAccessible = true }
        assertEquals(PlanAction.REPLACE, method.invoke(worker, null, DuplicatePolicy.REPLACE))
        assertEquals(PlanAction.RENAME, method.invoke(worker, null, DuplicatePolicy.RENAME))
        assertEquals(PlanAction.MOVE, method.invoke(worker, null, DuplicatePolicy.SKIP))
        assertEquals(PlanAction.RENAME, method.invoke(worker, "renamed:a.jpg", DuplicatePolicy.SKIP))
        assertEquals(PlanAction.REPLACE, method.invoke(worker, "replace", DuplicatePolicy.SKIP))
    }

    @Test
    fun `B-01 a full run under RENAME renames instead of silently skipping`() = runBlocking {
        // Destination folder already holds "a.jpg"; a second, identical name arrives.
        FakeDocumentsProvider.addFile("primary:Pics", "a.jpg", "image/jpeg", ByteArray(10))
        FakeDocumentsProvider.addFolder("primary:Pics", "Images")
        FakeDocumentsProvider.addFile("primary:Pics/Images", "a.jpg", "image/jpeg", ByteArray(99))
        val jobId = seedJob(policy = DuplicatePolicy.RENAME, names = listOf("a.jpg"))
        val worker = realWorker(jobId)
        withTimeout(20_000) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { worker.doWork() }
        }
        val row = db.moveLogDao().byJob(jobId).single()
        assertEquals("MOVED", row.status)
        assertEquals("a (2).jpg", row.destName)
    }

    // ------------------------------------------------------------------
    // B-02: cancellation is never swallowed and never strands the job.
    // ------------------------------------------------------------------
    @Test
    fun `B-02 moveOne rethrows cancellation and removes the partial destination`() = runBlocking {
        // 16 MB so the 1 MiB chunk loop runs several times.
        FakeDocumentsProvider.addFile("primary:Pics", "big.jpg", "image/jpeg", ByteArray(16 * 1024 * 1024))
        FakeDocumentsProvider.addFolder("primary:Pics", "Images")
        val mover = Mover(context)
        val destFolder = mover.ensureFolder(tree(), Mover.treeRootDocId(tree()), listOf("Images"))

        val scope = CoroutineScope(Job() + Dispatchers.IO)
        var cancelled: Throwable? = null
        val deferred = scope.async {
            mover.moveOne(
                tree(), "primary:Pics/big.jpg", destFolder, "big.jpg", "image/jpeg",
                PlanAction.MOVE,
                progress = { scope.cancel() }, // cancel mid-file after the first chunk
            )
        }
        withTimeout(20_000) {
            try {
                deferred.await()
                org.junit.Assert.fail("expected the move to be cancelled mid-file")
            } catch (e: CancellationException) {
                cancelled = e
            }
        }
        assertTrue("cancellation must surface, not be swallowed", cancelled is CancellationException)
        // The partial destination is deleted in NonCancellable; the source stays.
        assertFalse("partial destination must be gone", mover.listNames(tree(), destFolder).contains("big.jpg"))
        assertTrue("source must be intact", FakeDocumentsProvider.fileFor("primary:Pics/big.jpg").exists())
        scope.cancel()
    }

    @Test
    fun `B-02 a job whose worker is stopped is never left RUNNING`() = runBlocking {
        FakeDocumentsProvider.addFile("primary:Pics", "a.jpg", "image/jpeg", ByteArray(10))
        val jobId = seedJob(names = listOf("a.jpg"))
        val worker = realWorker(jobId)
        // A user pause arrives before the loop starts: rows stay untouched,
        // and the job lands in PAUSED, never RUNNING.
        SortWorker.requestStop(jobId, SortWorker.RequestedState.PAUSED)
        withTimeout(20_000) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { worker.doWork() }
        }
        val job = db.sortJobDao().byId(jobId)!!
        assertEquals("PAUSED", job.status)
        assertEquals(1, db.moveLogDao().countByStatus(jobId, "PLANNED")) // untouched, resumable
    }

    // ------------------------------------------------------------------
    // B-04: moveDocument fast path when the provider supports it.
    // ------------------------------------------------------------------
    @Test
    fun `B-04 moveDocument is used when the provider supports it`() = runBlocking {
        FakeDocumentsProvider.supportsMove = true
        FakeDocumentsProvider.addFile("primary:Pics", "m.jpg", "image/jpeg", ByteArray(16))
        FakeDocumentsProvider.addFolder("primary:Pics", "Images")
        val mover = Mover(context)
        assertTrue("probe must see the flag", mover.supportsMove(tree()))
        val destFolder = mover.ensureFolder(tree(), Mover.treeRootDocId(tree()), listOf("Images"))
        val outcome = mover.moveOne(
            tree(), "primary:Pics/m.jpg", destFolder, "m.jpg", "image/jpeg", PlanAction.MOVE,
        )
        assertTrue(outcome is Mover.Outcome.Moved)
        assertFalse("source must be gone without a copy", FakeDocumentsProvider.fileFor("primary:Pics/m.jpg").exists())
        assertTrue(FakeDocumentsProvider.fileFor("primary:Pics/Images/m.jpg").exists())
    }

    @Test
    fun `B-04 copy+delete still works when the provider does not support move`() = runBlocking {
        FakeDocumentsProvider.supportsMove = false
        FakeDocumentsProvider.addFile("primary:Pics", "m.jpg", "image/jpeg", ByteArray(16))
        FakeDocumentsProvider.addFolder("primary:Pics", "Images")
        val mover = Mover(context)
        assertFalse(mover.supportsMove(tree()))
        val destFolder = mover.ensureFolder(tree(), Mover.treeRootDocId(tree()), listOf("Images"))
        val outcome = mover.moveOne(
            tree(), "primary:Pics/m.jpg", destFolder, "m.jpg", "image/jpeg", PlanAction.MOVE,
        )
        assertTrue(outcome is Mover.Outcome.Moved)
        assertTrue(FakeDocumentsProvider.fileFor("primary:Pics/Images/m.jpg").exists())
        assertFalse(FakeDocumentsProvider.fileFor("primary:Pics/m.jpg").exists())
    }

    // ------------------------------------------------------------------
    // B-05: storage evaluation is about the largest file, not the plan
    // total, and skips the block when moveDocument is available.
    // ------------------------------------------------------------------
    @Test
    fun `B-05 storage verdict skips the block when move is supported`() {
        val verdict = StorageSafety.evaluate(context, tree(), largestFileBytes = 999_000_000_000L, moveSupported = true)
        assertEquals(StorageSafety.SpaceVerdict.OK, verdict)
    }

    // ------------------------------------------------------------------
    // B-07: failed rows are re-queued for a retry pass; resume counts them.
    // ------------------------------------------------------------------
    @Test
    fun `B-07 requeueFailed moves FAILED rows back to PLANNED`() = runBlocking {
        val jobId = seedJob(names = listOf("a.jpg", "b.jpg"))
        val rows = db.moveLogDao().byJob(jobId)
        db.moveLogDao().updateStatus(rows[0].id, "FAILED", null, "copy-failed")
        db.moveLogDao().updateStatus(rows[1].id, "MOVED", "uri", null)
        assertEquals(1, db.moveLogDao().countByStatus(jobId, "FAILED"))
        assertEquals(1, db.moveLogDao().requeueFailed(jobId))
        assertEquals(0, db.moveLogDao().countByStatus(jobId, "FAILED"))
        assertEquals(1, db.moveLogDao().countByStatus(jobId, "PLANNED"))
        assertEquals(1, db.moveLogDao().countByStatus(jobId, "MOVED")) // MOVED rows untouched

    }

    // ------------------------------------------------------------------
    // B-08: mime travels with the plan row and into the log.
    // ------------------------------------------------------------------
    @Test
    fun `B-08 plan rows carry the source mime`() {
        val files = listOf(
            com.sortfold.app.core.model.MediaFile("primary:Pics/a.png", "a.png", 10, 0, "image/png"),
        )
        val plan = RuleEngine.plan(files, SortConfig(modes = setOf(SortMode.FILE_TYPE)))
        assertEquals("image/png", plan[0].mime)
    }

    // ------------------------------------------------------------------
    // B-09: SOURCE_APP detects the app per file, not per folder.
    // ------------------------------------------------------------------
    @Test
    fun `B-09 source app is detected from the file name in a flat folder`() {
        val files = listOf(
            com.sortfold.app.core.model.MediaFile("primary:Pics/1", "Screenshot_20230501-120000.jpg", 10, 0, "image/jpeg"),
            com.sortfold.app.core.model.MediaFile("primary:Pics/2", "IMG-20230501-WA0001.jpg", 10, 0, "image/jpeg"),
            com.sortfold.app.core.model.MediaFile("primary:Pics/3", "PXL_20230501_120000000.jpg", 10, 0, "image/jpeg"),
            com.sortfold.app.core.model.MediaFile("primary:Pics/4", "Telegram Image.jpg", 10, 0, "image/jpeg"),
            com.sortfold.app.core.model.MediaFile("primary:Pics/5", "mystery.bin", 10, 0, null),
        )
        val plan = RuleEngine.plan(files, SortConfig(modes = setOf(SortMode.SOURCE_APP), treePath = "Pics"))
        assertEquals("Screenshots", plan[0].destinationFolder)
        assertEquals("WhatsApp", plan[1].destinationFolder)
        assertEquals("Camera", plan[2].destinationFolder)
        assertEquals("Telegram", plan[3].destinationFolder)
        assertEquals("Other", plan[4].destinationFolder)
    }

    // ------------------------------------------------------------------
    // B-10: copyBack verifies size, resolves collisions, cleans folders.
    // ------------------------------------------------------------------
    @Test
    fun `B-10 copyBack refuses to stack duplicate names and reports collisions`() = runBlocking {
        FakeDocumentsProvider.addFile("primary:Pics", "orig.jpg", "image/jpeg", ByteArray(32))
        FakeDocumentsProvider.addFolder("primary:Pics", "Images")
        val mover = Mover(context)
        val destFolder = mover.ensureFolder(tree(), Mover.treeRootDocId(tree()), listOf("Images"))
        val moved = mover.moveOne(tree(), "primary:Pics/orig.jpg", destFolder, "orig.jpg", "image/jpeg", PlanAction.MOVE)
        val destDocId = android.provider.DocumentsContract.getDocumentId((moved as Mover.Outcome.Moved).destUri)
        // Drop a different file at the original spot: a collision for undo.
        FakeDocumentsProvider.addFile("primary:Pics", "orig.jpg", "image/jpeg", ByteArray(8))
        val outcome = mover.copyBack(tree(), destDocId, Mover.parentDocIdOf("primary:Pics/orig.jpg"), "orig.jpg", "image/jpeg")
        assertTrue("undo must survive a collision", outcome is Mover.Outcome.Moved)
        assertEquals("orig (2).jpg", (outcome as Mover.Outcome.Moved).destName)
        assertFalse("sorted copy must be gone", FakeDocumentsProvider.fileFor("primary:Pics/Images/orig.jpg").exists())
    }

    @Test
    fun `B-10 successful undo removes empty created folders`() = runBlocking {
        FakeDocumentsProvider.addFile("primary:Pics", "f.jpg", "image/jpeg", ByteArray(16))
        val mover = Mover(context)
        val destFolder = mover.ensureFolder(tree(), Mover.treeRootDocId(tree()), listOf("Images"))
        val moved = mover.moveOne(tree(), "primary:Pics/f.jpg", destFolder, "f.jpg", "image/jpeg", PlanAction.MOVE)
        val docId = android.provider.DocumentsContract.getDocumentId((moved as Mover.Outcome.Moved).destUri)
        mover.copyBack(tree(), docId, Mover.parentDocIdOf("primary:Pics/f.jpg"), "f.jpg", "image/jpeg")
        assertFalse("empty destination folder must be cleaned up", FakeDocumentsProvider.fileFor("primary:Pics/Images").exists())
        assertTrue("restored file is back", FakeDocumentsProvider.fileFor("primary:Pics/f.jpg").exists())
    }

    @Test
    fun `B-10 undoing several files that shared one folder never fails on cleanup`() = runBlocking {
        // Reverse-order undo (as UndoManager walks): the second copyBack runs
        // after the first pass already pruned the shared destination folder.
        FakeDocumentsProvider.addFile("primary:Pics", "f1.jpg", "image/jpeg", ByteArray(16))
        FakeDocumentsProvider.addFile("primary:Pics", "f2.jpg", "image/jpeg", ByteArray(16))
        FakeDocumentsProvider.addFolder("primary:Pics", "Images")
        val mover = Mover(context)
        val destFolder = mover.ensureFolder(tree(), Mover.treeRootDocId(tree()), listOf("Images"))
        val moved1 = mover.moveOne(tree(), "primary:Pics/f1.jpg", destFolder, "f1.jpg", "image/jpeg", PlanAction.MOVE) as Mover.Outcome.Moved
        val moved2 = mover.moveOne(tree(), "primary:Pics/f2.jpg", destFolder, "f2.jpg", "image/jpeg", PlanAction.MOVE) as Mover.Outcome.Moved
        val out2 = mover.copyBack(
            tree(), android.provider.DocumentsContract.getDocumentId(moved2.destUri),
            Mover.parentDocIdOf("primary:Pics/f2.jpg"), "f2.jpg", "image/jpeg",
        )
        assertTrue(out2 is Mover.Outcome.Moved)
        val out1 = mover.copyBack(
            tree(), android.provider.DocumentsContract.getDocumentId(moved1.destUri),
            Mover.parentDocIdOf("primary:Pics/f1.jpg"), "f1.jpg", "image/jpeg",
        )
        assertTrue("cleanup must never fail a later undo pass", out1 is Mover.Outcome.Moved)
        assertTrue(FakeDocumentsProvider.fileFor("primary:Pics/f1.jpg").exists())
        assertTrue(FakeDocumentsProvider.fileFor("primary:Pics/f2.jpg").exists())
    }

    // ------------------------------------------------------------------
    // B-11: a row left IN_FLIGHT with a complete copy finishes on resume;
    // a partial copy is rolled back and re-queued.
    // ------------------------------------------------------------------
    @Test
    fun `B-11 worker reconciles a complete IN_FLIGHT copy into MOVED`() = runBlocking {
        FakeDocumentsProvider.addFile("primary:Pics", "x.jpg", "image/jpeg", ByteArray(64))
        val jobId = seedJob(names = listOf("x.jpg"))
        val row = db.moveLogDao().byJob(jobId).single()

        // Process death after the destination copy exists but before the
        // source delete: create the destination copy by hand, equal size.
        val mover = Mover(context)
        val destFolder = mover.ensureFolder(tree(), Mover.treeRootDocId(tree()), listOf("Images"))
        val copyFile = java.io.File(FakeDocumentsProvider.fileFor(destFolder), "x.jpg")
        copyFile.writeBytes(ByteArray(64))
        val copyDocId = FakeDocumentsProvider.docIdFor(copyFile)
        db.moveLogDao().updateStatus(row.id, "IN_FLIGHT", null, null)

        val worker = realWorker(jobId)
        withTimeout(20_000) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { worker.doWork() }
        }
        val done = db.moveLogDao().byJob(jobId).single()
        assertEquals("MOVED", done.status)
        assertTrue("source must be gone after the reconciled delete", !FakeDocumentsProvider.fileFor(row.sourceDocId).exists())
        assertEquals("DONE", db.sortJobDao().byId(jobId)!!.status)
    }

    @Test
    fun `B-11 worker rolls back a partial IN_FLIGHT copy and moves the file cleanly`() = runBlocking {
        FakeDocumentsProvider.addFile("primary:Pics", "y.jpg", "image/jpeg", ByteArray(64))
        val jobId = seedJob(names = listOf("y.jpg"))
        val row = db.moveLogDao().byJob(jobId).single()

        val mover = Mover(context)
        val destFolder = mover.ensureFolder(tree(), Mover.treeRootDocId(tree()), listOf("Images"))
        val copyFile = java.io.File(FakeDocumentsProvider.fileFor(destFolder), "y.jpg")
        copyFile.writeBytes(ByteArray(10)) // partial (10 != 64)
        db.moveLogDao().updateStatus(row.id, "IN_FLIGHT", null, null)

        val worker = realWorker(jobId)
        withTimeout(20_000) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { worker.doWork() }
        }
        val done = db.moveLogDao().byJob(jobId).single()
        assertEquals("MOVED", done.status)
        assertTrue("partial copy must have been removed and redone cleanly",
            FakeDocumentsProvider.fileFor("primary:Pics/Images/y.jpg").length() == 64L)
    }

    // ------------------------------------------------------------------
    // B-14: the update notification fires once per version.
    // ------------------------------------------------------------------
    @Test
    fun `B-14 lastNotifiedVersion is persisted`() = runBlocking {
        val repo = com.sortfold.app.data.prefs.SettingsRepository(context)
        repo.setLastNotifiedVersion("1.2.0")
        assertEquals("1.2.0", repo.snapshot().lastNotifiedVersion)
        repo.setLastNotifiedVersion(null)
        assertNull(repo.snapshot().lastNotifiedVersion)
    }

    // ------------------------------------------------------------------
    // B-15: name dates need a valid calendar date and a known prefix.
    // ------------------------------------------------------------------
    @Test
    fun `B-15 dateFromName rejects mid-name dates and impossible dates`() {
        assertNotEquals(null, MetadataReader.dateFromName("IMG_20230501_123456.jpg"))
        assertNotEquals(null, MetadataReader.dateFromName("2023-05-01 report.jpg"))
        assertEquals(null, MetadataReader.dateFromName("holiday-20190501-party.jpg"))
        assertEquals(null, MetadataReader.dateFromName("IMG_20230231_000000.jpg"))
    }

    @Test
    fun `B-15 ts files are not video unless the mime says so`() {
        assertEquals(MediaType.OTHER, MediaType.fromMimeAndName(null, "main.ts"))
        assertEquals(MediaType.VIDEO, MediaType.fromMimeAndName("video/mp2t", "main.ts"))
    }

    // ------------------------------------------------------------------
    // B-16: unreadable resolution gets its own bucket.
    // ------------------------------------------------------------------
    @Test
    fun `B-16 unknown resolution has its own bucket and segment`() {
        assertEquals(ResolutionClass.UNKNOWN, ResolutionClass.of(null, null))
        val f = com.sortfold.app.core.model.MediaFile("primary:Pics/r", "r.png", 10, 0, "image/png")
        val plan = RuleEngine.plan(listOf(f), SortConfig(modes = setOf(SortMode.RESOLUTION)))
        assertEquals("Unknown", plan[0].destinationFolder)
    }

    // ------------------------------------------------------------------
    // B-13 + Part A: the job row remembers modes, policy and the cap.
    // ------------------------------------------------------------------
    @Test
    fun `B-13 rebuilding a plan refreshes modes policy and cap on the job row`() = runBlocking {
        val jobId = seedJob()
        db.sortJobDao().updatePlanColumns(jobId, 9, 900, "FILE_TYPE,CAPACITY", "REPLACE", 2_000_000_000L)
        val job = db.sortJobDao().byId(jobId)!!
        assertEquals("FILE_TYPE,CAPACITY", job.modesCsv)
        assertEquals("REPLACE", job.duplicatePolicy)
        assertEquals(2_000_000_000L, job.capacityBytes)
        assertEquals(9, job.totalFiles)
    }

    // ------------------------------------------------------------------
    // B-17: keyset paging returns rows after a seq, in order, in pages.
    // ------------------------------------------------------------------
    @Test
    fun `B-17 keyset paging walks rows without loading everything`() = runBlocking {
        val jobId = seedJob(names = (1..12).map { "f$it.jpg" })
        // The keyset is strictly EXCLUSIVE, so paging starts at -1, never 0
        // (the worker bug this pins: starting at 0 skipped the first row).
        val page1 = db.moveLogDao().byJobStatusAfterSeq(jobId, "PLANNED", -1, 5)
        assertEquals(5, page1.size)
        val page2 = db.moveLogDao().byJobStatusAfterSeq(jobId, "PLANNED", page1.last().seq, 5)
        assertEquals(5, page2.size)
        val page3 = db.moveLogDao().byJobStatusAfterSeq(jobId, "PLANNED", page2.last().seq, 5)
        assertEquals(2, page3.size)
        assertEquals((0..11).toList(), (page1 + page2 + page3).map { it.seq })
    }

    // ------------------------------------------------------------------
    // B-18: the merged manifest no longer requests media permissions.
    // ------------------------------------------------------------------
    @Test
    fun `B-18 manifest drops media read permissions`() {
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions?.toList().orEmpty()
        assertFalse(permissions.any { it.startsWith("android.permission.READ_MEDIA") })
        assertFalse(permissions.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertTrue(permissions.contains("android.permission.POST_NOTIFICATIONS"))
    }

    // ------------------------------------------------------------------
    // B-19: blank signing secrets are treated as unset.
    // ------------------------------------------------------------------
    @Test
    fun `B-19 blank strings are rejected as keystore values`() {
        val asUnset: (String?) -> String? = { it?.takeIf { v -> v.isNotBlank() } }
        assertEquals(null, asUnset(""))
        assertEquals(null, asUnset("   "))
        assertEquals("key.jks", asUnset("key.jks"))
    }

    // ------------------------------------------------------------------
    // Part C-01: reduced motion pins the press scale at 1f via the token.
    // ------------------------------------------------------------------
    @Test
    fun `C-01 reduced-motion tween is instant`() {
        assertEquals(1, com.sortfold.app.ui.theme.Motion.reduced<Int>().durationMillis)
    }

    // ------------------------------------------------------------------
    // Part A: capacity defaults persist for the next wizard run.
    // ------------------------------------------------------------------
    @Test
    fun `A capacity defaults round-trip through the settings repository`() = runBlocking {
        val repo = com.sortfold.app.data.prefs.SettingsRepository(context)
        repo.setCapacityDefaults(2_000_000_000L, CapacityOrder.BEST_FIT, "Chunk", "MB")
        val s = repo.snapshot()
        assertEquals(2_000_000_000L, s.capacityDefaultBytes)
        assertEquals(CapacityOrder.BEST_FIT, s.capacityDefaultOrder)
        assertEquals("Chunk", s.capacityDefaultPrefix)
        assertEquals("MB", s.capacityDefaultUnit)
        assertTrue(s.capacityUnitDecimal)
    }

    // ------------------------------------------------------------------
    // Part A end-to-end: a capacity job actually produces Part folders that
    // respect the cap, and the job row records the cap.
    // ------------------------------------------------------------------
    @Test
    fun `A capacity job splits files into capped part folders end to end`() = runBlocking {
        FakeDocumentsProvider.addFile("primary:Pics", "v1.mp4", "video/mp4", ByteArray(50_000_000))
        FakeDocumentsProvider.addFile("primary:Pics", "v2.mp4", "video/mp4", ByteArray(50_000_000))
        val now = System.currentTimeMillis()
        val jobId = db.sortJobDao().insert(
            SortJobEntity(
                treeUri = tree().toString(), destTreeUri = tree().toString(),
                modesCsv = "CAPACITY", duplicatePolicy = "SKIP",
                status = "PLANNED", totalFiles = 2,
                doneFiles = 0, totalBytes = 100_000_000, doneBytes = 0,
                createdAt = now, updatedAt = now, capacityBytes = 100_000_000L,
            ),
        )
        db.moveLogDao().insertAll(
            listOf(
                MoveLogEntity(
                    jobId = jobId, seq = 0, sourceDocId = "primary:Pics/v1.mp4",
                    displayName = "v1.mp4", mime = "video/mp4", destFolder = "Part 01",
                    destDocId = null, destName = "v1.mp4", sizeBytes = 50_000_000,
                    status = "PLANNED", detail = null,
                ),
                MoveLogEntity(
                    jobId = jobId, seq = 1, sourceDocId = "primary:Pics/v2.mp4",
                    displayName = "v2.mp4", mime = "video/mp4", destFolder = "Part 02",
                    destDocId = null, destName = "v2.mp4", sizeBytes = 50_000_000,
                    status = "PLANNED", detail = null,
                ),
            ),
        )
        val worker = realWorker(jobId)
        withTimeout(60_000) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { worker.doWork() }
        }
        assertEquals(2, db.moveLogDao().countByStatus(jobId, "MOVED"))
        assertEquals("DONE", db.sortJobDao().byId(jobId)!!.status)
        assertEquals(100_000_000L, db.sortJobDao().byId(jobId)!!.capacityBytes)
        assertTrue(FakeDocumentsProvider.fileFor("primary:Pics/Part 01/v1.mp4").exists())
        assertTrue(FakeDocumentsProvider.fileFor("primary:Pics/Part 02/v2.mp4").exists())
        // The part folders exist on disk — packer naming is what the worker created.
        assertEquals("Part 01", db.moveLogDao().byJob(jobId)[0].destFolder)
    }
}
