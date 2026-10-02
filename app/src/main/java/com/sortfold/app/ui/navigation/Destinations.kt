package com.sortfold.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.sortfold.app.R

sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")
    data object Home : Destination("home")
    data object History : Destination("history")
    data object Errors : Destination("errors")
    data object ErrorDetail : Destination("errors/{errorId}") {
        fun build(id: Long) = "errors/$id"
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

val topLevelDestinations = listOf(
    TopLevelDestination(Destination.Home, R.string.nav_home, Icons.Filled.Folder),
    TopLevelDestination(Destination.History, R.string.nav_history, Icons.AutoMirrored.Filled.List),
    TopLevelDestination(Destination.Errors, R.string.nav_errors, Icons.Filled.Report),
    TopLevelDestination(Destination.Settings, R.string.nav_settings, Icons.Filled.Settings),
)
