package com.sortfold.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sortfold.app.data.prefs.ThemeMode
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.ui.home.HomeScreen
import com.sortfold.app.ui.settings.SettingsScreen
import com.sortfold.app.ui.theme.LocalReducedMotion
import com.sortfold.app.ui.theme.SortfoldTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Layout matrix (audit pass 7): the layout contract must hold across widths,
 * font scales and locales including RTL. Reduced motion keeps tests
 * deterministic. The screens run with a real container (Room + DataStore on
 * the Robolectric context).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h800dp")
class LayoutMatrixTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before
    fun initWorkManagerForTests() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(),
            Configuration.Builder().build(),
        )
    }

    private fun localizedContext(tag: String): android.content.Context {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val conf = android.content.res.Configuration(base.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(conf)
    }

    private fun content(tag: String, fontScale: Float, block: @Composable (AppContainer) -> Unit) {
        val ctx = localizedContext(tag)
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        compose.setContent {
            val activity = compose.activity
            CompositionLocalProvider(
                LocalContext provides ctx,
                LocalDensity provides Density(2f, fontScale),
                LocalReducedMotion provides true,
                LocalActivityResultRegistryOwner provides activity,
                LocalOnBackPressedDispatcherOwner provides activity,
            ) {
                SortfoldTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false, reducedMotion = true) {
                    block(container)
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode()
        val bounds = Rect(0f, 0f, root.size.width.toFloat(), root.size.height.toFloat())
        LayoutAssertions.assertLayoutContract(compose.onRoot(), bounds)
    }

    @Test
    fun `home holds the contract at 2x font in Indonesian`() {
        content("in", fontScale = 2f) { container ->
            HomeScreen(
                container = container,
                expanded = false,
                onStartSort = {}, onOpenJob = {}, onOpenErrors = {}, bottomBar = {},
            )
        }
    }

    @Test
    fun `home holds the contract in Arabic RTL`() {
        content("ar", fontScale = 1f) { container ->
            HomeScreen(
                container = container,
                expanded = false,
                onStartSort = {}, onOpenJob = {}, onOpenErrors = {}, bottomBar = {},
            )
        }
    }

    @Test
    fun `home holds the contract in Malay at 1_5x font`() {
        content("ms", fontScale = 1.5f) { container ->
            HomeScreen(
                container = container,
                expanded = false,
                onStartSort = {}, onOpenJob = {}, onOpenErrors = {}, bottomBar = {},
            )
        }
    }

    @Test
    fun `settings holds the contract in Chinese at 2x font`() {
        content("zh-CN", fontScale = 2f) { container ->
            SettingsScreen(
                container = container,
                onOpenLanguage = {}, onOpenRules = {}, onOpenAbout = {},
                onOpenErrorLibrary = {}, bottomBar = {},
            )
        }
    }

    // ------------------------------------------------------------------
    // 1.2.0: the capacity split surfaces. The wizard runs with the capacity
    // mode selected and the panel expanded — the estimate line, chips, custom
    // field and order selector must hold the no-overlap/no-clip contract at
    // small widths and large font scales, in both orientations.
    // ------------------------------------------------------------------

    private fun wizardAtCapacity(fontScale: Float) {
        val ctx = localizedContext("en")
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        // Pre-configure outside composition: capacity selected with the 2 GB preset.
        val vm = com.sortfold.app.ui.wizard.WizardViewModel(
            container,
            startStep = com.sortfold.app.ui.wizard.WizardStep.MODES,
        )
        vm.toggleMode(SortMode.CAPACITY, true)
        vm.chooseCapacity(2_000_000_000L, 1)
        compose.setContent {
            val activity = compose.activity
            CompositionLocalProvider(
                LocalContext provides ctx,
                LocalDensity provides Density(2f, fontScale),
                LocalReducedMotion provides true,
                LocalActivityResultRegistryOwner provides activity,
                LocalOnBackPressedDispatcherOwner provides activity,
            ) {
                SortfoldTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false, reducedMotion = true) {
                    com.sortfold.app.ui.wizard.WizardScreen(
                        container = container,
                        expanded = false,
                        onExit = {},
                        onOpenErrorLibrary = {},
                        vmOverride = vm,
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode()
        val bounds = Rect(0f, 0f, root.size.width.toFloat(), root.size.height.toFloat())
        LayoutAssertions.assertLayoutContract(compose.onRoot(), bounds)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h740dp")
    fun `wizard capacity panel holds the contract at 360dp and 1_3x font`() {
        wizardAtCapacity(fontScale = 1.3f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h800dp")
    fun `wizard capacity panel holds the contract at 411dp portrait`() {
        wizardAtCapacity(fontScale = 1f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h411dp")
    fun `wizard capacity panel holds the contract on a small tablet in landscape`() {
        wizardAtCapacity(fontScale = 1.3f)
    }
}
