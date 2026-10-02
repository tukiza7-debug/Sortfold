package com.sortfold.app.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortfold.app.AppContainer
import com.sortfold.app.R
import com.sortfold.app.ui.common.Formatters
import com.sortfold.app.ui.common.SectionHeader

/** Result screen opened from the completion notification or history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobResultScreen(container: AppContainer, jobId: Long, onDone: () -> Unit) {
    val job by container.database.sortJobDao().observeById(jobId)
        .collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.result_screen_title)) })
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val j = job
            if (j == null) {
                Text(stringResource(R.string.job_not_found))
                return@Column
            }
            Text(jobStatusLabel(j.status), style = MaterialTheme.typography.headlineSmall)
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeaderRow(stringResource(R.string.result_details))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.preview_move_count), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(pluralStringResource(R.plurals.job_files_done, j.totalFiles, j.doneFiles, j.totalFiles))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.preview_total_size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.bytes(context, j.doneBytes))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.result_modes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            j.modesCsv.split(',').map { modeNameOf(it) }.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    j.message?.let { m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.result_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(m)
                        }
                    }
                }
            }

            if (j.status in setOf("PAUSED", "PARTIAL")) {
                Button(
                    onClick = { com.sortfold.app.work.WorkScheduler.resumeJob(context, jobId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_resume)) }
            }
            if (j.status != "UNDONE" && j.status != "UNDOING" && j.doneFiles > 0) {
                OutlinedButton(
                    onClick = { com.sortfold.app.work.WorkScheduler.undoLast(context, jobId) {} },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_undo)) }
            }
            Text(
                stringResource(R.string.result_undo_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun modeNameOf(mode: String): String = when (mode) {
    "FILE_TYPE" -> stringResource(R.string.mode_file_type)
    "DATE_TAKEN" -> stringResource(R.string.mode_date_taken)
    "SOURCE_APP" -> stringResource(R.string.mode_source_app)
    "RESOLUTION" -> stringResource(R.string.mode_resolution)
    "SIZE" -> stringResource(R.string.mode_size)
    "EXTENSION" -> stringResource(R.string.mode_extension)
    "NAME_PATTERN" -> stringResource(R.string.mode_name_pattern)
    else -> mode
}
