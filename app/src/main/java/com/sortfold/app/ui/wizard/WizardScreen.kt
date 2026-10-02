package com.sortfold.app.ui.wizard

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sortfold.app.AppContainer
import com.sortfold.app.R
import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.MediaType
import com.sortfold.app.core.model.NamePatternType
import com.sortfold.app.core.model.NameRule
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.ui.common.DelayedLoader
import com.sortfold.app.ui.common.EmptyState
import com.sortfold.app.ui.common.ErrorState
import com.sortfold.app.ui.common.Formatters
import com.sortfold.app.ui.common.SectionHeader
import com.sortfold.app.ui.common.SortedBarsLoader
import com.sortfold.app.ui.home.simpleFactory
import com.sortfold.app.ui.permissions.MediaPermissions
import com.sortfold.app.ui.theme.LocalReducedMotion
import com.sortfold.app.ui.theme.Motion
import com.sortfold.app.ui.theme.pressable
import com.sortfold.app.ui.theme.rememberHaptics
import com.sortfold.app.ui.theme.rememberPressInteraction

@Composable
fun jobStatusLabel(status: String): String = stringResource(
    when (status) {
        "PLANNED" -> R.string.job_status_planned
        "RUNNING" -> R.string.job_status_running
        "PAUSED" -> R.string.job_status_paused
        "DONE" -> R.string.job_status_done
        "PARTIAL" -> R.string.job_status_partial
        "FAILED" -> R.string.job_status_failed
        "CANCELLED" -> R.string.job_status_cancelled
        "UNDOING" -> R.string.job_status_undoing
        "UNDONE" -> R.string.job_status_undone
        else -> R.string.job_status_running
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    container: AppContainer,
    expanded: Boolean,
    onExit: () -> Unit,
    onOpenErrorLibrary: () -> Unit = {},
) {
    val vm: WizardViewModel = viewModel(factory = simpleFactory { WizardViewModel(container) })
    val context = LocalContext.current

    val job by vm.job.collectAsStateWithLifecycle()
    val previewRows by vm.previewRows.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = com.sortfold.app.data.prefs.AppSettings(),
    )

    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var showNotifRationale by rememberSaveable { mutableStateOf(false) }
    var pendingApply by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (pendingApply) {
            pendingApply = false
            vm.apply(context)
        }
    }

    fun startApplyFlow() {
        if (MediaPermissions.hasNotifications(context)) {
            vm.apply(context)
        } else {
            pendingApply = true
            showNotifRationale = true
        }
    }

    fun confirmThenRequest() {
        haptics() // light confirmation tick on the primary action (Part B5)
        showConfirm = false
        startApplyFlow()
    }

    LaunchedEffect(job?.status) {
        if (job != null && vm.step == WizardStep.APPLY) {
            when (job?.status) {
                "DONE", "PARTIAL", "FAILED", "CANCELLED" -> vm.advanceToResult()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wizard_title, vm.step.ordinal + 1)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
        bottomBar = {
            if (!expanded && vm.step != WizardStep.APPLY) {
                WizardBottomBar(vm = vm, context = context, onAdvance = {
                    when (vm.step) {
                        WizardStep.FOLDER -> vm.goToModes()
                        WizardStep.MODES -> vm.buildPreview(context)
                        WizardStep.PREVIEW ->
                            if (settings.confirmBeforeApply) showConfirm = true else startApplyFlow()
                        else -> {}
                    }
                })
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        if (expanded) {
            // Two-pane: options left, live preview / progress right.
            Row(contentModifier) {
                Column(
                    Modifier
                        .weight(0.42f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StepContent(vm, context, job, previewRows, onOpenErrorLibrary)
                }
                Column(
                    Modifier
                        .weight(0.58f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) {
                    SidePane(vm, job, previewRows)
                }
            }
        } else {
            Column(
                contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StepContent(vm, context, job, previewRows, onOpenErrorLibrary)
            }
        }

        // Confirmation before Apply: file count, total size, destination.
        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text(stringResource(R.string.confirm_apply_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.confirm_apply_body,
                            vm.planTotals.moveCount,
                            Formatters.bytes(context, vm.planTotals.moveBytes),
                            vm.treeLabel,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { confirmThenRequest() }) {
                        Text(stringResource(R.string.confirm_apply_yes))
                    }
                },
                dismissButton = {
                    // Safe default: cancel keeps the plan untouched.
                    TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }

        // In-app rationale before the first system notification dialog.
        if (showNotifRationale) {
            AlertDialog(
                onDismissRequest = { showNotifRationale = false },
                title = { Text(stringResource(R.string.rationale_notifications_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.rationale_notifications_body))
                        Text(
                            stringResource(R.string.rationale_notifications_will_not),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showNotifRationale = false
                        MediaPermissions.notificationPermission()?.let {
                            notifLauncher.launch(it)
                        } ?: run { vm.apply(context) }
                    }) { Text(stringResource(R.string.rationale_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showNotifRationale = false
                        if (pendingApply) {
                            pendingApply = false
                            vm.apply(context) // degrade: no progress notification, sort still runs
                        }
                    }) { Text(stringResource(R.string.rationale_not_now)) }
                },
            )
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun StepContent(
    vm: WizardViewModel,
    context: Context,
    job: SortJobEntity?,
    previewRows: List<MoveLogEntity>,
    onOpenErrorLibrary: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val layoutDir = LocalLayoutDirection.current
    // Shared-axis X between wizard steps; forward/back follows the direction of travel.
    // The stepper header is a shared element: it glides between steps instead
    // of being replaced (reduced motion keeps a plain cross-fade).
    SharedTransitionLayout {
        AnimatedContent(
            targetState = vm.step,
            transitionSpec = {
                val dir = if (layoutDir == LayoutDirection.Ltr) 1 else -1
                val forward = targetState.ordinal >= initialState.ordinal
                if (reduced) {
                    (fadeIn(tween(1))) togetherWith (fadeOut(tween(1)))
                } else if (forward) {
                    (slideInHorizontally(Motion.enter()) { it / 4 * dir } + fadeIn(Motion.enter())) togetherWith
                        (slideOutHorizontally(Motion.exit()) { -it / 4 * dir } + fadeOut(Motion.exit()))
                } else {
                    (slideInHorizontally(Motion.enter()) { -it / 4 * dir } + fadeIn(Motion.enter())) togetherWith
                        (slideOutHorizontally(Motion.exit()) { it / 4 * dir } + fadeOut(Motion.exit()))
                }
            },
            label = "wizard-step",
        ) { step ->
            Column {
                val shared = rememberSharedContentState(key = "wizard-stepper")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .then(
                            if (reduced) Modifier else Modifier.sharedBounds(
                                sharedContentState = shared,
                                animatedVisibilityScope = this@AnimatedContent,
                            ),
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WizardStepper(current = step, onStepSelected = { target ->
                        // Allow jumping between the three planning steps only.
                        when (target) {
                            WizardStep.FOLDER -> { vm.goBackToFolder() }
                            WizardStep.MODES -> if (vm.step == WizardStep.PREVIEW) vm.goBack() else vm.goToModes()
                            else -> {}
                        }
                    })
                }
                when (step) {
                    WizardStep.FOLDER -> FolderStep(vm, context, onOpenErrorLibrary)
                    WizardStep.MODES -> ModesStep(vm)
                    WizardStep.PREVIEW -> PreviewStep(vm, previewRows, context)
                    WizardStep.APPLY -> ApplyStep(vm, job)
                    WizardStep.RESULT -> ResultStep(vm, job, context)
                }
            }
        }
    }
}

/**
 * Visual 1-2-3 stepper for the three planning steps. Completed steps get a
 * check, the current step is emphasized; Apply/Result are outside the flow.
 */
@Composable
private fun WizardStepper(current: WizardStep, onStepSelected: (WizardStep) -> Unit) {
    val steps = listOf(
        WizardStep.FOLDER to Icons.Filled.Folder,
        WizardStep.MODES to Icons.AutoMirrored.Filled.List,
        WizardStep.PREVIEW to Icons.Filled.Visibility,
    )
    val currentIndex = steps.indexOfFirst { it.first == current }
    steps.forEachIndexed { i, (step, icon) ->
        val done = i < currentIndex
        val active = i == currentIndex
        val color = when {
            active -> MaterialTheme.colorScheme.primary
            done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        }
        val interaction = rememberPressInteraction()
        Row(
            Modifier
                .clip(CircleShape)
                .then(if (active || done) Modifier.clickable(interactionSource = interaction, indication = null, onClick = { onStepSelected(step) }) else Modifier)
                .pressable(interaction)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            } else {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(
                stringResource(
                    when (step) {
                        WizardStep.FOLDER -> R.string.wizard_step_folder
                        WizardStep.MODES -> R.string.wizard_step_modes
                        else -> R.string.wizard_step_preview
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}

@Composable
private fun WizardBottomBar(vm: WizardViewModel, context: Context, onAdvance: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val canGoBack = vm.step == WizardStep.MODES || vm.step == WizardStep.PREVIEW
        if (canGoBack) {
            OutlinedButton(onClick = { vm.goBack() }, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(stringResource(R.string.action_back))
            }
        }
        val nextLabel = when (vm.step) {
            WizardStep.FOLDER -> stringResource(R.string.wizard_next_modes)
            WizardStep.MODES -> stringResource(R.string.wizard_next_preview)
            WizardStep.PREVIEW -> stringResource(R.string.wizard_next_apply)
            else -> null
        }
        if (nextLabel != null) {
            Button(
                onClick = onAdvance,
                enabled = when (vm.step) {
                    WizardStep.FOLDER -> vm.treeUri != null && !vm.scanning && vm.files.isNotEmpty()
                    WizardStep.MODES -> vm.selectedModes.isNotEmpty()
                    WizardStep.PREVIEW -> !vm.hasBlockingWarning
                    else -> false
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) { Text(nextLabel) }
        }
    }
}

// ---------------- Step 1: choose folder ----------------

@Composable
private fun FolderStep(vm: WizardViewModel, context: Context, onOpenErrorLibrary: () -> Unit) {
    var showMediaRationale by rememberSaveable { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(uri, context)
    }

    // "Default destination folder" setting: preselect and scan it once.
    LaunchedEffect(Unit) {
        vm.consumePendingDefaultTree()?.let { tag ->
            runCatching { android.net.Uri.parse(tag) }.getOrNull()?.let { vm.setFolder(it, context) }
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val anyGranted = result.values.any { it }
        vm.onMediaPermissionsResult(!anyGranted)
        pickFolder.launch(null)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.wizard_folder_intro), style = MaterialTheme.typography.bodyLarge)

        Button(
            onClick = {
                if (MediaPermissions.hasAnyMediaRead(context)) {
                    pickFolder.launch(null)
                } else {
                    showMediaRationale = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text(stringResource(R.string.wizard_pick_folder)) }

        if (showMediaRationale) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.rationale_media_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.rationale_media_body))
                    Text(
                        stringResource(R.string.rationale_media_will_not),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showMediaRationale = false
                            mediaPermissionLauncher.launch(MediaPermissions.mediaReadPermissions())
                        }) { Text(stringResource(R.string.rationale_continue)) }
                        TextButton(onClick = {
                            showMediaRationale = false
                            pickFolder.launch(null) // SAF needs no runtime permission
                        }) { Text(stringResource(R.string.rationale_without)) }
                    }
                }
            }
        }

        if (vm.scanning) {
            DelayedLoader(busy = vm.scanning, label = stringResource(R.string.wizard_scanning))
        }
        vm.scanError?.let { failure ->
            ErrorState(
                title = stringResource(R.string.wizard_scan_failed),
                description = when (failure.reason) {
                    com.sortfold.app.core.scanner.ScanFailedException.Reason.PERMISSION_REVOKED ->
                        stringResource(R.string.scan_error_permission, vm.treeLabel)
                    else ->
                        stringResource(R.string.scan_error_generic, vm.treeLabel, failure.detail)
                },
                actions = {
                    TextButton(onClick = { vm.scan(context) }) { Text(stringResource(R.string.action_retry)) }
                    TextButton(onClick = { pickFolder.launch(null) }) {
                        Text(stringResource(R.string.wizard_choose_another))
                    }
                    TextButton(onClick = onOpenErrorLibrary) { Text(stringResource(R.string.errors_title)) }
                },
            )
        }

        if (vm.treeUri != null && !vm.scanning) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vm.treeLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        pluralStringResource(R.plurals.wizard_scan_summary, vm.files.size, vm.files.size, vm.subFolders.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (vm.files.isNotEmpty()) {
                        val images = vm.files.count { it.type == MediaType.IMAGE }
                        val videos = vm.files.count { it.type == MediaType.VIDEO }
                        val others = vm.files.size - images - videos
                        Text(
                            stringResource(R.string.wizard_scan_mix, images, videos, others),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (vm.files.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.wizard_empty_title),
                    description = stringResource(R.string.wizard_empty_body),
                )
            }
            if (vm.mediaReadDenied) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.perm_degraded_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.perm_degraded_body), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { openAppSettings(context) }) {
                            Text(stringResource(R.string.action_open_settings))
                        }
                    }
                }
            }
        }
    }
}

fun openAppSettings(context: Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = android.net.Uri.fromParts("package", context.packageName, null)
    context.startActivity(intent)
}

// ---------------- Step 2: choose sort type ----------------

@Composable
private fun ModesStep(vm: WizardViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (vm.suggestions.isNotEmpty()) {
            SuggestionCard(vm)
        }
        SectionHeader(stringResource(R.string.wizard_modes_title))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SortMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = mode in vm.selectedModes,
                        onCheckedChange = { checked -> vm.toggleMode(mode, checked) },
                    )
                    Column {
                        Text(modeLabel(mode))
                        Text(
                            modeDescription(mode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.wizard_modes_priority_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (SortMode.DATE_TAKEN in vm.selectedModes) {
            SectionHeader(stringResource(R.string.wizard_date_granularity))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = vm.granularity == DateGranularity.YEAR,
                    onClick = { vm.chooseGranularity(DateGranularity.YEAR) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.wizard_by_year)) }
                SegmentedButton(
                    selected = vm.granularity == DateGranularity.MONTH,
                    onClick = { vm.chooseGranularity(DateGranularity.MONTH) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.wizard_by_month)) }
            }
        }

        if (SortMode.NAME_PATTERN in vm.selectedModes) {
            NameRulesEditor(vm)
        }

        SectionHeader(stringResource(R.string.wizard_duplicates))
        Column {
            DuplicatePolicy.entries.forEach { policy ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = vm.policy == policy,
                        onClick = { vm.choosePolicy(policy) },
                    )
                    Text(duplicateLabel(policy))
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(vm: WizardViewModel) {
    val suggestion = vm.suggestions.firstOrNull() ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.wizard_suggestion_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.wizard_suggestion_mode, modeLabel(suggestion.mode)),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(onClick = { vm.setModes(setOf(suggestion.mode)) }) {
                Text(stringResource(R.string.wizard_apply_suggestion))
            }
        }
    }
}

@Composable
private fun NameRulesEditor(vm: WizardViewModel) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf("") }
    var typeIndex by rememberSaveable { mutableStateOf(0) }

    SectionHeader(stringResource(R.string.wizard_name_rules))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            NamePatternType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = typeIndex == i,
                    onClick = { typeIndex = i },
                    shape = SegmentedButtonDefaults.itemShape(i, NamePatternType.entries.size),
                ) { Text(patternTypeLabel(t)) }
            }
        }
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text(stringResource(R.string.wizard_rule_keyword)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = folder,
            onValueChange = { folder = it },
            label = { Text(stringResource(R.string.wizard_rule_folder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        TextButton(
            enabled = keyword.isNotBlank() && folder.isNotBlank(),
            onClick = {
                vm.nameRules.add(
                    NameRule(NamePatternType.entries[typeIndex], keyword.trim(), folder.trim().replace('/', '-')),
                )
                keyword = ""
                folder = ""
                vm.refreshLivePreview()
            },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.wizard_rule_add))
        }
        vm.nameRules.forEachIndexed { i, rule ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.wizard_rule_summary,
                        rule.patternType.name.lowercase(), rule.value, rule.targetFolder,
                    ),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                IconButton(onClick = { vm.nameRules.removeAt(i); vm.refreshLivePreview() }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        }
    }
}

// ---------------- Step 3: preview ----------------

@Composable
private fun PreviewStep(vm: WizardViewModel, rows: List<MoveLogEntity>, context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (vm.planTotals.moveCount == 0 && rows.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.wizard_nothing_to_move),
                description = stringResource(R.string.wizard_nothing_to_move_body),
            )
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.preview_totals_title), style = MaterialTheme.typography.titleMedium)
                KeyValue(stringResource(R.string.preview_move_count), vm.planTotals.moveCount.toString())
                KeyValue(stringResource(R.string.preview_skip_count), vm.planTotals.skipCount.toString())
                KeyValue(stringResource(R.string.preview_total_size), Formatters.bytes(context, vm.planTotals.moveBytes))
                KeyValue(stringResource(R.string.preview_destination), vm.treeLabel)
            }
        }

        vm.warnings.forEach { w ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (w.blocking) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(
                    warningLabel(w.key),
                    Modifier.padding(16.dp),
                    color = if (w.blocking) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        SectionHeader(stringResource(R.string.preview_list_title))
        if (rows.isEmpty()) {
            Text(stringResource(R.string.preview_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rows.take(100).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(8.dp)) {}
                    Column(Modifier.weight(1f)) {
                        Text(row.destName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(
                            stringResource(R.string.preview_move_to, row.destFolder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        Formatters.bytes(context, row.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rows.size > 100) {
                Text(
                    stringResource(R.string.preview_more_items, rows.size - 100),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

// ---------------- Step 4: apply with progress ----------------

@Composable
private fun ApplyStep(vm: WizardViewModel, job: SortJobEntity?) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val total = job?.totalFiles ?: 0
        val done = job?.doneFiles ?: 0
        Text(stringResource(R.string.apply_running), style = MaterialTheme.typography.titleMedium)
        if (total > 0) {
            val progressLabel = stringResource(R.string.a11y_apply_progress, done, total)
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .semantics { contentDescription = progressLabel },
            )
        } else {
            CircularProgressIndicator(Modifier.size(40.dp))
        }
        Text(
            pluralStringResource(R.plurals.job_files_done, total, done, total),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.pause(context) }) { Text(stringResource(R.string.action_pause)) }
            OutlinedButton(onClick = { vm.cancel(context) }) { Text(stringResource(R.string.action_cancel)) }
        }
        Text(
            stringResource(R.string.apply_survives_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- Step 5: result + undo ----------------

@Composable
private fun ResultStep(vm: WizardViewModel, job: SortJobEntity?, context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(
                when (job?.status) {
                    "DONE" -> R.string.result_done_title
                    "PARTIAL" -> R.string.result_partial_title
                    "FAILED" -> R.string.result_failed_title
                    "CANCELLED" -> R.string.result_cancelled_title
                    else -> R.string.result_done_title
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        job?.let {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyValue(stringResource(R.string.preview_move_count), "${it.doneFiles} / ${it.totalFiles}")
                    KeyValue(stringResource(R.string.preview_total_size), Formatters.bytes(context, it.doneBytes))
                    it.message?.let { m -> KeyValue(stringResource(R.string.result_note), m) }
                }
            }
        }
        vm.undoResult?.let { (restored, failed) ->
            Card {
                Text(stringResource(R.string.result_undo_summary, restored, failed), Modifier.padding(16.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { vm.undo(context) },
                enabled = !vm.undoBusy && (job?.doneFiles ?: 0) > 0 && job?.status != "UNDONE",
            ) {
                Text(stringResource(R.string.action_undo))
            }
            OutlinedButton(onClick = { vm.reset(); }) {
                Text(stringResource(R.string.result_done))
            }
        }
        Text(
            stringResource(R.string.result_undo_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- Live preview side pane (expanded layout) ----------------

@Composable
private fun SidePane(vm: WizardViewModel, job: SortJobEntity?, previewRows: List<MoveLogEntity>) {
    val context = LocalContext.current
    when (vm.step) {
        WizardStep.FOLDER, WizardStep.MODES -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.preview_live_title))
            if (vm.livePreview.isEmpty()) {
                Text(
                    stringResource(R.string.preview_live_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(vm.livePreview, key = { it.documentId }) { item ->
                        Card {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    stringResource(R.string.preview_move_to, item.destinationFolder),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        WizardStep.PREVIEW -> PreviewStep(vm, previewRows, context)
        WizardStep.APPLY -> ApplyStep(vm, job)
        WizardStep.RESULT -> ResultStep(vm, job, context)
    }
}

// ---------------- Labels ----------------

@Composable
fun modeLabel(mode: SortMode): String = stringResource(
    when (mode) {
        SortMode.FILE_TYPE -> R.string.mode_file_type
        SortMode.DATE_TAKEN -> R.string.mode_date_taken
        SortMode.SOURCE_APP -> R.string.mode_source_app
        SortMode.RESOLUTION -> R.string.mode_resolution
        SortMode.SIZE -> R.string.mode_size
        SortMode.EXTENSION -> R.string.mode_extension
        SortMode.NAME_PATTERN -> R.string.mode_name_pattern
    },
)

@Composable
private fun modeDescription(mode: SortMode): String = stringResource(
    when (mode) {
        SortMode.FILE_TYPE -> R.string.mode_file_type_desc
        SortMode.DATE_TAKEN -> R.string.mode_date_taken_desc
        SortMode.SOURCE_APP -> R.string.mode_source_app_desc
        SortMode.RESOLUTION -> R.string.mode_resolution_desc
        SortMode.SIZE -> R.string.mode_size_desc
        SortMode.EXTENSION -> R.string.mode_extension_desc
        SortMode.NAME_PATTERN -> R.string.mode_name_pattern_desc
    },
)

@Composable
fun patternTypeLabel(type: NamePatternType): String = stringResource(
    when (type) {
        NamePatternType.PREFIX -> R.string.pattern_prefix
        NamePatternType.SUFFIX -> R.string.pattern_suffix
        NamePatternType.CONTAINS -> R.string.pattern_contains
    },
)

@Composable
fun duplicateLabel(policy: DuplicatePolicy): String = stringResource(
    when (policy) {
        DuplicatePolicy.SKIP -> R.string.dup_skip
        DuplicatePolicy.RENAME -> R.string.dup_rename
        DuplicatePolicy.REPLACE -> R.string.dup_replace
    },
)

@Composable
private fun warningLabel(key: String): String = stringResource(
    when (key) {
        "batch_files" -> R.string.warn_batch_files
        "batch_bytes" -> R.string.warn_batch_bytes
        "low_storage" -> R.string.warn_low_storage
        "insufficient_storage" -> R.string.warn_insufficient
        "replacing" -> R.string.warn_replacing
        "storage_root" -> R.string.warn_storage_root
        "app_private" -> R.string.warn_app_private
        "system_folder" -> R.string.warn_system_folder
        else -> R.string.warn_unknown
    },
)
