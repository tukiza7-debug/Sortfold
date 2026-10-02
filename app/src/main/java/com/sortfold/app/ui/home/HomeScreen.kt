package com.sortfold.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
}

@Composable
fun HomeScreen(
    container: AppContainer,
    expanded: Boolean,
    onStartSort: () -> Unit,
    onOpenJob: (Long) -> Unit,
    onOpenErrors: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = simpleFactory { HomeViewModel(container) })
    val settings by vm.settings.collectAsStateWithLifecycle()
    val jobs by vm.recentJobs.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Crash banner: "the app closed unexpectedly" from the last launch.
        if (settings?.crashedLastRun == true) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onOpenErrors) { Text(stringResource(R.string.home_crash_view)) }
                            TextButton(onClick = { vm.acknowledgeCrash() }) { Text(stringResource(R.string.action_dismiss)) }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onStartSort,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.home_start_cta))
            }
        }

        item { SectionHeader(stringResource(R.string.home_recent)) }

        if (jobs.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    description = stringResource(R.string.home_empty_body),
                )
            }
        } else {
            items(jobs, key = { it.id }) { job ->
                RecentJobCard(job, onClick = { onOpenJob(job.id) })
            }
        }
    }
}

@Composable
private fun RecentJobCard(job: SortJobEntity, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
