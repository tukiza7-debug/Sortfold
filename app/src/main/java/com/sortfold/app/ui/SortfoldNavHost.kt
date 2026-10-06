package com.sortfold.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sortfold.app.AppContainer
import com.sortfold.app.MainActivity
import com.sortfold.app.ui.errors.ErrorDetailScreen
import com.sortfold.app.ui.errors.ErrorLibraryScreen
import com.sortfold.app.ui.history.HistoryScreen
import com.sortfold.app.ui.home.HomeScreen
import com.sortfold.app.ui.navigation.Destination
import com.sortfold.app.ui.onboarding.OnboardingScreen
import com.sortfold.app.ui.settings.AboutScreen
import com.sortfold.app.ui.settings.AutoRulesScreen
import com.sortfold.app.ui.settings.LanguageScreen
import com.sortfold.app.ui.settings.SettingsScreen
import com.sortfold.app.ui.theme.LocalReducedMotion
import com.sortfold.app.ui.theme.Motion
import com.sortfold.app.ui.wizard.JobResultScreen
import com.sortfold.app.ui.wizard.WizardScreen
import com.sortfold.app.util.Locales
import androidx.compose.animation.scaleIn
import kotlinx.coroutines.launch

/** Routes reached from the Settings group; they share the shared-axis motion. */
private val settingsChildren = setOf(
    Destination.ErrorLibrary.route,
    Destination.ErrorDetail.route,
    Destination.Language.route,
    Destination.AutoRules.route,
    Destination.About.route,
)

@Composable
fun SortfoldNavHost(
    container: AppContainer,
    activity: MainActivity,
    navController: NavHostController,
    modifier: Modifier,
    expanded: Boolean,
    bottomBar: @Composable () -> Unit,
) {
    var navigatedToOnboarding by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotion.current
    val layoutDir = LocalLayoutDirection.current

    /**
     * Motion routing: shared-axis X for Settings sub-screens and wizard steps,
     * fade-through for everything else (bottom tabs, wizard exit, deep jumps).
     * Slides mirror in RTL (captured layout direction).
     */
    fun forwardDir() = if (layoutDir == LayoutDirection.Ltr) 1 else -1
    fun targetUsesSharedAxis(route: String?): Boolean =
        route in settingsChildren || route == Destination.Wizard.route

    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier,
        enterTransition = {
            if (reducedMotion) {
                fadeIn(tween(1))
            } else if (targetUsesSharedAxis(targetState.destination.route)) {
                slideInHorizontally(Motion.enter()) { it / 4 * forwardDir() } + fadeIn(Motion.enter())
            } else {
                // C-02 fade-through: enter fades in with an 8% scale-up.
                fadeIn(Motion.enter()) + scaleIn(Motion.enter(), initialScale = 0.92f)
            }
        },
        exitTransition = {
            if (reducedMotion) {
                fadeOut(tween(1))
            } else if (targetUsesSharedAxis(initialState.destination.route)) {
                slideOutHorizontally(Motion.exit()) { -it / 4 * forwardDir() } + fadeOut(Motion.exit())
            } else {
                // C-02: top-level destinations fade out first, in 90 ms.
                fadeOut(Motion.fadeThroughExit())
            }
        },
    ) {
        composable(Destination.Onboarding.route) {
            OnboardingScreen(
                onDone = {
                    container.appScope.launch {
                        container.settingsRepository.setOnboardingDone(true)
                    }
                    navController.popBackStack(Destination.Home.route, inclusive = false)
                },
            )
        }
        composable(Destination.Home.route) {
            HomeScreen(
                container = container,
                expanded = expanded,
                onStartSort = { navController.navigate(Destination.Wizard.route) },
                onOpenJob = { navController.navigate(Destination.JobResult.build(it)) },
                onOpenErrors = { navController.navigate(Destination.ErrorLibrary.route) },
                bottomBar = bottomBar,
            )
        }
        composable(Destination.History.route) {
            HistoryScreen(
                container = container,
                onOpenJob = { navController.navigate(Destination.JobResult.build(it)) },
                bottomBar = bottomBar,
            )
        }
        composable(Destination.Settings.route) {
            SettingsScreen(
                container = container,
                onOpenLanguage = { navController.navigate(Destination.Language.route) },
                onOpenRules = { navController.navigate(Destination.AutoRules.route) },
                onOpenAbout = { navController.navigate(Destination.About.route) },
                onOpenErrorLibrary = { navController.navigate(Destination.ErrorLibrary.route) },
                bottomBar = bottomBar,
            )
        }
        composable(Destination.ErrorLibrary.route) {
            ErrorLibraryScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenDetail = { navController.navigate(Destination.ErrorDetail.build(it)) },
            )
        }
        composable(
            Destination.ErrorDetail.route,
            arguments = listOf(navArgument("errorId") { type = NavType.LongType }),
        ) { entry ->
            val errorId = entry.arguments?.getLong("errorId") ?: -1L
            ErrorDetailScreen(container = container, errorId = errorId, onBack = { navController.popBackStack() })
        }
        composable(Destination.Language.route) {
            LanguageScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Destination.AutoRules.route) {
            AutoRulesScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Destination.About.route) {
            AboutScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Destination.Wizard.route) {
            WizardScreen(
                container = container,
                expanded = expanded,
                onExit = { navController.popBackStack() },
                onOpenErrorLibrary = { navController.navigate(Destination.ErrorLibrary.route) },
            )
        }
        composable(
            Destination.JobResult.route,
            arguments = listOf(navArgument("jobId") { type = NavType.LongType }),
        ) { entry ->
            val jobId = entry.arguments?.getLong("jobId") ?: -1L
            JobResultScreen(container = container, jobId = jobId, onDone = { navController.popBackStack() })
        }
    }

    // First-run onboarding, max 3 pages, skippable.
    LaunchedEffect(Unit) {
        if (!navigatedToOnboarding) {
            val settings = container.settingsRepository.snapshot()
            if (!settings.onboardingDone) {
                navigatedToOnboarding = true
                navController.navigate(Destination.Onboarding.route)
            }
        }
    }

    // Apply the saved language choice when the app process starts fresh.
    LaunchedEffect(Unit) {
        val settings = container.settingsRepository.snapshot()
        if (settings.languageTag != null && Locales.currentTag() == null) {
            Locales.apply(settings.languageTag)
        }
    }
}
