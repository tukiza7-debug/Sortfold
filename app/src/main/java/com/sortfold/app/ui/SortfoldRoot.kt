package com.sortfold.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sortfold.app.AppContainer
import com.sortfold.app.MainActivity
import com.sortfold.app.ui.navigation.Destination
import com.sortfold.app.ui.navigation.topLevelDestinations

/**
 * App chrome. Compact width: a bottom bar passed into each screen's single
 * Scaffold. Medium/expanded: a persistent rail rendered beside the content.
 * Screens own exactly one Scaffold each, so insets are consumed once.
 */
@Composable
fun SortfoldRoot(
    container: AppContainer,
    activity: MainActivity,
    windowSizeClass: WindowSizeClass,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Destination.Home.route
    val expanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val medium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium

    // Deep link from completion notifications: open the job result screen once.
    val pendingJob by activity.pendingJobId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJob) {
        pendingJob?.let {
            navController.navigate(Destination.JobResult.build(it))
            activity.pendingJobId.value = null
        }
    }

    val isTopLevel = topLevelDestinations.any { it.destination.route == currentRoute }

    @Composable
    fun bottomBar() {
        if (!isTopLevel) return
        NavigationBar(modifier = Modifier.testTag("bottom_nav")) {
            topLevelDestinations.forEach { dest ->
                NavigationBarItem(
                    selected = currentRoute == dest.destination.route,
                    onClick = { navigateTop(navController, dest.destination.route) },
                    icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                    label = { Text(stringResource(dest.labelRes)) },
                )
            }
        }
    }

    @Composable
    fun rail() {
        NavigationRail {
            topLevelDestinations.forEach { dest ->
                NavigationRailItem(
                    selected = currentRoute == dest.destination.route,
                    onClick = { navigateTop(navController, dest.destination.route) },
                    icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                    label = { Text(stringResource(dest.labelRes)) },
                )
            }
        }
    }

    when {
        // Expanded (tablets, unfolded foldables): permanent rail, wider content.
        expanded -> Row(Modifier.fillMaxSize()) {
            rail()
            SortfoldNavHost(container, activity, navController, Modifier.fillMaxSize(), expanded = true, bottomBar = {})
        }
        // Medium (portrait tablets, large phones landscape): navigation rail.
        medium -> Row(Modifier.fillMaxSize()) {
            rail()
            SortfoldNavHost(container, activity, navController, Modifier.fillMaxSize(), expanded = false, bottomBar = {})
        }
        // Compact: single pane; the bottom bar is hosted by each screen's Scaffold.
        else -> SortfoldNavHost(
            container, activity, navController,
            Modifier.fillMaxSize(),
            expanded = false,
            bottomBar = { bottomBar() },
        )
    }
}

private fun navigateTop(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
