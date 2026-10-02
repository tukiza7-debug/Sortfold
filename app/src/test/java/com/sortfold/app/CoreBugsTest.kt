package com.sortfold.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sortfold.app.core.history.UndoManager
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.MediaFile
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.rules.RuleEngine
import com.sortfold.app.core.rules.SortConfig
import com.sortfold.app.core.scanner.MediaScanner
import com.sortfold.app.core.scanner.MetadataReader
import com.sortfold.app.core.scanner.ScanFailedException
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.data.db.SortfoldDatabase
import com.sortfold.app.error.ErrorExporter
import com.sortfold.app.ui.errors.ErrorsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * REGRESSION TESTS for the 1.1.0 bug hunt (Part D of the overhaul).
 * Every test here reproduced a real defect first (red) and passes only after
 * the documented fix (green). See docs/AUDIT-1.1.0.md for the full register.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CoreBugsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.install(contextProvider = {})
        FakeDocumentsProvider.reset()
        FakeDocumentsProvider.addFolder("primary", "Pics")
    }

    private fun tree(rootId: String) =
        Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A" +
            android.net.Uri.encode(rootId))

    // ------------------------------------------------------------------
    // BUG-01: undo failed for files picked straight from the storage root.
    // sourceDocId "primary:top.jpg" contains no '/', so
    // sourceDocId.substringBeforeLast('/') returned the FILE id itself and
    // copyBack tried to create a child inside a file -> restore-create-failed.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-01 undo restores a file that was sorted from the storage root`() = runBlocking {
        // Tree root "primary:" holds top.jpg directly: doc id "primary:top.jpg".
        FakeDocumentsProvider.addFile("primary:", "top.jpg", "image/jpeg", content = ByteArray(512))
        val db = SortfoldDatabase.build(context)
        val mover = Mover(context)
        val t = tree("")
        val rootId = Mover.treeRootDocId(t)
        assertEquals("primary:", rootId)

        // Move it into a subfolder like the wizard would (DATE sort).
        val moved = mover.ensureFolder(t, rootId, listOf("2024"))
        val outcome = mover.moveOne(t, "primary:top.jpg", moved, "top.jpg", "image/jpeg", PlanAction.MOVE)
        assertTrue("outcome=$outcome", outcome is Mover.Outcome.Moved)

        val jobId = db.sortJobDao().insert(
            SortJobEntity(
                treeUri = t.toString(), destTreeUri = t.toString(), modesCsv = "DATE_TAKEN",
                duplicatePolicy = "SKIP", status = "DONE", totalFiles = 1,
                doneFiles = 1, totalBytes = 512, doneBytes = 512,
                createdAt = 1, updatedAt = 2,
            ),
        )
        db.moveLogDao().insert(
            MoveLogEntity(
                jobId = jobId, seq = 0, sourceDocId = "primary:top.jpg",
                displayName = "top.jpg", mime = "image/jpeg", destFolder = "2024",
                destDocId = (outcome as Mover.Outcome.Moved).destUri.toString(),
                destName = "top.jpg", sizeBytes = 512, status = "MOVED", detail = null,
            ),
        )

        val result = UndoManager(context, db).undoJob(jobId)
        assertEquals("undo must restore the root-level file", 1, result.restored)
        assertEquals(0, result.failed)
        assertTrue(File(FakeDocumentsProvider.root, "top.jpg").exists())
    }

    // ------------------------------------------------------------------
    // BUG-02: a provider that returns a NULL cursor produced a silent
    // "empty folder" instead of a visible failure.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-02 null provider cursor surfaces a typed scan failure`() = runBlocking {
        FakeDocumentsProvider.addFile("primary:Pics", "a.jpg", "image/jpeg")
        FakeDocumentsProvider.failQueriesWithNullCursor = true
        try {
            MediaScanner(context).scan(tree("Pics"))
            fail("expected ScanFailedException for a null cursor")
        } catch (e: ScanFailedException) {
            assertEquals(ScanFailedException.Reason.PROVIDER_ERROR, e.reason)
        } finally {
            FakeDocumentsProvider.failQueriesWithNullCursor = false
        }
    }

    // ------------------------------------------------------------------
    // BUG-03: moving a file into the folder it already lived in produced a
    // pointless "photo (1).jpg" duplicate and churned the source away.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-03 no-op move into the same folder is skipped without renaming`() = runBlocking {
        FakeDocumentsProvider.addFolder("primary:Pics", "Images")
        FakeDocumentsProvider.addFile("primary:Pics/Images", "photo.jpg", "image/jpeg", content = ByteArray(256))
        val mover = Mover(context)
        val t = tree("Pics")
        val outcome = mover.moveOne(
            t, "primary:Pics/Images/photo.jpg", "primary:Pics/Images", "photo.jpg",
            "image/jpeg", PlanAction.MOVE,
        )
        assertTrue("outcome=$outcome", outcome is Mover.Outcome.SkippedDuplicate)
        assertTrue(File(FakeDocumentsProvider.root, "Pics/Images/photo.jpg").exists())
        assertFalse(File(FakeDocumentsProvider.root, "Pics/Images/photo (1).jpg").exists())
    }

    // ------------------------------------------------------------------
    // BUG-04: RENAME collisions were tracked globally across destination
    // folders, so two files with the same name heading to DIFFERENT folders
    // triggered a pointless rename of the second one.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-04 rename collisions are scoped per destination folder`() {
        val files = listOf(
            // Same NAME, different folders (mime drives FILE_TYPE): no collision.
            file("photo.jpg", id = "primary:Pics/p1.jpg", mime = "image/jpeg"),   // -> Images
            file("photo.jpg", id = "primary:Pics/p2.jpg", mime = "video/mp4"),    // -> Videos
            // Same NAME into the SAME folder: second one must be renamed.
            file("b.jpg", id = "primary:Pics/b1.jpg", mime = "image/jpeg"),        // -> Images
            file("b.jpg", id = "primary:Pics/b2.jpg", mime = "image/jpeg"),        // -> Images
        )
        val config = SortConfig(
            modes = setOf(com.sortfold.app.core.model.SortMode.FILE_TYPE),
            duplicatePolicy = DuplicatePolicy.RENAME,
        )
        val plan = RuleEngine.plan(files, config)
        // Same name into DIFFERENT folders: both keep their name.
        val aImages = plan.first { it.destinationFolder == "Images" && it.displayName == "photo.jpg" }
        val aVideos = plan.first { it.destinationFolder == "Videos" && it.displayName == "photo.jpg" }
        assertEquals(PlanAction.MOVE, aImages.action)
        assertEquals(PlanAction.MOVE, aVideos.action)
        assertEquals("photo.jpg", aVideos.destinationName)
        // Same name into the SAME folder: second one is renamed.
        val bImages = plan.filter { it.destinationFolder == "Images" && it.displayName == "b.jpg" }
        assertEquals(2, bImages.size)
        assertEquals("b.jpg", bImages[0].destinationName)
        assertEquals("b (2).jpg", bImages[1].destinationName)
    }

    // ------------------------------------------------------------------
    // BUG-05: REPLACE deleted the existing destination BEFORE copying; if
    // the copy failed the original file was gone (data loss).
    // ------------------------------------------------------------------
    @Test
    fun `BUG-05 failed copy under REPLACE keeps the existing destination file`() = runBlocking {
        FakeDocumentsProvider.addFolder("primary:Pics", "Images")
        FakeDocumentsProvider.addFile("primary:Pics/Images", "photo.jpg", "image/jpeg", content = ByteArray(16))
        FakeDocumentsProvider.failOpensWithSecurityException = true
        val mover = Mover(context)
        val t = tree("Pics")
        val outcome = try {
            mover.moveOne(
                t, "primary:Pics/photo.jpg", "primary:Pics/Images", "photo.jpg",
                "image/jpeg", PlanAction.REPLACE,
            )
        } finally {
            FakeDocumentsProvider.failOpensWithSecurityException = false
        }
        assertTrue("outcome=$outcome", outcome is Mover.Outcome.Failed)
        // The pre-existing destination must survive the failed replace.
        assertEquals(16L, File(FakeDocumentsProvider.root, "Pics/Images/photo.jpg").length())
    }

    // ------------------------------------------------------------------
    // BUG-14: a SecurityException during the copy was reported as the
    // generic "copy-failed", hiding the real reason from the Error Library.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-14 permission failures during copy are reported as permission-denied`() = runBlocking {
        FakeDocumentsProvider.addFolder("primary:Pics", "Images")
        FakeDocumentsProvider.failOpensWithSecurityException = true
        val mover = Mover(context)
        val t = tree("Pics")
        val outcome = try {
            mover.moveOne(
                t, "primary:Pics/photo.jpg", "primary:Pics/Images", "photo.jpg",
                "image/jpeg", PlanAction.MOVE,
            )
        } finally {
            FakeDocumentsProvider.failOpensWithSecurityException = false
        }
        assertTrue(outcome is Mover.Outcome.Failed)
        assertEquals("permission-denied", (outcome as Mover.Outcome.Failed).detail)
    }

    // ------------------------------------------------------------------
    // BUG-13: metadata progress never sent its final count when the file
    // count was not a multiple of 32, leaving progress stuck.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-13 metadata enrichment sends a final progress callback`() = runBlocking {
        FakeDocumentsProvider.addFile("primary:Pics", "a.jpg", "image/jpeg")
        FakeDocumentsProvider.addFile("primary:Pics", "b.jpg", "image/jpeg")
        FakeDocumentsProvider.addFile("primary:Pics", "c.jpg", "image/jpeg")
        val files = MediaScanner(context).scan(tree("Pics")).files
        val seen = mutableListOf<Int>()
        MetadataReader.enrich(
            context.contentResolver, files, tree("Pics"),
            needDimensions = false, needDates = false, onProgress = { seen += it },
        )
        assertEquals("last progress must equal the file count", files.size, seen.last())
    }

    // ------------------------------------------------------------------
    // BUG-12: the "Android" top-level folder was classified as a safe
    // destination; sorting straight into it endangers app data.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-12 the Android top-level folder is classified app-private`() {
        assertEquals(
            com.sortfold.app.core.mover.StorageSafety.TreeRisk.APP_PRIVATE,
            com.sortfold.app.core.mover.StorageSafety.classifyTreeId("primary:Android"),
        )
    }

    // ------------------------------------------------------------------
    // BUG-06: two different errors sharing module+type+timestamp produced
    // identical LazyColumn keys -> IllegalArgumentException crash.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-06 grouped error keys are unique across distinct messages`() {
        val t = 1_700_000_000_000
        val entities = listOf(
            com.sortfold.app.data.db.ErrorEntity(
                id = 1, timestamp = t, module = "scanner", severity = "ERROR",
                type = "ScanFailedException", message = "Scan failed for A: boom",
                stackTrace = null, jobId = null, appVersion = "1.1.0",
                androidVersion = "14", deviceModel = "X", versionCode = 2, buildId = "s",
            ),
            com.sortfold.app.data.db.ErrorEntity(
                id = 2, timestamp = t, module = "scanner", severity = "ERROR",
                type = "ScanFailedException", message = "Scan failed for B: boom",
                stackTrace = null, jobId = null, appVersion = "1.1.0",
                androidVersion = "14", deviceModel = "X", versionCode = 2, buildId = "s",
            ),
        )
        val groups = ErrorsViewModel.group(entities)
        val keys = groups.map { ErrorsViewModel.groupKey(it) }
        assertEquals(2, keys.size)
        assertNotEquals("distinct messages must never share a list key", keys[0], keys[1])
    }

    // ------------------------------------------------------------------
    // BUG-07: moves.csv did not escape quotes/commas in file names.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-07 csv export escapes quotes commas and newlines`() {
        val escaped = ErrorExporter.csvField("my \"photo\", final\nedition")
        assertEquals("\"my \"\"photo\"\", final\nedition\"", escaped)
    }

    // ------------------------------------------------------------------
    // BUG-15: two exports in the same second overwrote each other.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-15 export zip names never collide within the same second`() = runBlocking {
        val exporter = ErrorExporter(context)
        val names = mutableSetOf<String>()
        repeat(5) {
            val result = exporter.export(emptyList(), "x\n", includeFullPaths = false)
            names += result.file.name
        }
        assertEquals("every export must produce a distinct file", 5, names.size)
    }

    private fun file(
        name: String,
        id: String = "primary:Pics/$name",
        sizeBytes: Long = 10,
        mime: String = "image/jpeg",
    ) = MediaFile(
        documentId = id,
        displayName = name,
        sizeBytes = sizeBytes,
        lastModifiedMillis = 1_710_000_000_000,
        mime = mime,
    )
}
