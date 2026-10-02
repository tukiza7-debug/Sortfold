package com.sortfold.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sortfold.app.AppContainer
import com.sortfold.app.R
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.ui.common.EmptyState
import com.sortfold.app.ui.common.SectionHeader
import com.sortfold.app.ui.theme.Spacing
import com.sortfold.app.ui.wizard.jobStatusLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentJobs = container.database.sortJobDao().observeRecent(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun acknowledgeCrash() {
        viewModelScope.launch {
            container.settingsRepository.setCrashedLastRun(false)
        }
    }

    fun dismissBatteryOffer() {
        viewModelScope.launch {
            container.settingsRepository.setJobKilledBySystem(false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    expanded: Boolean,
    onStartSort: () -> Unit,
    onOpenJob: (Long) -> Unit,
    onOpenErrors: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val vm: HomeViewModel = viewModel(factory = simpleFactory { HomeViewModel(container) })
    val settings by vm.settings.collectAsStateWithLifecycle()
    val jobs by vm.recentJobs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        bottomBar = bottomBar,
    ) { padding ->
        // Expanded layouts get a readable max content width, centered.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.then(
                    if (expanded) Modifier.widthIn(max = Spacing.maxContentWidth) else Modifier.fillMaxSize(),
                ),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.md,
                    bottom = Spacing.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item {
                    Text(
                        stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Crash banner: "the app closed unexpectedly" from the last launch.
                if (settings?.crashedLastRun == true) {
                    item(key = "crash") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        ) {
                            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    Icon(
                                        Icons.Filled.BugReport,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        stringResource(R.string.home_crash_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                                Text(
                                    stringResource(R.string.home_crash_body),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    TextButton(onClick = onOpenErrors) { Text(stringResource(R.string.home_crash_view)) }
                                    TextButton(onClick = { vm.acknowledgeCrash() }) { Text(stringResource(R.string.action_dismiss)) }
                                }
                            }
                        }
                    }
                }

                // Battery exemption is ONLY offered after the system killed a long job.
                if (settings?.jobKilledBySystem == true) {
                    item(key = "battery") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.battery_body),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    TextButton(onClick = {
                                        val i = android.content.Intent(
                                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        ).setData(android.net.Uri.parse("package:${context.packageName}"))
                                        runCatching { context.startActivity(i) }
                                    }) { Text(stringResource(R.string.battery_allow)) }
                                    TextButton(onClick = { vm.dismissBatteryOffer() }) {
                                        Text(stringResource(R.string.action_dismiss))
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "cta") {
                    Button(
                        onClick = onStartSort,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.home_start_cta))
                    }
                }

                item(key = "recent-header") { SectionHeader(stringResource(R.string.home_recent)) }

                if (jobs.isEmpty()) {
                    item(key = "recent-empty") {
                        EmptyState(
                            title = stringResource(R.string.home_empty_title),
                            description = stringResource(R.string.home_empty_body),
                        )
                    }
                } else {
                    items(jobs, key = { it.id }) { job ->
                        RecentJobCard(job, onClick = { onOpenJob(job.id) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentJobCard(job: SortJobEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(jobStatusLabel(job.status), style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.job_files_done, job.totalFiles, job.doneFiles, job.totalFiles),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Minimal generic ViewModel factory without pulling in a DI framework. */
fun simpleFactory(block: () -> ViewModel): androidx.lifecycle.ViewModelProvider.Factory =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = block() as T
    }
