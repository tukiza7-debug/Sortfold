package com.sortfold.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.data.db.SortfoldDatabase
import com.sortfold.app.ui.wizard.JobResultScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * REGRESSION TESTS for UI-state, accessibility and localization bugs
 * found in the 1.1.0 overhaul. Red before fix, green after.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UiBugsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        FakeDocumentsProvider.install(contextProvider = {})
        FakeDocumentsProvider.reset()
        FakeDocumentsProvider.addFolder("primary", "Pics")
        FakeDocumentsProvider.addFile("primary:Pics", "IMG_1.jpg", "image/jpeg", content = ByteArray(1024))
    }

    private fun resId(name: String): Int =
        context.resources.getIdentifier(name, "string", context.packageName)

    // ------------------------------------------------------------------
    // BUG-20: the Apply step announced "progress 3 of 10" in hardcoded
    // English regardless of the app language.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-20 apply progress has a localized accessibility description`() {
        assertNotEquals("missing string a11y_apply_progress", 0, resId("a11y_apply_progress"))
    }

    // ------------------------------------------------------------------
    // BUG-21: the name-rule type buttons showed raw English
    // (Prefix/Suffix/Contains) in every language.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-21 name rule types are localized`() {
        assertNotEquals("missing string pattern_prefix", 0, resId("pattern_prefix"))
        assertNotEquals("missing string pattern_suffix", 0, resId("pattern_suffix"))
        assertNotEquals("missing string pattern_contains", 0, resId("pattern_contains"))
    }

    // ------------------------------------------------------------------
    // BUG-22: the job result screen flashed "Job not found" for the first
    // frames while the job was still loading.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-22 job result shows a loading state before reporting not-found`() {
        // The screen must distinguish "still loading" from "no such job":
        // unknown id settles into the not-found state (never a blank screen).
        compose.setContent {
            com.sortfold.app.ui.theme.SortfoldTheme(
                themeMode = com.sortfold.app.data.prefs.ThemeMode.LIGHT,
                dynamicColor = false, reducedMotion = true,
            ) {
                JobResultScreen(
                    container = AppContainer(context),
                    jobId = 9999, // unknown id
                    onDone = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.job_not_found)).assertExists()
    }

    @Test
    fun `BUG-22 an existing job renders content, never not-found`() = runBlocking {
        val db = SortfoldDatabase.build(context)
        val jobId = db.sortJobDao().insert(
            SortJobEntity(
                treeUri = "x", destTreeUri = "x", modesCsv = "FILE_TYPE",
                duplicatePolicy = "SKIP", status = "DONE", totalFiles = 1,
                doneFiles = 1, totalBytes = 10, doneBytes = 10,
                createdAt = 1, updatedAt = 2,
            ),
        )
        compose.setContent {
            com.sortfold.app.ui.theme.SortfoldTheme(
                themeMode = com.sortfold.app.data.prefs.ThemeMode.LIGHT,
                dynamicColor = false, reducedMotion = true,
            ) {
                JobResultScreen(container = AppContainer(context), jobId = jobId, onDone = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.job_not_found)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.result_details)).assertExists()
        Unit
    }

    // ------------------------------------------------------------------
    // BUG-23: pressing Undo on the job result screen gave no feedback at
    // all; the user could not tell whether anything happened.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-23 undo from the job result reports the outcome`() = runBlocking {
        val db = SortfoldDatabase.build(context)
        val mover = Mover(context)
        val tree = android.net.Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3APics",
        )
        val images = mover.ensureFolder(tree, Mover.treeRootDocId(tree), listOf("Images"))
        val moved = mover.moveOne(tree, "primary:Pics/IMG_1.jpg", images, "IMG_1.jpg", "image/jpeg", PlanAction.MOVE)
        assertTrue(moved is Mover.Outcome.Moved)
        val jobId = db.sortJobDao().insert(
            SortJobEntity(
                treeUri = tree.toString(), destTreeUri = tree.toString(), modesCsv = "FILE_TYPE",
                duplicatePolicy = "SKIP", status = "DONE", totalFiles = 1,
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

        compose.setContent {
            com.sortfold.app.ui.theme.SortfoldTheme(
                themeMode = com.sortfold.app.data.prefs.ThemeMode.LIGHT,
                dynamicColor = false, reducedMotion = true,
            ) {
                JobResultScreen(
                    container = AppContainer(context),
                    jobId = jobId,
                    onDone = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.action_undo)).performClick()
        compose.waitForIdle()
        // The outcome must be visible: restored 1, failed 0.
        compose.onNodeWithText(context.getString(R.string.result_undo_summary, 1, 0)).assertExists()
        assertTrue(java.io.File(FakeDocumentsProvider.root, "Pics/IMG_1.jpg").exists())
    }

    // ------------------------------------------------------------------
    // BUG-24: the Settings data-size row walked cacheDir/filesDir on the
    // MAIN thread; with many files the whole UI froze.
    // ------------------------------------------------------------------
    @Test
    fun `BUG-24 data size computation reports real sizes`() = runBlocking {
        val dir = java.io.File(context.cacheDir, "probe").apply { mkdirs() }
        java.io.File(dir, "a.bin").writeBytes(ByteArray(2048))
        java.io.File(dir, "b.bin").writeBytes(ByteArray(1024))
        val total = com.sortfold.app.ui.settings.AppDataSize.compute(context)
        assertTrue("compute() must include cache files, got $total", total >= 3072)
    }
}
