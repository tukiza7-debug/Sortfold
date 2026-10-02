package com.sortfold.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.sortfold.app.R

sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")
    data object Home : Destination("home")
    data object History : Destination("history")

    /** Diagnostics sub-screen of Settings; deep-linked from errors and crashes. */
    data object ErrorLibrary : Destination("settings/errors")
    data object ErrorDetail : Destination("settings/errors/{errorId}") {
        fun build(id: Long) = "settings/errors/$id"
    }
    data object Settings : Destination("settings")
    data object Language : Destination("settings/language")
    data object AutoRules : Destination("settings/rules")
    data object About : Destination("settings/about")
    data object Wizard : Destination("wizard")
    data object JobResult : Destination("jobs/{jobId}") {
        fun build(id: Long) = "jobs/$id"
    }
}

data class TopLevelDestination(val destination: Destination, val labelRes: Int, val icon: ImageVector)

/** Exactly three top-level destinations: Main, History, Settings. */
val topLevelDestinations = listOf(
    TopLevelDestination(Destination.Home, R.string.nav_home, Icons.Filled.Folder),
    TopLevelDestination(Destination.History, R.string.nav_history, Icons.AutoMirrored.Filled.List),
    TopLevelDestination(Destination.Settings, R.string.nav_settings, Icons.Filled.Settings),
)
