package com.sortfold.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sortfold.app.AppContainer
import com.sortfold.app.R
import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.data.db.AutoRuleEntity
import com.sortfold.app.ui.common.EmptyState
import com.sortfold.app.ui.common.Formatters
import com.sortfold.app.ui.common.SectionHeader
import com.sortfold.app.ui.home.simpleFactory
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class AutoRulesViewModel(val container: AppContainer) : androidx.lifecycle.ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRulesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: AutoRulesViewModel = viewModel(factory = simpleFactory { AutoRulesViewModel(container) })
    val context = LocalContext.current

    var adding by rememberSaveable { mutableStateOf(false) }
    var newTreeUri by rememberSaveable { mutableStateOf<String?>(null) }
    var newTreeLabel by rememberSaveable { mutableStateOf("") }
    var newModeIndex by rememberSaveable { mutableStateOf(0) }
    var newGranularityIndex by rememberSaveable { mutableStateOf(1) }
    var newPolicyIndex by rememberSaveable { mutableStateOf(0) }
    val newModes = listOf(SortMode.FILE_TYPE, SortMode.DATE_TAKEN, SortMode.EXTENSION, SortMode.SOURCE_APP)

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            newTreeUri = uri.toString()
            newTreeLabel = DocumentFile.fromTreeUri(context, uri)?.name ?: "?"
            adding = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_auto_rules)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val rules by container.database.autoRuleDao().observeAll()
            .collectAsStateWithLifecycle(initialValue = emptyList())

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.auto_rules_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { pickFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.auto_rules_add))
            }

            if (adding && newTreeUri != null) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(newTreeLabel, style = MaterialTheme.typography.titleMedium)
                        SectionHeader(stringResource(R.string.auto_rules_mode))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            newModes.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = newModeIndex == i,
                                    onClick = { newModeIndex = i },
                                    shape = SegmentedButtonDefaults.itemShape(i, newModes.size),
                                ) { Text(modeLabelShort(mode)) }
                            }
                        }
                        if (newModes[newModeIndex] == SortMode.DATE_TAKEN) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf(DateGranularity.YEAR, DateGranularity.MONTH).forEachIndexed { i, g ->
                                    SegmentedButton(
                                        selected = newGranularityIndex == i,
                                        onClick = { newGranularityIndex = i },
                                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    ) {
                                        Text(
                                            stringResource(
                                                if (g == DateGranularity.YEAR) R.string.wizard_by_year else R.string.wizard_by_month,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            DuplicatePolicy.entries.forEachIndexed { i, p ->
                                SegmentedButton(
                                    selected = newPolicyIndex == i,
                                    onClick = { newPolicyIndex = i },
                                    shape = SegmentedButtonDefaults.itemShape(i, DuplicatePolicy.entries.size),
                                ) {
                                    Text(
                                        when (p) {
                                            DuplicatePolicy.SKIP -> stringResource(R.string.short_policy_skip)
                                            DuplicatePolicy.RENAME -> stringResource(R.string.short_policy_rename)
                                            DuplicatePolicy.REPLACE -> stringResource(R.string.short_policy_replace)
                                        },
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                container.appScope.launch {
                                    container.database.autoRuleDao().insert(
                                        AutoRuleEntity(
                                            name = newTreeLabel,
                                            treeUri = newTreeUri!!,
                                            modesCsv = newModes[newModeIndex].name,
                                            dateGranularity = (if (newGranularityIndex == 0) DateGranularity.YEAR else DateGranularity.MONTH).name,
                                            duplicatePolicy = DuplicatePolicy.entries[newPolicyIndex].name,
                                            enabled = true,
                                        ),
                                    )
                                }
                                adding = false
                                newTreeUri = null
                            }) { Text(stringResource(R.string.action_save)) }
                            TextButton(onClick = {
                                adding = false
                                newTreeUri = null
                            }) { Text(stringResource(R.string.action_cancel)) }
                        }
                    }
                }
            }

            if (rules.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.auto_rules_empty_title),
                    description = stringResource(R.string.auto_rules_empty_body),
                )
            } else {
                rules.forEach { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { enabled ->
                            container.appScope.launch {
                                container.database.autoRuleDao().update(rule.copy(enabled = enabled))
                            }
                        },
                        onDelete = {
                            container.appScope.launch { container.database.autoRuleDao().delete(rule.id) }
                        },
                        onRunNow = {
                            com.sortfold.app.work.AutoSortWorker.runNow(container.appContext)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun modeLabelShort(mode: SortMode): String = stringResource(
    when (mode) {
        SortMode.FILE_TYPE -> R.string.short_mode_type
        SortMode.DATE_TAKEN -> R.string.short_mode_date
        SortMode.EXTENSION -> R.string.short_mode_ext
        SortMode.SOURCE_APP -> R.string.short_mode_source
        else -> R.string.mode_file_type
    },
)

@Composable
private fun RuleCard(rule: AutoRuleEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onRunNow: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.auto_rules_summary,
                            rule.modesCsv.split(',').firstOrNull()?.lowercase() ?: "",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    rule.lastRunAt?.let {
                        Text(
                            stringResource(R.string.auto_rules_last_run, DateFormat.getDateTimeInstance().format(Date(it))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
            TextButton(onClick = onRunNow) { Text(stringResource(R.string.auto_rules_run_now)) }
        }
    }
}
