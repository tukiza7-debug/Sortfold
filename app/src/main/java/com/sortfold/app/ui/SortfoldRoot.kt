package com.sortfold.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
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

/** Which route groups show the top-level navigation. */
private val topLevels = setOf("home", "history", "errors", "settings")

@Composable
fun SortfoldRoot(
    container: AppContainer,
    activity: MainActivity,
    windowSizeClass: WindowSizeClass,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Destination.Home.route
    val showNav = currentRoute in topLevels
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

    when {
        // Expanded (tablets, unfolded foldables): permanent rail, wider content.
        expanded -> Row(Modifier.fillMaxSize()) {
            if (showNav) {
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
            SortfoldNavHost(container, activity, navController, Modifier.fillMaxSize(), expanded = true)
        }
        // Medium (portrait tablets, large phones landscape): navigation rail.
        medium -> Row(Modifier.fillMaxSize()) {
            if (showNav) {
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
            SortfoldNavHost(container, activity, navController, Modifier.fillMaxSize(), expanded = false)
        }
        // Compact: single pane with bottom navigation.
        else -> Scaffold(
            bottomBar = {
                if (showNav) {
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
            },
        ) { padding ->
            SortfoldNavHost(
                container, activity, navController,
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                expanded = false,
            )
        }
    }
}

private fun navigateTop(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
