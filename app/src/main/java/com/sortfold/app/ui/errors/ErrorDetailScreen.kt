package com.sortfold.app.ui.errors

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortfold.app.AppContainer
import com.sortfold.app.R
import com.sortfold.app.data.db.ErrorEntity
import com.sortfold.app.error.PathMasker
import com.sortfold.app.ui.common.DestructiveConfirmDialog
import com.sortfold.app.ui.common.Formatters
import com.sortfold.app.ui.common.KeyValueRow
import com.sortfold.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Detail view of one error entry: masked message and trace, device and build
 * info, copy, delete, and the list of identical occurrences over time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorDetailScreen(container: AppContainer, errorId: Long, onBack: () -> Unit) {
    var error by remember { mutableStateOf<ErrorEntity?>(null) }
    LaunchedEffect(errorId) {
        error = container.database.errorDao().byId(errorId)
    }
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val maskPaths = settings?.includeFullPathsInExport == false

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    val occurrencesList: List<ErrorEntity> by remember(error) {
        error?.let { e ->
            container.database.errorDao()
                .observeOccurrences(e.module, e.type, e.message)
        } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.error_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            error?.let { e ->
                                clipboard.setText(
                                    AnnotatedString(
                                        buildString {
                                            append("${e.type}: ")
                                            appendLine(PathMasker.maskText(e.message, !maskPaths))
                                            e.stackTrace?.let { appendLine(PathMasker.maskText(it, !maskPaths)) }
                                        },
                                    ),
                                )
                                android.widget.Toast.makeText(
                                    context, R.string.error_copied, android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        val e = error
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (e == null) {
                item { Text(stringResource(R.string.job_not_found)) }
                return@LazyColumn
            }
            item { Text(e.type, style = MaterialTheme.typography.headlineSmall) }
            item {
                Text(
                    PathMasker.maskText(e.message, !maskPaths),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        KeyValueRow(stringResource(R.string.error_module), moduleLabel(e.module))
                        KeyValueRow(stringResource(R.string.error_severity), severityLabel(e.severity))
                        KeyValueRow(stringResource(R.string.error_time), Formatters.date(e.timestamp))
                        KeyValueRow(stringResource(R.string.error_device), "${e.deviceModel}, Android ${e.androidVersion}")
                        KeyValueRow(
                            stringResource(R.string.error_app_version),
                            "${e.appVersion} (${e.versionCode}) ${e.buildId}".trim(),
                        )
                        e.jobId?.let { KeyValueRow(stringResource(R.string.error_job), "#$it") }
                    }
                }
            }
            e.stackTrace?.let { trace ->
                item {
                    Text(stringResource(R.string.error_stack), style = MaterialTheme.typography.titleSmall)
                    Text(
                        PathMasker.maskText(trace, !maskPaths),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
            if (occurrencesList.size > 1) {
                item {
                    Text(
                        stringResource(R.string.errors_occurrences, occurrencesList.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(occurrencesList, key = { it.id }) { occurrence ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(
                                Formatters.date(occurrence.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${occurrence.appVersion} (${occurrence.versionCode})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (confirmDelete && error != null) {
            DestructiveConfirmDialog(
                title = stringResource(R.string.action_delete),
                body = stringResource(R.string.error_delete_body),
                confirmText = stringResource(R.string.action_delete),
                onConfirm = {
                    container.appScope.launch { container.database.errorDao().delete(error!!.id) }
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

@Composable
private fun moduleLabel(module: String): String = when (module) {
    "scanner" -> stringResource(R.string.module_scanner)
    "mover" -> stringResource(R.string.module_mover)
    "updater" -> stringResource(R.string.module_updater)
    "settings" -> stringResource(R.string.module_settings)
    else -> module
}
