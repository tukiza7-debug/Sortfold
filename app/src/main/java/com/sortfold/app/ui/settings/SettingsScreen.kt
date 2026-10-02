package com.sortfold.app.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sortfold.app.AppContainer
import com.sortfold.app.R
import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.data.prefs.AppSettings
import com.sortfold.app.data.prefs.ThemeMode
import com.sortfold.app.ui.common.DestructiveConfirmDialog
import com.sortfold.app.ui.common.SectionHeader
import com.sortfold.app.ui.home.simpleFactory
import com.sortfold.app.ui.wizard.modeLabel
import kotlinx.coroutines.launch

class SettingsViewModel(val container: AppContainer) : androidx.lifecycle.ViewModel() {
    fun set(block: suspend () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { block() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenLanguage: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = simpleFactory { SettingsViewModel(container) })
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val context = LocalContext.current
    var showClearHistory by remember { mutableStateOf(false) }
    var showThresholdEditor by remember { mutableStateOf(false) }
    var updateState by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Appearance ----
            SectionHeader(stringResource(R.string.settings_appearance))
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { vm.set { container.settingsRepository.setThemeMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    },
                                )
                            }
                        }
                    }
                    ToggleRow(
                        label = stringResource(R.string.settings_dynamic_color),
                        description = if (android.os.Build.VERSION.SDK_INT >= 31) null
                        else stringResource(R.string.settings_dynamic_color_unavailable),
                        checked = settings.dynamicColor && android.os.Build.VERSION.SDK_INT >= 31,
                        enabled = android.os.Build.VERSION.SDK_INT >= 31,
                        onChecked = { vm.set { container.settingsRepository.setDynamicColor(it) } },
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_reduce_animations),
                        description = stringResource(R.string.settings_reduce_animations_desc),
                        checked = settings.reduceAnimations,
                        onChecked = { vm.set { container.settingsRepository.setReduceAnimations(it) } },
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenLanguage)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.settings_language))
                        Text(languageLabel(settings.languageTag), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ---- Defaults ----
            SectionHeader(stringResource(R.string.settings_defaults))
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_default_mode), style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf(SortMode.FILE_TYPE, SortMode.DATE_TAKEN, SortMode.EXTENSION, SortMode.SOURCE_APP)
                        modes.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = settings.defaultSortMode == mode,
                                onClick = { vm.set { container.settingsRepository.setDefaultSortMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(shortModeLabel(mode)) }
                        }
                    }
                    Text(stringResource(R.string.settings_default_policy), style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        DuplicatePolicy.entries.forEachIndexed { i, p ->
                            SegmentedButton(
                                selected = settings.defaultDuplicatePolicy == p,
                                onClick = { vm.set { container.settingsRepository.setDefaultDuplicatePolicy(p) } },
                                shape = SegmentedButtonDefaults.itemShape(i, DuplicatePolicy.entries.size),
                            ) { Text(shortPolicyLabel(p)) }
                        }
                    }
                    Text(stringResource(R.string.settings_default_granularity), style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        DateGranularity.entries.forEachIndexed { i, g ->
                            SegmentedButton(
                                selected = settings.defaultDateGranularity == g,
                                onClick = { vm.set { container.settingsRepository.setDefaultDateGranularity(g) } },
                                shape = SegmentedButtonDefaults.itemShape(i, DateGranularity.entries.size),
                            ) {
                                Text(
                                    stringResource(
                                        if (g == DateGranularity.YEAR) R.string.wizard_by_year else R.string.wizard_by_month,
                                    ),
                                )
                            }
                        }
                    }
                    ToggleRow(
                        label = stringResource(R.string.settings_confirm_apply),
                        checked = settings.confirmBeforeApply,
                        onChecked = { vm.set { container.settingsRepository.setConfirmBeforeApply(it) } },
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showThresholdEditor = true }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.settings_batch_threshold))
                        Text(
                            stringResource(R.string.settings_batch_threshold_value, settings.batchFileThreshold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- History ----
            SectionHeader(stringResource(R.string.settings_history))
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_undo_retention), style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(1, 7, 30).forEachIndexed { i, days ->
                            SegmentedButton(
                                selected = settings.undoRetentionDays == days,
                                onClick = { vm.set { container.settingsRepository.setUndoRetentionDays(days) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                            ) {
                                Text(pluralStringResourceSafe(days))
                            }
                        }
                    }
                    TextButton(onClick = { showClearHistory = true }) {
                        Text(stringResource(R.string.settings_clear_history))
                    }
                }
            }

            // ---- Notifications & auto-sort ----
            SectionHeader(stringResource(R.string.settings_background))
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow(
                        label = stringResource(R.string.settings_notifications),
                        description = stringResource(R.string.settings_notifications_desc),
                        checked = settings.notificationsEnabled,
                        onChecked = { vm.set { container.settingsRepository.setNotificationsEnabled(it) } },
                    )
                    TextButton(onClick = { openSystemNotificationSettings(context) }) {
                        Text(stringResource(R.string.settings_open_notif_settings))
                    }
                    ToggleRow(
                        label = stringResource(R.string.settings_auto_sort),
                        description = stringResource(R.string.settings_auto_sort_desc),
                        checked = settings.autoSortEnabled,
                        onChecked = { checked ->
                            vm.set {
                                container.settingsRepository.setAutoSortEnabled(checked)
                                if (checked) {
                                    com.sortfold.app.work.AutoSortWorker.schedule(container.appContext)
                                } else {
                                    com.sortfold.app.work.AutoSortWorker.cancel(container.appContext)
                                }
                            }
                        },
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenRules)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.settings_auto_rules))
                    }
                }
            }

            // ---- Update ----
            SectionHeader(stringResource(R.string.settings_update))
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow(
                        label = stringResource(R.string.settings_auto_update),
                        checked = settings.autoCheckUpdates,
                        onChecked = { vm.set { container.settingsRepository.setAutoCheckUpdates(it) } },
                    )
                    Text(
                        stringResource(R.string.settings_current_version, com.sortfold.app.BuildConfig.VERSION_NAME),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {
                        vm.set {
                            val result = container.updateRepository.check(force = true, lastCheckAt = 0)
                            container.settingsRepository.setLastUpdateCheckAt(System.currentTimeMillis())
                            updateState = when {
                                result.latest == null -> "error"
                                result.updateAvailable -> result.latest.version
                                else -> "latest"
                            }
                        }
                    }) { Text(stringResource(R.string.settings_check_now)) }
                    updateState?.let { state ->
                        when (state) {
                            "error" -> Text(stringResource(R.string.update_check_failed), color = MaterialTheme.colorScheme.error)
                            "latest" -> Text(stringResource(R.string.update_up_to_date))
                            else -> Text(stringResource(R.string.update_available_short, state))
                        }
                    }
                }
            }

            // ---- Storage ----
            SectionHeader(stringResource(R.string.settings_storage))
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val size by androidx.compose.runtime.produceState(0L) {
                        value = computeAppDataSize(context)
                    }
                    Text(
                        stringResource(R.string.settings_data_size, com.sortfold.app.ui.common.Formatters.bytes(context, size)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    }) { Text(stringResource(R.string.settings_clear_cache)) }
                }
            }

            // ---- About ----
            SectionHeader(stringResource(R.string.settings_about))
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenAbout)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.settings_about_app))
                        Text(
                            com.sortfold.app.BuildConfig.VERSION_NAME,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (showClearHistory) {
            DestructiveConfirmDialog(
                title = stringResource(R.string.settings_clear_history),
                body = stringResource(R.string.settings_clear_history_body),
                confirmText = stringResource(R.string.settings_clear_history),
                onConfirm = {
                    vm.set {
                        container.database.sortJobDao().deleteOlderThan(0)
                        container.database.moveLogDao().clearAll()
                    }
                },
                onDismiss = { showClearHistory = false },
            )
        }
        if (showThresholdEditor) {
            ThresholdEditorDialog(
                initialFiles = settings.batchFileThreshold,
                onDismiss = { showThresholdEditor = false },
                onSave = { files ->
                    showThresholdEditor = false
                    vm.set { container.settingsRepository.setBatchThresholds(files, settings.batchBytesThreshold) }
                },
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun ThresholdEditorDialog(initialFiles: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialFiles.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_batch_threshold)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.settings_batch_files_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toIntOrNull()?.let { onSave(it.coerceAtLeast(1)) } }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun pluralStringResourceSafe(days: Int): String = when (days) {
    1 -> stringResource(R.string.days_one)
    7 -> stringResource(R.string.days_seven)
    else -> stringResource(R.string.days_thirty)
}

@Composable
private fun shortModeLabel(mode: SortMode): String = when (mode) {
    SortMode.FILE_TYPE -> stringResource(R.string.short_mode_type)
    SortMode.DATE_TAKEN -> stringResource(R.string.short_mode_date)
    SortMode.EXTENSION -> stringResource(R.string.short_mode_ext)
    SortMode.SOURCE_APP -> stringResource(R.string.short_mode_source)
    else -> modeLabel(mode)
}

@Composable
private fun shortPolicyLabel(policy: DuplicatePolicy): String = when (policy) {
    DuplicatePolicy.SKIP -> stringResource(R.string.short_policy_skip)
    DuplicatePolicy.RENAME -> stringResource(R.string.short_policy_rename)
    DuplicatePolicy.REPLACE -> stringResource(R.string.short_policy_replace)
}

@Composable
fun languageLabel(tag: String?): String = stringResource(
    when (tag) {
        null -> R.string.lang_system
        "en" -> R.string.lang_en
        "ms" -> R.string.lang_ms
        "in" -> R.string.lang_in
        "ar" -> R.string.lang_ar
        "zh-CN" -> R.string.lang_zh
        else -> R.string.lang_system
    },
)

private fun openSystemNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}

private fun computeAppDataSize(context: Context): Long {
    var total = 0L
    context.cacheDir.walkTopDown().forEach { if (it.isFile) total += it.length() }
    context.filesDir.walkTopDown().forEach { if (it.isFile) total += it.length() }
    return total
}
