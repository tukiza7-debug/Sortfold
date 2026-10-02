package com.sortfold.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.sortfold.app.ui.common.StaggeredEntry
import com.sortfold.app.ui.home.simpleFactory
import com.sortfold.app.ui.wizard.jobStatusLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(container: AppContainer) : ViewModel() {
    val jobs = container.database.sortJobDao().observeRecent(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** Full operation history; every job can be opened, resumed or undone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    container: AppContainer,
    onOpenJob: (Long) -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val vm: HistoryViewModel = viewModel(factory = simpleFactory { HistoryViewModel(container) })
    val allJobs by vm.jobs.collectAsStateWithLifecycle()
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }

    // Live search: filter by the localized status label, auto tag or date text.
    val jobs = allJobs.filter { job ->
        val q = query.trim()
        q.isEmpty() ||
            jobStatusLabel(job.status).contains(q, ignoreCase = true) ||
            job.isAuto && q.startsWith("a", ignoreCase = true) ||
            com.sortfold.app.ui.common.Formatters.date(job.createdAt).contains(q, ignoreCase = true)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.history_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = com.sortfold.app.ui.theme.Spacing.lg, vertical = com.sortfold.app.ui.theme.Spacing.sm),
            )
            if (jobs.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.history_empty_title),
                    description = stringResource(R.string.history_empty_body),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = com.sortfold.app.ui.theme.Spacing.lg,
                        end = com.sortfold.app.ui.theme.Spacing.lg,
                        top = com.sortfold.app.ui.theme.Spacing.md,
                        bottom = com.sortfold.app.ui.theme.Spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(com.sortfold.app.ui.theme.Spacing.sm),
                ) {
                    items(jobs, key = { it.id }) { job ->
                        val index = jobs.indexOf(job).coerceAtMost(9)
                        StaggeredEntry(index = index) {
                            HistoryCard(
                                job,
                                onClick = { onOpenJob(job.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(job: SortJobEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(jobStatusLabel(job.status), style = MaterialTheme.typography.titleMedium)
                if (job.isAuto) {
                    Text(
                        stringResource(R.string.history_auto_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                pluralStringResource(R.plurals.job_files_done, job.totalFiles, job.doneFiles, job.totalFiles),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                com.sortfold.app.ui.common.Formatters.date(job.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
