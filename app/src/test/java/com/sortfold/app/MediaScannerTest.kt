package com.sortfold.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.scanner.MediaScanner
import com.sortfold.app.core.scanner.ScanFailedException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Scanner and mover tests against a file-backed fake DocumentsProvider.
 * The first test reproduces the field failure from the Xiaomi device report:
 * passing a tree URI into DocumentsContract.getDocumentId threw
 * "Invalid URI: content://com.android.externalstorage.documents/tree/primary%3APictures".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MediaScannerTest {

    companion object {
        /** Exact URI observed in the device error report. */
        val DEVICE_TREE_URI: Uri =
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APictures")
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.install(contextProvider = {})
        FakeDocumentsProvider.reset()
        FakeDocumentsProvider.addFolder("primary", "Pictures")
        FakeDocumentsProvider.addFolder("primary:Pictures", "Camera")
        FakeDocumentsProvider.addFile("primary:Pictures", "IMG_20240501_1200.jpg", "image/jpeg")
        FakeDocumentsProvider.addFile("primary:Pictures", "VID_20240501_1205.mp4", "video/mp4")
        FakeDocumentsProvider.addFile("primary:Pictures", "notes.txt", "text/plain")
    }

    @Test
    fun `scans the exact device tree URI without IllegalArgumentException`() = runBlocking {
        val result = MediaScanner(context).scan(DEVICE_TREE_URI)
        val names = result.files.map { it.displayName }
        assertTrue("IMG_20240501_1200.jpg" in names)
        assertTrue("VID_20240501_1205.mp4" in names)
        assertTrue("notes.txt" in names)
        assertEquals(3, result.files.size)
        assertEquals(listOf("Camera"), result.subFolders)
    }

    @Test
    fun `root document id is resolved from the tree URI, never hand-built`() {
        assertEquals("primary:Pictures", DocumentsContract.getTreeDocumentId(DEVICE_TREE_URI))
    }

    @Test
    fun `nested folder contents are not scanned, folder is listed as summary`() = runBlocking {
        FakeDocumentsProvider.addFile("primary:Pictures/Camera", "inner.jpg", "image/jpeg")
        val result = MediaScanner(context).scan(DEVICE_TREE_URI)
        assertTrue(result.files.none { it.displayName == "inner.jpg" })
    }

    @Test
    fun `empty folder scans to zero files without error`() = runBlocking {
        // Seed under the storage ROOT id ("primary:") so the tree the scanner
        // sees genuinely exists and is empty.
        FakeDocumentsProvider.addFolder("primary:", "EmptyPics")
        val result = MediaScanner(context).scan(
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AEmptyPics"),
        )
        assertEquals(0, result.files.size)
        assertEquals(0, result.subFolders.size)
    }

    @Test
    fun `file names with spaces and unicode survive the scan`() = runBlocking {
        FakeDocumentsProvider.addFile("primary:Pictures", "my photo (1) & café.jpg", "image/jpeg")
        val result = MediaScanner(context).scan(DEVICE_TREE_URI)
        assertTrue("my photo (1) & café.jpg" in result.files.map { it.displayName })
    }

    @Test
    fun `revoked permission surfaces a typed scan failure instead of crashing`() = runBlocking {
        FakeDocumentsProvider.failQueriesWithSecurityException = true
        try {
            MediaScanner(context).scan(DEVICE_TREE_URI)
            fail("expected ScanFailedException")
        } catch (e: ScanFailedException) {
            assertEquals(ScanFailedException.Reason.PERMISSION_REVOKED, e.reason)
            assertTrue(e.cause is SecurityException)
        }
    }

    @Test
    fun `progress callback reports increasing counts`() = runBlocking {
        val seen = mutableListOf<Int>()
        MediaScanner(context).scan(DEVICE_TREE_URI) { seen += it }
        assertEquals(3, seen.last())
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MoverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.install(contextProvider = {})
        FakeDocumentsProvider.reset()
        FakeDocumentsProvider.addFolder("primary", "Pics")
        FakeDocumentsProvider.addFile("primary:Pics", "photo.jpg", "image/jpeg", content = ByteArray(2048) { it.toByte() })
    }

    private val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APics")

    @Test
    fun `ensureFolder creates nested destination folders`() = runBlocking {
        val mover = Mover(context)
        val rootId = Mover.treeRootDocId(treeUri)
        val created = mover.ensureFolder(treeUri, rootId, listOf("Images", "2024-05"))
        assertTrue(File(FakeDocumentsProvider.root, "Pics/Images/2024-05").isDirectory)
        assertTrue(created.endsWith("2024-05"))
        // Second call reuses the existing folder.
        val again = mover.ensureFolder(treeUri, rootId, listOf("Images", "2024-05"))
        assertEquals(created, again)
    }

    @Test
    fun `moveOne moves the file and removes the source`() = runBlocking {
        val mover = Mover(context)
        val rootId = Mover.treeRootDocId(treeUri)
        val images = mover.ensureFolder(treeUri, rootId, listOf("Images"))
        val outcome = mover.moveOne(
            treeUri,
            sourceDocId = "primary:Pics/photo.jpg",
            destFolderDocId = images,
            displayName = "photo.jpg",
            mime = "image/jpeg",
            action = PlanAction.MOVE,
        )
        assertTrue(outcome is Mover.Outcome.Moved)
        val dest = File(FakeDocumentsProvider.root, "Pics/Images/photo.jpg")
        assertTrue(dest.exists())
        assertEquals(2048L, dest.length())
        assertFalse(File(FakeDocumentsProvider.root, "Pics/photo.jpg").exists())
    }

    @Test
    fun `moveOne with an existing name and SKIP policy skips`() = runBlocking {
        val mover = Mover(context)
        val rootId = Mover.treeRootDocId(treeUri)
        val outcome = mover.moveOne(
            treeUri, "primary:Pics/photo.jpg", rootId, "photo.jpg", "image/jpeg",
            PlanAction.MOVE,
        )
        assertTrue(outcome is Mover.Outcome.SkippedDuplicate)
        assertTrue(File(FakeDocumentsProvider.root, "Pics/photo.jpg").exists())
    }

    @Test
    fun `moveOne with RENAME policy produces numbered name`() = runBlocking {
        val mover = Mover(context)
        val rootId = Mover.treeRootDocId(treeUri)
        val images = mover.ensureFolder(treeUri, rootId, listOf("Images"))
        // A DIFFERENT photo.jpg already lives in the destination folder.
        FakeDocumentsProvider.addFile("primary:Pics/Images", "photo.jpg", "image/jpeg", content = ByteArray(8))
        val outcome = mover.moveOne(
            treeUri, "primary:Pics/photo.jpg", images, "photo.jpg", "image/jpeg",
            PlanAction.RENAME,
        )
        assertTrue("outcome=$outcome", outcome is Mover.Outcome.Moved)
        // Rename-move: the source is gone, the content lives under the new name.
        assertFalse(File(FakeDocumentsProvider.root, "Pics/photo.jpg").exists())
        // The rename convention skips the taken base name: photo (2).jpg.
        assertEquals(2048L, File(FakeDocumentsProvider.root, "Pics/Images/photo (2).jpg").length())
        // The pre-existing destination file is untouched.
        assertEquals(8L, File(FakeDocumentsProvider.root, "Pics/Images/photo.jpg").length())
    }

    @Test
    fun `moveOne with RENAME inside the same folder is a no-op skip`() = runBlocking {
        val mover = Mover(context)
        val rootId = Mover.treeRootDocId(treeUri)
        val outcome = mover.moveOne(
            treeUri, "primary:Pics/photo.jpg", rootId, "photo.jpg", "image/jpeg",
            PlanAction.RENAME,
        )
        assertTrue(outcome is Mover.Outcome.SkippedDuplicate)
        assertTrue(File(FakeDocumentsProvider.root, "Pics/photo.jpg").exists())
        assertFalse(File(FakeDocumentsProvider.root, "Pics/photo (2).jpg").exists())
    }

    @Test
    fun `moveOne with REPLACE policy overwrites destination`() = runBlocking {
        val mover = Mover(context)
        FakeDocumentsProvider.addFolder("primary:Pics", "Images")
        FakeDocumentsProvider.addFile("primary:Pics/Images", "photo.jpg", "image/jpeg", content = ByteArray(16))
        val outcome = mover.moveOne(
            treeUri, "primary:Pics/photo.jpg", "primary:Pics/Images", "photo.jpg", "image/jpeg",
            PlanAction.REPLACE,
        )
        assertTrue(outcome is Mover.Outcome.Moved)
        assertEquals(2048L, File(FakeDocumentsProvider.root, "Pics/Images/photo.jpg").length())
    }

    @Test
    fun `copyBack restores the file to its original folder`() = runBlocking {
        val mover = Mover(context)
        val rootId = Mover.treeRootDocId(treeUri)
        val images = mover.ensureFolder(treeUri, rootId, listOf("Images"))
        mover.moveOne(treeUri, "primary:Pics/photo.jpg", images, "photo.jpg", "image/jpeg", PlanAction.MOVE)
        val outcome = mover.copyBack(treeUri, "primary:Pics/Images/photo.jpg", "primary:Pics", "photo.jpg", "image/jpeg")
        assertTrue(outcome is Mover.Outcome.Moved)
        assertTrue(File(FakeDocumentsProvider.root, "Pics/photo.jpg").exists())
    }
}
