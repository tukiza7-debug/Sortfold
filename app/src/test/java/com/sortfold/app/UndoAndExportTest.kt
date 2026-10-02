package com.sortfold.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sortfold.app.core.history.UndoManager
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.data.db.SortfoldDatabase
import com.sortfold.app.error.ErrorExporter
import com.sortfold.app.error.PathMasker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UndoAndExportTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.install(contextProvider = {})
        FakeDocumentsProvider.reset()
        FakeDocumentsProvider.addFolder("primary", "Pics")
        FakeDocumentsProvider.addFile("primary:Pics", "IMG_1.jpg", "image/jpeg", content = ByteArray(1024))
        FakeDocumentsProvider.addFile("primary:Pics", "IMG_2.jpg", "image/jpeg", content = ByteArray(2048))
    }

    private val treeUri = "content://com.android.externalstorage.documents/tree/primary%3APics"

    private fun database(): SortfoldDatabase =
        SortfoldDatabase.build(context)

    @Test
    fun `undo restores files to the source folder and removes sorted copies`() = runBlocking {
        val db = database()
        val mover = Mover(context)
        val tree = android.net.Uri.parse(treeUri)
        val rootId = Mover.treeRootDocId(tree)
        val images = mover.ensureFolder(tree, rootId, listOf("Images"))

        val jobId = db.sortJobDao().insert(
            SortJobEntity(
                treeUri = treeUri, destTreeUri = treeUri, modesCsv = "FILE_TYPE",
                duplicatePolicy = "SKIP", status = "DONE", totalFiles = 2,
                doneFiles = 2, totalBytes = 3072, doneBytes = 3072,
                createdAt = 1, updatedAt = 2,
            ),
        )
        val m1 = mover.moveOne(tree, "primary:Pics/IMG_1.jpg", images, "IMG_1.jpg", "image/jpeg", PlanAction.MOVE)
        val m2 = mover.moveOne(tree, "primary:Pics/IMG_2.jpg", images, "IMG_2.jpg", "image/jpeg", PlanAction.MOVE)
        assertTrue(m1 is Mover.Outcome.Moved && m2 is Mover.Outcome.Moved)
        db.moveLogDao().insertAll(
            listOf(
                MoveLogEntity(
                    jobId = jobId, seq = 0, sourceDocId = "primary:Pics/IMG_1.jpg",
                    displayName = "IMG_1.jpg", mime = "image/jpeg", destFolder = "Images",
                    destDocId = (m1 as Mover.Outcome.Moved).destUri.toString(),
                    destName = "IMG_1.jpg", sizeBytes = 1024, status = "MOVED", detail = null,
                ),
                MoveLogEntity(
                    jobId = jobId, seq = 1, sourceDocId = "primary:Pics/IMG_2.jpg",
                    displayName = "IMG_2.jpg", mime = "image/jpeg", destFolder = "Images",
                    destDocId = (m2 as Mover.Outcome.Moved).destUri.toString(),
                    destName = "IMG_2.jpg", sizeBytes = 2048, status = "MOVED", detail = null,
                ),
            ),
        )

        val result = UndoManager(context, db).undoJob(jobId)
        assertEquals(2, result.restored)
        assertEquals(0, result.failed)
        assertTrue(File(FakeDocumentsProvider.root, "Pics/IMG_1.jpg").exists())
        assertTrue(File(FakeDocumentsProvider.root, "Pics/IMG_2.jpg").exists())
        assertFalse(File(FakeDocumentsProvider.root, "Pics/Images/IMG_1.jpg").exists())
    }

    @Test
    fun `exported zip masks content URIs unless full paths requested`() = runBlocking {
        val exporter = ErrorExporter(context)
        val message = "Scan failed: Invalid URI: content://com.android.externalstorage.documents/tree/primary%3APictures"
        val trace = "at q2.b.q(Unknown Source)\nUri content://com.android.externalstorage.documents/tree/primary%3APictures"
        val entries = listOf(
            com.sortfold.app.data.db.ErrorEntity(
                timestamp = 1_710_000_000_000, module = "scanner", severity = "ERROR",
                type = "IllegalArgumentException", message = message, stackTrace = trace,
                jobId = null, appVersion = "1.0.1", androidVersion = "15",
                deviceModel = "Xiaomi M2101", versionCode = 2, buildId = "abc1234",
            ),
        )

        // Default: masked.
        val masked = exporter.export(entries, "job_id\n", includeFullPaths = false)
        ZipFile(masked.file).use { zip ->
            val report = zip.getInputStream(zip.getEntry("report.txt")).bufferedReader().readText()
            val json = zip.getInputStream(zip.getEntry("errors.json")).bufferedReader().readText()
            assertFalse(report.contains("primary%3APictures"))
            assertFalse(json.contains("primary%3APictures"))
            assertTrue(report.contains("content://com.android.externalstorage.documents/..."))
            assertTrue(report.contains("abc1234"))
            assertTrue(report.contains("(2)"))
        }

        // Opt-in: full paths preserved.
        val full = exporter.export(entries, "job_id\n", includeFullPaths = true)
        ZipFile(full.file).use { zip ->
            val report = zip.getInputStream(zip.getEntry("report.txt")).bufferedReader().readText()
            assertTrue(report.contains("primary%3APictures"))
        }
    }

    @Test
    fun `path masker hides uri paths and keeps the authority`() {
        assertEquals(
            "content://com.android.externalstorage.documents/...",
            PathMasker.maskUri("content://com.android.externalstorage.documents/tree/primary%3APictures"),
        )
        val masked = PathMasker.maskText(
            "failed at content://a.b/tree/x and /storage/emulated/0/DCIM/pic.jpg and primary:DCIM/IMG_1.jpg",
            includeFullPaths = false,
        )
        assertFalse(masked.contains("/storage/emulated/0/DCIM/pic.jpg"))
        assertFalse(masked.contains("primary:DCIM/IMG_1.jpg"))
        assertTrue(masked.contains("content://a.b/..."))
    }
}
