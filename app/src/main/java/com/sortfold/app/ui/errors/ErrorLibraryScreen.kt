package com.sortfold.app.ui.errors

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sortfold.app.AppContainer
import com.sortfold.app.R
import com.sortfold.app.data.db.ErrorEntity
import com.sortfold.app.error.PathMasker
import com.sortfold.app.ui.common.DestructiveConfirmDialog
import com.sortfold.app.ui.common.EmptyState
import com.sortfold.app.ui.common.Formatters
import com.sortfold.app.ui.common.SectionHeader
import com.sortfold.app.ui.home.simpleFactory
import com.sortfold.app.ui.theme.LocalReducedMotion
import com.sortfold.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ErrorFilters(val module: String? = null, val severity: String? = null)

/** One grouped card: identical errors share module + type + message. */
data class ErrorGroup(
    val module: String,
    val severity: String,
    val type: String,
    val message: String,
    val count: Int,
    val lastTimestamp: Long,
    val latestId: Long,
)

class ErrorsViewModel(private val container: AppContainer) : ViewModel() {

    val filters = MutableStateFlow(ErrorFilters())

    val includeFullPaths = container.settingsRepository.settings

    val errors = combine(
        container.database.errorDao().observeAll(),
        filters,
        includeFullPaths,
    ) { all, f, settings ->
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

    fun delete(id: Long) {
        viewModelScope.launch { container.database.errorDao().delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { container.database.errorDao().clearAll() }
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

    companion object {
        /** Identical = same module + type + message; grouped into one card. */
        fun group(errors: List<ErrorEntity>): List<ErrorGroup> =
            errors
                .groupBy { Triple(it.module, it.type, it.message) }
                .map { (_, list) ->
                    val newest = list.maxBy { it.timestamp }
                    ErrorGroup(
                        module = newest.module,
                        severity = newest.severity,
                        type = newest.type,
                        message = newest.message,
                        count = list.size,
                        lastTimestamp = newest.timestamp,
                        latestId = newest.id,
                    )
                }
                .sortedByDescending { it.lastTimestamp }
    }
}

private data class ChipSpec(val key: String?, val label: String)

/** Horizontally scrollable chip row with a fixed height and reserved check space. */
@Composable
private fun FilterChipRow(
    label: String,
    chips: List<ChipSpec>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(end = Spacing.sm),
        ) {
            items(chips, key = { it.key ?: "__all" }) { chip ->
                val selected = chip.key == selectedKey
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(if (selected && chip.key != null) null else chip.key) },
                    modifier = Modifier.height(FilterChipDefaults.Height),
                    leadingIcon = {
                        // Reserved space keeps the chip identical size when toggled.
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        } else {
                            Spacer(Modifier.size(FilterChipDefaults.IconSize))
                        }
                    },
                    label = {
                        Text(
                            chip.label,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }
        }
    }
}

/**
 * Error Library: a Settings sub-screen listing every caught error and crash.
 * Identical entries collapse into one card with a count; filters and export
 * live in the top app bar; all displayed text is path-masked by default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLibraryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    val vm: ErrorsViewModel = viewModel(factory = simpleFactory { ErrorsViewModel(container) })
    val errors by vm.errors.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val settings by vm.includeFullPaths.collectAsStateWithLifecycle(initialValue = null)
    val includeFullPaths = settings?.includeFullPathsInExport ?: false
    var confirmClear by remember { mutableStateOf(false) }
    var showExportNotice by remember { mutableStateOf(false) }
    var exportFullPaths by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val severityChips = listOf(
        ChipSpec(null, stringResource(R.string.chip_all)),
        ChipSpec("ERROR", stringResource(R.string.severity_error)),
        ChipSpec("WARNING", stringResource(R.string.severity_warning)),
        ChipSpec("INFO", stringResource(R.string.severity_info)),
    )
    val moduleChips = listOf(
        ChipSpec(null, stringResource(R.string.chip_all)),
        ChipSpec("scanner", stringResource(R.string.module_scanner)),
        ChipSpec("mover", stringResource(R.string.module_mover)),
        ChipSpec("updater", stringResource(R.string.module_updater)),
        ChipSpec("settings", stringResource(R.string.module_settings)),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.errors_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showExportNotice = true }) {
                        Icon(
                            Icons.Filled.IosShare,
                            contentDescription = stringResource(R.string.errors_export),
                        )
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.errors_clear_all),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilterChipRow(
                    label = stringResource(R.string.filter_severity),
                    chips = severityChips,
                    selectedKey = filters.severity,
                    onSelect = vm::setSeverity,
                )
                FilterChipRow(
                    label = stringResource(R.string.filter_module),
                    chips = moduleChips,
                    selectedKey = filters.module,
                    onSelect = vm::setModule,
                )
            }

            if (errors.isEmpty()) {
                Column(Modifier.padding(padding)) {
                    EmptyState(
                        title = stringResource(R.string.errors_empty_title),
                        description = stringResource(R.string.errors_empty_body),
                    )
                }
            } else {
                val grouped = remember(errors) { ErrorsViewModel.group(errors) }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.sm,
                        bottom = Spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    val byDay = grouped.groupBy { Formatters.day(it.lastTimestamp) }
                    byDay.forEach { (day, list) ->
                        item(key = "day-$day") { SectionHeader(day) }
                        items(list, key = { "${it.module}-${it.type}-${it.lastTimestamp}" }) { group ->
                            ErrorGroupCard(
                                group = group,
                                count = group.count,
                                maskedMessage = PathMasker.maskText(group.message, includeFullPaths),
                                onClick = { onOpenDetail(group.latestId) },
                                modifier = Modifier.animateItem(),
                            )
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
                onConfirm = { vm.clearAll() },
                onDismiss = { confirmClear = false },
            )
        }

        if (showExportNotice) {
            AlertDialog(
                onDismissRequest = { showExportNotice = false },
                title = { Text(stringResource(R.string.export_notice_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(stringResource(R.string.export_notice_body))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = exportFullPaths, onCheckedChange = { exportFullPaths = it })
                            Text(stringResource(R.string.export_include_paths))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExportNotice = false
                        vm.export(
                            exportFullPaths,
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
private fun ErrorGroupCard(
    group: ErrorGroup,
    count: Int,
    maskedMessage: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(group.type, style = MaterialTheme.typography.titleSmall)
                    if (count > 1) {
                        Text(
                            stringResource(R.string.errors_group_count, count),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    severityLabel(group.severity),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (group.severity == "CRASH" || group.severity == "ERROR") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                maskedMessage,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Formatters.date(group.lastTimestamp), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.errors_expires_in, daysLeft(group.lastTimestamp)),
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
