package com.sortfold.app.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.ViewModel
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
import com.sortfold.app.data.repo.GithubApiClient
import com.sortfold.app.data.repo.UpdateRepository
import com.sortfold.app.error.ErrorRepository
import com.sortfold.app.ui.common.DestructiveConfirmDialog
import com.sortfold.app.ui.common.Formatters
import com.sortfold.app.ui.common.SectionHeader
import com.sortfold.app.ui.home.simpleFactory
import com.sortfold.app.ui.theme.Spacing
import com.sortfold.app.ui.wizard.modeLabel
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** UI state of the manual update check; every failure has its own reason. */
sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data class UpToDate(val checkedAt: Long) : UpdateUiState()
    data class Available(val release: GithubApiClient.LatestRelease) : UpdateUiState()
    data class Failed(
        val reason: UpdateCheckFailure,
        val checkedAt: Long,
        val rateResetSeconds: Long? = null,
    ) : UpdateUiState()
}

enum class UpdateCheckFailure { OFFLINE, TIMEOUT, NO_RELEASE, RATE_LIMITED, HTTP, MALFORMED, NO_APK, UNEXPECTED }

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    fun set(block: suspend () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { block() }
    }

    val updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)

    fun checkNow() {
        if (updateState.value is UpdateUiState.Checking) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            updateState.value = UpdateUiState.Checking
            val result = container.updateRepository.check(force = true, lastCheckAt = 0)
            val now = System.currentTimeMillis()
            when (result) {
                is UpdateRepository.CheckResult.Skipped -> updateState.value = UpdateUiState.Idle
                is UpdateRepository.CheckResult.UpToDate -> {
                    container.settingsRepository.setLastUpdateCheckAt(now)
                    updateState.value = UpdateUiState.UpToDate(now)
                }
                is UpdateRepository.CheckResult.UpdateAvailable -> {
                    container.settingsRepository.setLastUpdateCheckAt(now)
                    updateState.value = UpdateUiState.Available(result.release)
                }
                is UpdateRepository.CheckResult.Failure -> {
                    val reason = when (result.error) {
                        is GithubApiClient.UpdateException.Offline -> UpdateCheckFailure.OFFLINE
                        is GithubApiClient.UpdateException.Timeout -> UpdateCheckFailure.TIMEOUT
                        is GithubApiClient.UpdateException.NoRelease -> UpdateCheckFailure.NO_RELEASE
                        is GithubApiClient.UpdateException.RateLimited -> UpdateCheckFailure.RATE_LIMITED
                        is GithubApiClient.UpdateException.Malformed -> UpdateCheckFailure.MALFORMED
                        is GithubApiClient.UpdateException.NoApkAsset -> UpdateCheckFailure.NO_APK
                        is GithubApiClient.UpdateException.Http -> UpdateCheckFailure.HTTP
                        is GithubApiClient.UpdateException.Unknown -> UpdateCheckFailure.UNEXPECTED
                    }
                    container.errorRepository.log(
                        module = "updater",
                        severity = ErrorRepository.Severity.WARNING,
                        type = result.error.javaClass.simpleName,
                        message = "Update check failed: ${result.error.message}",
                        stackTrace = result.error.stackTraceToString(),
                    )
                    updateState.value = UpdateUiState.Failed(
                        reason = reason,
                        checkedAt = now,
                        rateResetSeconds =
                            (result.error as? GithubApiClient.UpdateException.RateLimited)?.resetEpochSeconds,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenLanguage: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenErrorLibrary: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val vm: SettingsViewModel = viewModel(factory = simpleFactory { SettingsViewModel(container) })
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showClearHistory by remember { mutableStateOf(false) }
    var showThresholdEditor by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var showGranularityDialog by remember { mutableStateOf(false) }
    var showNamingDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }

    var errorCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        container.database.errorDao().observeAll().collect { errorCount = it.size }
    }
    var dataRefresh by remember { mutableStateOf(0) }
    var dataBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(dataRefresh) {
        dataBytes = computeAppDataSize(context)
    }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.set { container.settingsRepository.setDefaultDestTreeUri(uri.toString()) }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // ---- Language ----
            item { SectionHeader(stringResource(R.string.settings_language)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_language)) },
                        supportingContent = { Text(languageLabel(settings.languageTag)) },
                        leadingContent = { Icon(Icons.Filled.Translate, contentDescription = null) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable(onClick = onOpenLanguage),
                    )
                }
            }

            // ---- Appearance ----
            item { SectionHeader(stringResource(R.string.settings_appearance)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_theme)) },
                        supportingContent = {
                            Text(
                                when (settings.themeMode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null) },
                        modifier = Modifier.clickable { showThemeDialog = true },
                    )
                    ToggleListItem(
                        icon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                        title = stringResource(R.string.settings_dynamic_color),
                        description = if (android.os.Build.VERSION.SDK_INT >= 31) {
                            null
                        } else {
                            stringResource(R.string.settings_dynamic_color_unavailable)
                        },
                        checked = settings.dynamicColor && android.os.Build.VERSION.SDK_INT >= 31,
                        enabled = android.os.Build.VERSION.SDK_INT >= 31,
                        onChecked = { vm.set { container.settingsRepository.setDynamicColor(it) } },
                    )
                    ToggleListItem(
                        icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        title = stringResource(R.string.settings_reduce_animations),
                        description = stringResource(R.string.settings_reduce_animations_desc),
                        checked = settings.reduceAnimations,
                        onChecked = { vm.set { container.settingsRepository.setReduceAnimations(it) } },
                    )
                }
            }

            // ---- Sorting defaults ----
            item { SectionHeader(stringResource(R.string.settings_defaults)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_default_mode)) },
                        supportingContent = { Text(shortModeLabel(settings.defaultSortMode)) },
                        leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        modifier = Modifier.clickable { showModeDialog = true },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_default_policy)) },
                        supportingContent = { Text(shortPolicyLabel(settings.defaultDuplicatePolicy)) },
                        leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        modifier = Modifier.clickable { showPolicyDialog = true },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_default_granularity)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (settings.defaultDateGranularity == DateGranularity.YEAR) {
                                        R.string.wizard_by_year
                                    } else {
                                        R.string.wizard_by_month
                                    },
                                ),
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        modifier = Modifier.clickable { showGranularityDialog = true },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_destination)) },
                        supportingContent = {
                            Text(
                                settings.defaultDestTreeUri?.let {
                                    android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(it))
                                        .substringAfter(':')
                                } ?: stringResource(R.string.settings_destination_none),
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        modifier = Modifier.clickable { folderPicker.launch(null) },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_naming_style)) },
                        supportingContent = {
                            Text(
                                if (settings.namingStyleIso) "2024-05" else stringResource(R.string.settings_naming_readable),
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Description, contentDescription = null) },
                        modifier = Modifier.clickable { showNamingDialog = true },
                    )
                    ToggleListItem(
                        icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        title = stringResource(R.string.settings_confirm_apply),
                        checked = settings.confirmBeforeApply,
                        onChecked = { vm.set { container.settingsRepository.setConfirmBeforeApply(it) } },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_batch_threshold)) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_batch_threshold_value, settings.batchFileThreshold))
                        },
                        leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        modifier = Modifier.clickable { showThresholdEditor = true },
                    )
                    ToggleListItem(
                        icon = { Icon(Icons.Filled.Update, contentDescription = null) },
                        title = stringResource(R.string.settings_auto_sort),
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
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_auto_rules)) },
                        supportingContent = { Text(stringResource(R.string.settings_auto_rules_desc)) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable(onClick = onOpenRules),
                    )
                }
            }

            // ---- History ----
            item { SectionHeader(stringResource(R.string.settings_history)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_undo_retention)) },
                        supportingContent = { Text(retentionLabel(settings.undoRetentionDays)) },
                        leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                        modifier = Modifier.clickable { showRetentionDialog = true },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_clear_history)) },
                        supportingContent = { Text(stringResource(R.string.settings_clear_history_body)) },
                        leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        colors = ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.clickable { showClearHistory = true },
                    )
                }
            }

            // ---- Notifications ----
            item { SectionHeader(stringResource(R.string.settings_background)) }
            item {
                SettingsGroup {
                    ToggleListItem(
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                        title = stringResource(R.string.settings_notifications),
                        description = stringResource(R.string.settings_notifications_desc),
                        checked = settings.notificationsEnabled,
                        onChecked = { vm.set { container.settingsRepository.setNotificationsEnabled(it) } },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_open_notif_settings)) },
                        leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                        modifier = Modifier.clickable { openSystemNotificationSettings(context) },
                    )
                }
            }

            // ---- Updates ----
            item { SectionHeader(stringResource(R.string.settings_update)) }
            item {
                SettingsGroup {
                    ToggleListItem(
                        icon = { Icon(Icons.Filled.Update, contentDescription = null) },
                        title = stringResource(R.string.settings_auto_update),
                        checked = settings.autoCheckUpdates,
                        onChecked = { vm.set { container.settingsRepository.setAutoCheckUpdates(it) } },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_check_now)) },
                        supportingContent = {
                            when (val s = updateState) {
                                is UpdateUiState.Checking -> Text(stringResource(R.string.update_checking))
                                is UpdateUiState.UpToDate -> Text(
                                    stringResource(R.string.update_up_to_date, Formatters.date(s.checkedAt)),
                                )
                                is UpdateUiState.Available -> Text(
                                    stringResource(R.string.update_available_short, s.release.version),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                is UpdateUiState.Failed -> Text(
                                    updateFailureText(s.reason, s.rateResetSeconds),
                                    color = MaterialTheme.colorScheme.error,
                                )
                                UpdateUiState.Idle -> {
                                    if (settings.lastUpdateCheckAt > 0) {
                                        Text(stringResource(R.string.settings_last_checked, Formatters.date(settings.lastUpdateCheckAt)))
                                    } else {
                                        Text(stringResource(R.string.settings_last_checked_never))
                                    }
                                }
                            }
                        },
                        leadingContent = { Icon(Icons.Filled.Update, contentDescription = null) },
                        trailingContent = {
                            when (updateState) {
                                UpdateUiState.Checking -> CircularProgressIndicator(Modifier.height(24.dp))
                                else -> {}
                            }
                        },
                        modifier = Modifier.clickable(enabled = updateState !is UpdateUiState.Checking) {
                            vm.checkNow()
                        },
                    )
                    if (updateState is UpdateUiState.Available) {
                        val s = updateState as UpdateUiState.Available
                        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                            Text(
                                stringResource(R.string.update_available_short, s.release.version),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (s.release.notes.isNotBlank()) {
                                Text(
                                    s.release.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = {
                                    val url = container.updateRepository.pickApkForDevice(s.release.apkUrls)
                                    if (url != null) {
                                        container.updateRepository.enqueueApkDownload(context, url, s.release.version)
                                    }
                                },
                                modifier = Modifier.padding(top = Spacing.sm),
                            ) {
                                Text(stringResource(R.string.update_download))
                            }
                        }
                    }
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.settings_current_version, UpdateRepository.currentVersion))
                        },
                        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    )
                }
            }

            // ---- Storage ----
            item { SectionHeader(stringResource(R.string.settings_storage)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_data_size)) },
                        supportingContent = { Text(Formatters.bytes(context, dataBytes)) },
                        leadingContent = { Icon(Icons.Filled.Storage, contentDescription = null) },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
                        leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        modifier = Modifier.clickable {
                            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            dataRefresh++
                        },
                    )
                }
            }

            // ---- Diagnostics ----
            item { SectionHeader(stringResource(R.string.settings_diagnostics)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.errors_title)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_error_library_support, errorCount),
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable(onClick = onOpenErrorLibrary),
                    )
                    ToggleListItem(
                        icon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                        title = stringResource(R.string.settings_include_full_paths),
                        description = stringResource(R.string.settings_include_full_paths_desc),
                        checked = settings.includeFullPathsInExport,
                        onChecked = { vm.set { container.settingsRepository.setIncludeFullPaths(it) } },
                    )
                }
            }

            // ---- About ----
            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_about_app)) },
                        supportingContent = { Text(com.sortfold.app.BuildConfig.VERSION_NAME) },
                        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable(onClick = onOpenAbout),
                    )
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
        if (showThemeDialog) {
            val themeLabels = listOf(
                stringResource(R.string.theme_system),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark),
            )
            SingleChoiceDialog(
                title = stringResource(R.string.settings_theme),
                options = themeLabels,
                selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                onSelect = { i ->
                    showThemeDialog = false
                    vm.set { container.settingsRepository.setThemeMode(ThemeMode.entries[i]) }
                },
                onDismiss = { showThemeDialog = false },
            )
        }
        if (showModeDialog) {
            val selectableModes = listOf(SortMode.FILE_TYPE, SortMode.DATE_TAKEN, SortMode.EXTENSION, SortMode.SOURCE_APP)
            val modeLabels = selectableModes.map { shortModeLabel(it) }
            SingleChoiceDialog(
                title = stringResource(R.string.settings_default_mode),
                options = modeLabels,
                selectedIndex = selectableModes.indexOf(settings.defaultSortMode).let { if (it < 0) 0 else it },
                onSelect = { i ->
                    showModeDialog = false
                    vm.set { container.settingsRepository.setDefaultSortMode(selectableModes[i]) }
                },
                onDismiss = { showModeDialog = false },
            )
        }
        if (showPolicyDialog) {
            val policyLabels = DuplicatePolicy.entries.map { shortPolicyLabel(it) }
            SingleChoiceDialog(
                title = stringResource(R.string.settings_default_policy),
                options = policyLabels,
                selectedIndex = DuplicatePolicy.entries.indexOf(settings.defaultDuplicatePolicy),
                onSelect = { i ->
                    showPolicyDialog = false
                    vm.set { container.settingsRepository.setDefaultDuplicatePolicy(DuplicatePolicy.entries[i]) }
                },
                onDismiss = { showPolicyDialog = false },
            )
        }
        if (showGranularityDialog) {
            val granularityLabels = DateGranularity.entries.map {
                stringResource(
                    if (it == DateGranularity.YEAR) R.string.wizard_by_year else R.string.wizard_by_month,
                )
            }
            SingleChoiceDialog(
                title = stringResource(R.string.settings_default_granularity),
                options = granularityLabels,
                selectedIndex = DateGranularity.entries.indexOf(settings.defaultDateGranularity),
                onSelect = { i ->
                    showGranularityDialog = false
                    vm.set { container.settingsRepository.setDefaultDateGranularity(DateGranularity.entries[i]) }
                },
                onDismiss = { showGranularityDialog = false },
            )
        }
        if (showNamingDialog) {
            val namingLabels = listOf("2024-05", stringResource(R.string.settings_naming_readable))
            SingleChoiceDialog(
                title = stringResource(R.string.settings_naming_style),
                options = namingLabels,
                selectedIndex = if (settings.namingStyleIso) 0 else 1,
                onSelect = { i ->
                    showNamingDialog = false
                    vm.set { container.settingsRepository.setNamingStyleIso(i == 0) }
                },
                onDismiss = { showNamingDialog = false },
            )
        }
        if (showRetentionDialog) {
            val retentionOptions = listOf(1, 7, 30)
            val retentionLabels = retentionOptions.map { retentionLabel(it) }
            SingleChoiceDialog(
                title = stringResource(R.string.settings_undo_retention),
                options = retentionLabels,
                selectedIndex = retentionOptions.indexOf(settings.undoRetentionDays).let { if (it < 0) 1 else it },
                onSelect = { i ->
                    showRetentionDialog = false
                    vm.set { container.settingsRepository.setUndoRetentionDays(retentionOptions[i]) }
                },
                onDismiss = { showRetentionDialog = false },
            )
        }
    }
}

/** A consistently styled group of settings rows: same width, same padding everywhere. */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    androidx.compose.material3.Card(
        Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column { content() }
    }
}

@Composable
private fun ToggleListItem(
    icon: @Composable () -> Unit,
    title: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = icon,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
        },
        modifier = Modifier.clickable(enabled = enabled) { onChecked(!checked) },
    )
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                options.forEachIndexed { i, label ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { RadioButton(selected = i == selectedIndex, onClick = null) },
                        modifier = Modifier.clickable { onSelect(i) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun updateFailureText(reason: UpdateCheckFailure, resetSeconds: Long?): String = when (reason) {
    UpdateCheckFailure.OFFLINE -> stringResource(R.string.update_err_offline)
    UpdateCheckFailure.TIMEOUT -> stringResource(R.string.update_err_timeout)
    UpdateCheckFailure.NO_RELEASE -> stringResource(R.string.update_err_no_release)
    UpdateCheckFailure.RATE_LIMITED ->
        if (resetSeconds != null && resetSeconds > 0) {
            stringResource(
                R.string.update_err_rate_limit_until,
                Formatters.date(resetSeconds * 1000),
            )
        } else {
            stringResource(R.string.update_err_rate_limit)
        }
    UpdateCheckFailure.HTTP -> stringResource(R.string.update_err_http)
    UpdateCheckFailure.MALFORMED -> stringResource(R.string.update_err_malformed)
    UpdateCheckFailure.NO_APK -> stringResource(R.string.update_err_no_apk)
    UpdateCheckFailure.UNEXPECTED -> stringResource(R.string.update_err_unexpected)
}

@Composable
private fun retentionLabel(days: Int): String = when (days) {
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

@Composable
private fun ThresholdEditorDialog(initialFiles: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialFiles.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_batch_threshold)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
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
