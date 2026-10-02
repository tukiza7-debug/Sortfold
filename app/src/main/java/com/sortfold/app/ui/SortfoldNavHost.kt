package com.sortfold.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.sortfold.app.ui.wizard.JobResultScreen
import com.sortfold.app.ui.wizard.WizardScreen
import com.sortfold.app.util.Locales
import kotlinx.coroutines.launch

@Composable
fun SortfoldNavHost(
    container: AppContainer,
    activity: MainActivity,
    navController: NavHostController,
    modifier: Modifier,
    expanded: Boolean,
) {
    var navigatedToOnboarding by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotion.current

    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier,
        // Fade-through step motion: short, standard easing, honors reduced motion.
        enterTransition = { if (reducedMotion) fadeIn(tween(1)) else fadeIn(tween(220)) },
        exitTransition = { if (reducedMotion) fadeOut(tween(1)) else fadeOut(tween(150)) },
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
                onOpenErrors = { navController.navigate(Destination.Errors.route) },
            )
        }
        composable(Destination.History.route) {
            HistoryScreen(
                container = container,
                onOpenJob = { navController.navigate(Destination.JobResult.build(it)) },
            )
        }
        composable(Destination.Errors.route) {
            ErrorLibraryScreen(
                container = container,
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
        composable(Destination.Settings.route) {
            SettingsScreen(
                container = container,
                onOpenLanguage = { navController.navigate(Destination.Language.route) },
                onOpenRules = { navController.navigate(Destination.AutoRules.route) },
                onOpenAbout = { navController.navigate(Destination.About.route) },
            )
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
            WizardScreen(container = container, expanded = expanded, onExit = { navController.popBackStack() })
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
