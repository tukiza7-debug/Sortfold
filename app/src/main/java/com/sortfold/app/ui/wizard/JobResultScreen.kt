package com.sortfold.app.ui.wizard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortfold.app.AppContainer
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.R
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.ui.common.Formatters
import com.sortfold.app.ui.common.SectionHeader
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.sortfold.app.ui.theme.LocalReducedMotion
import com.sortfold.app.ui.theme.Motion
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import androidx.compose.animation.core.Spring

/** Loading / ready state so a slow Room read never flashes "not found". */
private sealed interface JobUi {
    data object Loading : JobUi
    data class Ready(val job: SortJobEntity?) : JobUi
}

/** Result screen opened from the completion notification or history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobResultScreen(container: AppContainer, jobId: Long, onDone: () -> Unit) {
    val context = LocalContext.current

    // BUG-22: the raw flow's first emission used to be indistinguishable from
    // "no such job", flashing the not-found state on every open. A distinct
    // Loading state fixes it.
    val ui by remember(jobId) {
        container.database.sortJobDao().observeById(jobId).map { JobUi.Ready(it) as JobUi }
    }.collectAsStateWithLifecycle(initialValue = JobUi.Loading)

    // BUG-23: undo previously fired with no user feedback at all.
    var undoBusy by rememberSaveable { mutableStateOf(false) }
    var undoResult by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }

    // Collapsible skipped/failed lists with explicit reasons (1.1.0 Part B4).
    val problems by remember(jobId) {
        flow {
            val skipped = container.database.moveLogDao().byJobStatusPaged(jobId, "SKIPPED", 50, 0)
            val failed = container.database.moveLogDao().byJobStatusPaged(jobId, "FAILED", 50, 0)
            emit(skipped to failed)
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList<MoveLogEntity>() to emptyList<MoveLogEntity>())
    var showSkipped by rememberSaveable { mutableStateOf(false) }
    var showFailed by rememberSaveable { mutableStateOf(false) }

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
            when (val state = ui) {
                JobUi.Loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is JobUi.Ready -> {
                    val j = state.job
                    if (j == null) {
                        Text(stringResource(R.string.job_not_found))
                    } else {
                        OutcomeIcon(status = j.status)
                        Text(jobStatusLabel(j.status), style = MaterialTheme.typography.headlineSmall)
                        Card {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SectionHeader(stringResource(R.string.result_details))
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

                        if (problems.first.isNotEmpty()) {
                            ProblemSection(
                                title = stringResource(R.string.preview_skip_count) + " (" + problems.first.size + ")",
                                rows = problems.first,
                                expanded = showSkipped,
                                onToggle = { showSkipped = !showSkipped },
                            )
                        }
                        if (problems.second.isNotEmpty()) {
                            ProblemSection(
                                title = stringResource(R.string.result_failed_title) + " (" + problems.second.size + ")",
                                rows = problems.second,
                                expanded = showFailed,
                                onToggle = { showFailed = !showFailed },
                            )
                        }

                        if (j.status in setOf("PAUSED", "PARTIAL")) {
                            Button(
                                onClick = { com.sortfold.app.work.WorkScheduler.resumeJob(context, jobId) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.action_resume)) }
                        }
                        if (j.status != "UNDONE" && j.status != "UNDOING" && j.doneFiles > 0) {
                            OutlinedButton(
                                onClick = {
                                    undoBusy = true
                                    com.sortfold.app.work.WorkScheduler.undoLast(context, jobId) { result ->
                                        undoBusy = false
                                        undoResult = result.restored to result.failed
                                    }
                                },
                                enabled = !undoBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.action_undo)) }
                        }
                        if (undoBusy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        undoResult?.let { (restored, failed) ->
                            Card {
                                Text(
                                    stringResource(R.string.result_undo_summary, restored, failed),
                                    Modifier.padding(16.dp),
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.result_undo_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Collapsible list of skipped/failed rows with the stored reason. */
@Composable
private fun ProblemSection(
    title: String,
    rows: List<MoveLogEntity>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                androidx.compose.material3.IconButton(onClick = onToggle) {
                    androidx.compose.material3.Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }
            if (expanded) {
                rows.forEach { row ->
                    Text(
                        row.displayName + (row.detail?.let { " — $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Animated outcome icon: springs in on arrival, picks the glyph from the job
 * status. Static instantly when reduced motion is on.
 */
@Composable
private fun OutcomeIcon(status: String, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val (image, tint, description) = when (status) {
        "DONE" -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, stringResource(R.string.result_done_title))
        "FAILED", "CANCELLED" -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, stringResource(R.string.result_failed_title))
        else -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.tertiary, stringResource(R.string.result_partial_title))
    }
    var started by remember { mutableStateOf(reduced) }
    androidx.compose.runtime.LaunchedEffect(Unit) { started = true }
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.4f,
        animationSpec = if (reduced) {
            androidx.compose.animation.core.tween(1)
        } else {
            Motion.entrySpring<Float>()
        },
        label = "outcome-scale",
    )
    androidx.compose.material3.Icon(
        image,
        contentDescription = description,
        tint = tint,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(56.dp),
    )
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
