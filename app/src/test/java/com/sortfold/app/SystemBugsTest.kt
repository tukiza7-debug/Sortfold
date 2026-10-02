package com.sortfold.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.sortfold.app.core.history.UndoManager
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.data.db.SortfoldDatabase
import com.sortfold.app.data.prefs.AppSettings
import com.sortfold.app.data.prefs.SettingsRepository
import com.sortfold.app.data.repo.GithubApiClient
import com.sortfold.app.data.repo.UpdateRepository
import com.sortfold.app.work.ProgressNotifications
import com.sortfold.app.work.WorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * REGRESSION TESTS for the 1.1.0 system bugs: prefs corruption, work
 * scheduling policy, update-check resilience, undo recovery, and the
 * wizard duplicate-job/state races. Red before the fix, green after.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SystemBugsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.install(contextProvider = {})
        FakeDocumentsProvider.reset()
    }

    // ------------------------------------------------------------------
    // BUG-08: a corrupt DataStore file crashed every settings read (and
    // therefore the whole app) instead of falling back to defaults.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-08 corrupt preferences file falls back to defaults`() = runBlocking {
        val dsDir = File(context.filesDir, "datastore").apply { mkdirs() }
        File(dsDir, "sortfold_settings.preferences_pb").writeBytes(byteArrayOf(0x00, 0x13, 0x37, 0x42))
        val repo = SettingsRepository(context)
        val settings = repo.settings.first() // must not throw
        assertEquals(AppSettings(), settings)
    }

    // ------------------------------------------------------------------
    // BUG-09: switching "automatic update checks" off never cancelled the
    // already-scheduled daily worker; it kept running forever.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-09 update-check worker is cancelled when auto-check is off`() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        val wm = WorkManager.getInstance(context)

        WorkScheduler.applyPolicy(context, AppSettings(autoCheckUpdates = true))
        val scheduled = wm.getWorkInfosForUniqueWork(WorkScheduler.UPDATE_CHECK_NAME).get()
        assertTrue("worker must be scheduled when enabled", scheduled.any { !it.state.isFinished })

        WorkScheduler.applyPolicy(context, AppSettings(autoCheckUpdates = false))
        val after = wm.getWorkInfosForUniqueWork(WorkScheduler.UPDATE_CHECK_NAME).get()
        assertTrue(
            "worker must be cancelled when disabled",
            after.none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING },
        )
    }

    // ------------------------------------------------------------------
    // BUG-10: any unexpected exception type inside the update check
    // escaped the repository and crashed the daily worker instead of
    // surfacing as a typed failure.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-10 unexpected updater exceptions become a typed failure`() = runBlocking {
        val brokenClient = object : GithubApiClient() {
            override suspend fun latestRelease(repo: String): GithubApiClient.LatestRelease {
                throw IllegalArgumentException("unexpected bad state")
            }
        }
        val repo = UpdateRepository(context, brokenClient, networkOnline = { true })
        val result = repo.check(force = true, lastCheckAt = 0)
        assertTrue(
            "expected Failure, got $result",
            result is UpdateRepository.CheckResult.Failure,
        )
        assertTrue(
            ((result as UpdateRepository.CheckResult.Failure).error.message ?: "").startsWith("unexpected"),
        )
    }

    // ------------------------------------------------------------------
    // BUG-11: if the process died while an undo was running, the job stayed
    // UNDOING forever and undo could never be retried.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-11 undo can be retried after a process death mid-undo`() = runBlocking {
        FakeDocumentsProvider.addFolder("primary", "Pics")
        FakeDocumentsProvider.addFile("primary:Pics", "IMG_1.jpg", "image/jpeg", content = ByteArray(1024))
        val db = SortfoldDatabase.build(context)
        val mover = Mover(context)
        val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APics")
        val images = mover.ensureFolder(tree, Mover.treeRootDocId(tree), listOf("Images"))
        val moved = mover.moveOne(tree, "primary:Pics/IMG_1.jpg", images, "IMG_1.jpg", "image/jpeg", PlanAction.MOVE)
        assertTrue(moved is Mover.Outcome.Moved)

        val jobId = db.sortJobDao().insert(
            SortJobEntity(
                treeUri = tree.toString(), destTreeUri = tree.toString(), modesCsv = "FILE_TYPE",
                duplicatePolicy = "SKIP", status = "UNDOING", totalFiles = 1,
                doneFiles = 1, totalBytes = 1024, doneBytes = 1024,
                createdAt = 1, updatedAt = 2,
            ),
        )
        db.moveLogDao().insert(
            MoveLogEntity(
                jobId = jobId, seq = 0, sourceDocId = "primary:Pics/IMG_1.jpg",
                displayName = "IMG_1.jpg", mime = "image/jpeg", destFolder = "Images",
                destDocId = (moved as Mover.Outcome.Moved).destUri.toString(),
                destName = "IMG_1.jpg", sizeBytes = 1024, status = "MOVED", detail = null,
            ),
        )

        val result = UndoManager(context, db).undoJob(jobId)
        assertEquals("a died-mid-undo job must remain undoable", 1, result.restored)
        assertEquals(0, result.failed)
        assertTrue(File(FakeDocumentsProvider.root, "Pics/IMG_1.jpg").exists())
    }

    // ------------------------------------------------------------------
    // BUG-16: "update available" notifications were posted on the job-done
    // channel; the dedicated app-updates channel was never used.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-16 update notification uses the dedicated updates channel`() {
        ProgressNotifications.ensureChannels(context)
        val notification = ProgressNotifications.updateAvailable(context, "1.1.0")
        assertEquals(ProgressNotifications.CHANNEL_UPDATES, notification.channelId)
        assertTrue(notification.extras.getString(android.app.Notification.EXTRA_TEXT).orEmpty().contains("1.1.0"))
    }

    // ------------------------------------------------------------------
    // BUG-17: the wizard's "apply saved defaults" init step raced the user:
    // if the user changed modes before the DataStore read landed, their
    // selection was silently overwritten.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-17 user mode choice is never clobbered by the defaults load`() = runBlocking {
        val container = AppContainer(context)
        container.settingsRepository.setDefaultSortMode(SortMode.SIZE)
        container.settingsRepository.snapshot() // warm DataStore

        val vm = com.sortfold.app.ui.wizard.WizardViewModel(container)
        awaitUntil { vm.defaultsApplied } // saved default (SIZE) has been applied

        // The user now changes the selection; the applied defaults must not
        // re-apply or survive where the user removed them.
        vm.toggleMode(SortMode.SIZE, false)
        vm.toggleMode(SortMode.DATE_TAKEN, true)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(
            "user selection must win over saved defaults: got ${'$'}{vm.selectedModes}",
            SortMode.DATE_TAKEN in vm.selectedModes && SortMode.SIZE !in vm.selectedModes,
        )
    }

    // ------------------------------------------------------------------
    // BUG-18: pressing Next from the Modes step twice created a second
    // PLANNED job (and orphaned plan rows) every single time.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-18 rebuilding the preview reuses the planned job`() = runBlocking {
        FakeDocumentsProvider.addFolder("primary", "Pics")
        FakeDocumentsProvider.addFile("primary:Pics", "a.jpg", "image/jpeg")
        FakeDocumentsProvider.addFile("primary:Pics", "b.jpg", "image/jpeg")
        val container = AppContainer(context)
        val vm = com.sortfold.app.ui.wizard.WizardViewModel(container)
        val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APics")

        vm.setFolder(tree, context)
        awaitUntil { vm.files.isNotEmpty() && !vm.scanning }
        vm.goToModes()
        vm.buildPreview(context)
        awaitUntil { vm.jobId != null }
        val firstJobId = vm.jobId

        vm.buildPreview(context) // user went back and pressed Next again
        awaitUntil { vm.planTotals.moveCount > 0 }
        val jobs = container.database.sortJobDao().recent(10)
        assertEquals("rebuilding the preview must not pile up PLANNED jobs", 1, jobs.size)
        assertEquals(firstJobId, jobs.single().id)
    }

    private fun awaitUntil(condition: () -> Boolean) {
        repeat(300) {
            val looper = Shadows.shadowOf(android.os.Looper.getMainLooper())
            looper.idle()
            if (condition()) return
            looper.idle() // run tasks the previous idle may have scheduled
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within timeout")
    }
}
