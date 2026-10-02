package com.sortfold.app.ui.errors

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sortfold.app.AppContainer
import com.sortfold.app.R
import com.sortfold.app.data.db.ErrorEntity
import com.sortfold.app.ui.common.DestructiveConfirmDialog
import com.sortfold.app.ui.common.EmptyState
import com.sortfold.app.ui.common.Formatters
import com.sortfold.app.ui.common.SectionHeader
import com.sortfold.app.ui.home.simpleFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ErrorFilters(val module: String? = null, val severity: String? = null)

class ErrorsViewModel(private val container: AppContainer) : ViewModel() {

    val filters = MutableStateFlow(ErrorFilters())

    val errors = combine(container.database.errorDao().observeAll(), filters) { all, f ->
        all.filter { e ->
            (f.module == null || e.module == f.module) && (f.severity == null || e.severity == f.severity)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setModule(module: String?) {
        filters.value = filters.value.copy(module = module)
    }

    fun setSeverity(severity: String?) {
        filters.value = filters.value.copy(severity = severity)
    }

    fun delete(error: ErrorEntity) {
        viewModelScope.launch { container.database.errorDao().delete(error.id) }
    }

    fun clearAll(onDone: () -> Unit) {
        viewModelScope.launch {
            container.database.errorDao().clearAll()
            onDone()
        }
    }

    /** Builds the export zip (report.txt + errors.json + moves.csv) and returns a shareable Uri. */
    fun export(includeFullPaths: Boolean, onReady: (Uri, String) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val errorsNow = container.database.errorDao().since(0)
                    container.errorExporter.export(errorsNow, buildMovesCsv(), includeFullPaths)
                }
                val context = container.appContext
                val uri = FileProvider.getUriForFile(
                    context, context.packageName + ".fileprovider", result.file,
                )
                onReady(uri, result.file.name)
            } catch (_: Exception) {
                onError()
            }
        }
    }

    private suspend fun buildMovesCsv(): String {
        val sb = StringBuilder("job_id,seq,file,dest_folder,status,size_bytes\n")
        container.database.sortJobDao().recent(50).forEach { job ->
            container.database.moveLogDao().byJob(job.id).forEach { m ->
                sb.append("${m.jobId},${m.seq},\"${m.displayName}\",\"${m.destFolder}\",${m.status},${m.sizeBytes}\n")
            }
        }
        return sb.toString()
    }
}

/**
 * Error Library: every caught error and crash, grouped by date, filterable by
 * module and severity, exportable as a zip through the share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLibraryScreen(container: AppContainer, onOpenDetail: (Long) -> Unit) {
    val vm: ErrorsViewModel = viewModel(factory = simpleFactory { ErrorsViewModel(container) })
    val errors by vm.errors.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var showExportNotice by remember { mutableStateOf(false) }
    var includeFullPaths by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.errors_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filters.severity == "CRASH",
                    onClick = { vm.setSeverity(if (filters.severity == "CRASH") null else "CRASH") },
                    label = { Text(stringResource(R.string.severity_crash)) },
                )
                FilterChip(
                    selected = filters.severity == "ERROR",
                    onClick = { vm.setSeverity(if (filters.severity == "ERROR") null else "ERROR") },
                    label = { Text(stringResource(R.string.severity_error)) },
                )
                FilterChip(
                    selected = filters.module == "mover",
                    onClick = { vm.setModule(if (filters.module == "mover") null else "mover") },
                    label = { Text(stringResource(R.string.module_mover)) },
                )
                FilterChip(
                    selected = filters.module == "scanner",
                    onClick = { vm.setModule(if (filters.module == "scanner") null else "scanner") },
                    label = { Text(stringResource(R.string.module_scanner)) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { showExportNotice = true }) {
                    Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.errors_export))
                }
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.errors_clear_all))
                }
            }

            if (errors.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.errors_empty_title),
                    description = stringResource(R.string.errors_empty_body),
                )
            } else {
                val grouped = errors.groupBy { Formatters.day(it.timestamp) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grouped.forEach { (day, list) ->
                        item(key = day) { SectionHeader(day) }
                        items(list, key = { it.id }) { error ->
                            ErrorCard(error, onClick = { onOpenDetail(error.id) })
                        }
                    }
                }
            }
        }

        if (confirmClear) {
            DestructiveConfirmDialog(
                title = stringResource(R.string.errors_clear_all),
                body = stringResource(R.string.errors_clear_all_body),
                confirmText = stringResource(R.string.errors_clear_confirm),
                onConfirm = { vm.clearAll {} },
                onDismiss = { confirmClear = false },
            )
        }

        if (showExportNotice) {
            AlertDialog(
                onDismissRequest = { showExportNotice = false },
                title = { Text(stringResource(R.string.export_notice_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.export_notice_body))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = includeFullPaths, onCheckedChange = { includeFullPaths = it })
                            Text(stringResource(R.string.export_include_paths))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExportNotice = false
                        vm.export(
                            includeFullPaths,
                            onReady = { uri, name -> shareZip(context, uri, name) },
                            onError = {},
                        )
                    }) { Text(stringResource(R.string.export_notice_share)) }
                },
                dismissButton = {
                    TextButton(onClick = { showExportNotice = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}

private fun shareZip(context: Context, uri: Uri, name: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
private fun ErrorCard(error: ErrorEntity, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(error.type, style = MaterialTheme.typography.titleSmall)
                Text(
                    severityLabel(error.severity),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (error.severity == "CRASH" || error.severity == "ERROR") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(error.message, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Formatters.date(error.timestamp), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.errors_expires_in, daysLeft(error.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun daysLeft(timestamp: Long): Int {
    val expires = timestamp + 30L * 86_400_000L
    return (((expires - System.currentTimeMillis()) / 86_400_000L).toInt() + 1).coerceAtLeast(0)
}

@Composable
fun severityLabel(severity: String): String = stringResource(
    when (severity) {
        "CRASH" -> R.string.severity_crash
        "ERROR" -> R.string.severity_error
        "WARNING" -> R.string.severity_warning
        else -> R.string.severity_info
    },
)
