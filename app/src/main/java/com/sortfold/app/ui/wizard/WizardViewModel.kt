package com.sortfold.app.ui.wizard

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortfold.app.AppContainer
import com.sortfold.app.core.model.CapacityOrder
import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.MediaFile
import com.sortfold.app.core.model.NameRule
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.model.PlanItem
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.core.mover.StorageSafety
import com.sortfold.app.core.rules.CapacityPacker
import com.sortfold.app.core.rules.ModeSuggester
import com.sortfold.app.core.rules.RuleEngine
import com.sortfold.app.core.rules.SortConfig
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.ui.common.CapacityEstimate
import com.sortfold.app.work.SortWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WizardStep { FOLDER, MODES, PREVIEW, APPLY, RESULT }

/**
 * One wizard warning; [count] carries the capacity_oversized file count for
 * the translated message (1.2.0).
 */
data class WizardWarning(val key: String, val blocking: Boolean, val count: Int = 0)

/**
 * Holds the whole wizard across rotation and process death: the job and its
 * planned moves live in Room, in-memory settings survive via the ViewModel.
 */
class WizardViewModel(
    private val container: AppContainer,
    /** Test seam: start deeper in the flow than the folder picker. */
    val startStep: WizardStep = WizardStep.FOLDER,
) : ViewModel() {

    var step by mutableStateOf(startStep)
        private set
    var treeUri by mutableStateOf<String?>(null)
        private set
    var treeLabel by mutableStateOf("")
        private set
    var treeRisk by mutableStateOf(StorageSafety.TreeRisk.NONE)
        private set
    var scanning by mutableStateOf(false)
        private set

    /** Typed scan failure: the reason drives the friendly message and the actions. */
    data class ScanFailure(val reason: com.sortfold.app.core.scanner.ScanFailedException.Reason?, val detail: String)

    var scanError by mutableStateOf<ScanFailure?>(null)
        private set
    var mediaReadDenied by mutableStateOf(false)
        private set

    var files by mutableStateOf<List<MediaFile>>(emptyList())
        private set
    var subFolders by mutableStateOf<List<String>>(emptyList())
        private set

    var selectedModes by mutableStateOf(setOf(SortMode.FILE_TYPE))
        private set
    var granularity by mutableStateOf(DateGranularity.MONTH)
        private set
    var policy by mutableStateOf(DuplicatePolicy.SKIP)
        private set
    val nameRules = mutableStateListOf<NameRule>()

    // ---- 1.2.0 capacity state (Split by capacity) ----
    var capacityBytes by mutableStateOf<Long?>(null)
        private set
    var capacityOrder by mutableStateOf(CapacityOrder.SEQUENTIAL)
        private set
    var capacityPrefix by mutableStateOf(CapacityPacker.DEFAULT_PREFIX)
        private set
    /** Unit of the custom value field: "MB" | "GB". */
    var capacityUnit by mutableStateOf("GB")
        private set
    var capacityPresetIndex by mutableStateOf(-1) // 0..3 presets, 4 custom, -1 none
        private set
    var capacityError by mutableStateOf<String?>(null)
        private set
    var capacityEstimate by mutableStateOf<CapacityEstimate?>(null)
        private set

    /** Raw custom value text; lives in the VM so rotation keeps it. */
    var capacityCustomText by mutableStateOf("")

    /** True once the saved-defaults init job has run (test seam + guard). */
    var defaultsApplied by mutableStateOf(false)
        private set

    /** Any explicit user choice on the Modes step must win over saved defaults. */
    private var userAdjusted = false

    /**
     * NOTE: every property the init coroutine touches MUST be declared BEFORE
     * this init block. With Dispatchers.Main.immediate a warm DataStore read
     * can complete synchronously DURING construction; touching a delegated
     * property declared later than the init block would dereference a null
     * delegate and kill the coroutine (BUG-19).
     */
    /** Set from DataStore init; consumed by FolderStep to preselect the folder. */
    var pendingDefaultTree by mutableStateOf<String?>(null)
        private set
    var dateStyleIso by mutableStateOf(true)
        private set

    init {
        // Apply the user's saved defaults — but never stomp on choices the user
        // already made while the DataStore read was in flight (BUG-17).
        viewModelScope.launch {
            val s = container.settingsRepository.snapshot()
            if (!userAdjusted) {
                selectedModes = setOf(s.defaultSortMode)
                policy = s.defaultDuplicatePolicy
                granularity = s.defaultDateGranularity
                dateStyleIso = s.namingStyleIso
                // 1.2.0: saved capacity defaults prefill the panel.
                capacityBytes = s.capacityDefaultBytes
                capacityOrder = s.capacityDefaultOrder
                capacityPrefix = s.capacityDefaultPrefix
                capacityUnit = s.capacityDefaultUnit
            }
            s.defaultDestTreeUri?.let { pendingDefaultTree = it }
            defaultsApplied = true
        }
    }

    fun consumePendingDefaultTree(): String? {
        val v = pendingDefaultTree
        pendingDefaultTree = null
        return v
    }

    fun setModes(modes: Set<SortMode>) {
        userAdjusted = true
        selectedModes = modes
        refreshLivePreview()
        refreshEstimate()
    }

    fun toggleMode(mode: SortMode, checked: Boolean) {
        userAdjusted = true
        selectedModes = if (checked) selectedModes + mode else selectedModes - mode
        refreshLivePreview()
        refreshEstimate()
    }

    fun choosePolicy(p: DuplicatePolicy) {
        userAdjusted = true
        policy = p
    }

    fun chooseGranularity(g: DateGranularity) {
        userAdjusted = true
        granularity = g
    }

    fun chooseCapacity(bytes: Long?, presetIndex: Int) {
        userAdjusted = true
        capacityBytes = bytes
        capacityPresetIndex = presetIndex
        capacityError = null
        refreshLivePreview()
        refreshEstimate()
    }

    fun chooseCapacityOrder(order: CapacityOrder) {
        userAdjusted = true
        capacityOrder = order
        refreshLivePreview()
        refreshEstimate()
    }

    fun chooseCapacityPrefix(prefix: String) {
        userAdjusted = true
        capacityPrefix = CapacityPacker.sanitizePrefix(prefix)
        refreshLivePreview()
        refreshEstimate()
    }

    fun chooseCapacityUnit(unit: String) {
        userAdjusted = true
        capacityUnit = unit
    }

    /** UI-side validation message; safe to call from non-composable callbacks. */
    fun reportCapacityError(message: String?) {
        capacityError = message
    }

    var suggestions by mutableStateOf<List<ModeSuggester.Suggestion>>(emptyList())
        private set
    var planTotals by mutableStateOf(RuleEngine.PlanTotals(0, 0, 0))
        private set
    var warnings by mutableStateOf<List<WizardWarning>>(emptyList())
        private set
    var hasBlockingWarning by mutableStateOf(false)
        private set

    /** B-01: the plan contains replacements; the UI confirms before applying. */
    var hasReplacePlan by mutableStateOf(false)
        private set

    var jobId by mutableStateOf<Long?>(null)
        private set
    var undoBusy by mutableStateOf(false)
        private set
    var undoResult by mutableStateOf<Pair<Int, Int>?>(null) // restored, failed
        private set

    /** Live preview for the expanded two-pane layout. */
    var livePreview by mutableStateOf<List<PlanItem>>(emptyList())
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    private val jobIdFlowInternal = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val job = jobIdFlowInternal.flatMapLatest { id ->
        if (id == null) flowOf(null) else container.database.sortJobDao().observeById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val previewRows = jobIdFlowInternal.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else container.database.moveLogDao().observeByJobPaged(id, 300)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun updateJobId(id: Long?) {
        jobId = id
        jobIdFlowInternal.value = id
    }

    fun advanceToResult() {
        step = WizardStep.RESULT
    }

    fun goBack() {
        step = when (step) {
            WizardStep.MODES -> WizardStep.FOLDER
            WizardStep.PREVIEW -> WizardStep.MODES
            else -> step
        }
    }

    /** Stepper jump back to the folder step. */
    fun goBackToFolder() {
        step = WizardStep.FOLDER
    }

    fun setFolder(uri: Uri, context: Context) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        treeUri = uri.toString()
        treeLabel = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "?"
        treeRisk = StorageSafety.classifyTree(uri)
        scan(context)
    }

    fun scan(context: Context) {
        val uri = treeUri ?: return
        scanning = true
        scanError = null
        viewModelScope.launch {
            try {
                val result = container.scanner.scan(Uri.parse(uri))
                files = result.files
                subFolders = result.subFolders
                suggestions = ModeSuggester.suggest(files, treeLabel)
                refreshLivePreview()
                refreshEstimate()
                scanning = false
            } catch (e: com.sortfold.app.core.scanner.ScanFailedException) {
                files = emptyList()
                scanning = false
                scanError = ScanFailure(e.reason, e.message ?: e.javaClass.simpleName)
                container.errorRepository.log(
                    "scanner", com.sortfold.app.error.ErrorRepository.Severity.ERROR,
                    e.javaClass.simpleName, "Scan failed for $treeLabel: ${e.message}", e.stackTraceToString(),
                )
            } catch (e: Exception) {
                files = emptyList()
                scanning = false
                scanError = ScanFailure(null, e.message ?: "scan-failed")
                container.errorRepository.log(
                    "scanner", com.sortfold.app.error.ErrorRepository.Severity.ERROR,
                    e.javaClass.simpleName, "Scan failed for $treeLabel: ${e.message}", e.stackTraceToString(),
                )
            }
        }
    }

    fun onMediaPermissionsResult(denied: Boolean) {
        mediaReadDenied = denied
    }

    fun refreshLivePreview() {
        if (selectedModes.isEmpty() || files.isEmpty()) {
            livePreview = emptyList()
            return
        }
        val config = currentConfig()
        // CAPACITY segments depend on the whole group, so the plan must always
        // run over the FULL list; the UI only displays a slice of the result.
        // Other modes stay on the 40-file live slice.
        val input = if (SortMode.CAPACITY in selectedModes) files else files.take(40)
        livePreview = RuleEngine.plan(input, config)
    }

    private var estimateJob: Job? = null

    /**
     * Live estimate line: "About N folders · largest Part 3.94 GB", computed
     * from the FULL scan. Recalculation is debounced (150 ms) and packed off
     * the main thread.
     */
    fun refreshEstimate() {
        estimateJob?.cancel()
        if (SortMode.CAPACITY !in selectedModes || capacityBytes == null || files.isEmpty()) {
            capacityEstimate = null
            return
        }
        estimateJob = viewModelScope.launch(Dispatchers.Default) {
            delay(150)
            val config = currentConfig()
            val plan = RuleEngine.plan(files, config)
            val folders = plan.map { it.destinationFolder }.filter { it.isNotEmpty() }.distinct()
            val partFolders = folders.filter { folder ->
                val name = folder.substringAfterLast('/')
                name.startsWith(CapacityPacker.sanitizePrefix(config.capacityPrefix) + " ") ||
                    name == CapacityPacker.OVERSIZED_FOLDER
            }
            val largest = plan.filter { it.action != PlanAction.SKIP_DUPLICATE }
                .groupBy { it.destinationFolder }
                .filterKeys { key -> key.substringAfterLast('/').startsWith(CapacityPacker.sanitizePrefix(config.capacityPrefix) + " ") }
                .mapValues { (_, items) -> items.sumOf { it.sizeBytes } }
                .maxOfOrNull { it.value } ?: 0L
            capacityEstimate = CapacityEstimate(partFolders.size, largest, RuleEngine.oversizedCount(plan))
        }
    }

    private fun currentConfig() = SortConfig(
        modes = selectedModes,
        dateGranularity = granularity,
        dateStyleIso = dateStyleIso,
        nameRules = nameRules.toList(),
        duplicatePolicy = policy,
        treePath = treeLabel,
        capacityBytes = if (SortMode.CAPACITY in selectedModes) capacityBytes else null,
        capacityOrder = capacityOrder,
        capacityPrefix = capacityPrefix,
    )

    fun goToModes() {
        if (selectedModes.isEmpty()) selectedModes = setOf(SortMode.FILE_TYPE)
        step = WizardStep.MODES
        refreshLivePreview()
        refreshEstimate()
    }

    /** Builds the dry-run plan in Room and computes warnings. */
    fun buildPreview(context: Context) {
        val uri = treeUri ?: return
        val config = currentConfig()
        viewModelScope.launch(Dispatchers.IO) {
            // Enrich metadata only when the selected modes need it.
            val needDims = SortMode.RESOLUTION in selectedModes
            val needDates = SortMode.DATE_TAKEN in selectedModes
            val enriched = com.sortfold.app.core.scanner.MetadataReader.enrich(
                context.contentResolver, files, Uri.parse(uri), needDims, needDates,
            )
            files = enriched

            // B-01: list the real names of every destination folder (cached per
            // folder; folders that do not exist yet are empty) so RENAME and
            // REPLACE plans reflect reality.
            val mover = container.mover
            val tree = Uri.parse(uri)
            val rootDocId = runCatching { com.sortfold.app.core.mover.Mover.treeRootDocId(tree) }.getOrNull()
            val existingNames = if (rootDocId != null) {
                val out = HashMap<String, Set<String>>()
                for (base in RuleEngine.baseFolders(files, config)) {
                    val segments = base.split('/').filter { it.isNotEmpty() }
                    val docId = if (segments.isEmpty()) rootDocId else mover.resolveFolder(tree, rootDocId, segments)
                    out[base] = if (docId == null) emptySet() else runCatching { mover.listNames(tree, docId) }.getOrElse { emptySet() }
                }
                out
            } else {
                emptyMap()
            }

            val plan = RuleEngine.plan(files, config, existingNames)
            val totals = RuleEngine.totals(plan)
            planTotals = totals
            hasReplacePlan = plan.any { it.action == PlanAction.REPLACE }

            val destTree = Uri.parse(uri)
            val w = ArrayList<WizardWarning>()
            if (totals.moveCount >= container.settingsRepository.snapshot().batchFileThreshold) {
                w += WizardWarning("batch_files", false)
            }
            if (totals.moveBytes >= container.settingsRepository.snapshot().batchBytesThreshold) {
                w += WizardWarning("batch_bytes", false)
            }
            // B-05: volume-aware storage verdict — the peak need is the largest
            // single file, and moveDocument needs no extra space at all.
            val largestFile = files.maxOfOrNull { it.sizeBytes } ?: 0L
            val moveSupported = runCatching { mover.supportsMove(tree) }.getOrDefault(false)
            when (StorageSafety.evaluate(context, tree, largestFile, moveSupported)) {
                StorageSafety.SpaceVerdict.INSUFFICIENT -> w += WizardWarning("insufficient_storage", true)
                StorageSafety.SpaceVerdict.LOW -> w += WizardWarning("low_storage", false)
                StorageSafety.SpaceVerdict.UNKNOWN -> w += WizardWarning("low_storage", false)
                StorageSafety.SpaceVerdict.OK -> {}
            }
            if (hasReplacePlan) {
                w += WizardWarning("replacing", false)
            }
            if (RuleEngine.oversizedCount(plan) > 0) {
                w += WizardWarning("capacity_oversized", false, RuleEngine.oversizedCount(plan))
            }
            // Destination is always inside the source tree in this design, so a
            // cross-storage copy+delete path never happens by construction.
            if (treeRisk != StorageSafety.TreeRisk.NONE) {
                w += WizardWarning(
                    when (treeRisk) {
                        StorageSafety.TreeRisk.STORAGE_ROOT -> "storage_root"
                        StorageSafety.TreeRisk.APP_PRIVATE -> "app_private"
                        else -> "system_folder"
                    },
                    treeRisk != StorageSafety.TreeRisk.STORAGE_ROOT,
                )
            }
            warnings = w
            hasBlockingWarning = w.any { it.blocking }

            // 1.2.0: last used capacity choices become the saved defaults.
            if (SortMode.CAPACITY in selectedModes && capacityBytes != null) {
                container.settingsRepository.setCapacityDefaults(
                    capacityBytes, capacityOrder, CapacityPacker.sanitizePrefix(capacityPrefix), capacityUnit,
                )
            }

            if (totals.moveCount == 0) {
                withContext(Dispatchers.Main) { step = WizardStep.PREVIEW }
                return@launch
            }

            val now = System.currentTimeMillis()
            val newJobId: Long
            val existingJobId = jobId
            if (existingJobId != null) {
                // Rebuild of an existing plan: reuse the job row instead of
                // piling up orphan PLANNED jobs in History (BUG-18).
                newJobId = existingJobId
                container.database.moveLogDao().deleteForJob(newJobId)
                // B-13: refresh every plan-related column, not just totals —
                // modes/policy/capacity changed when the user went back.
                container.database.sortJobDao().updatePlanColumns(
                    newJobId, totals.moveCount, totals.moveBytes,
                    SortMode.ordered(selectedModes).joinToString(",") { it.name },
                    policy.name,
                    if (SortMode.CAPACITY in selectedModes) capacityBytes else null,
                )
            } else {
                newJobId = container.database.sortJobDao().insert(
                    SortJobEntity(
                        treeUri = uri, destTreeUri = uri,
                        modesCsv = SortMode.ordered(selectedModes).joinToString(",") { it.name },
                        duplicatePolicy = policy.name,
                        status = "PLANNED", totalFiles = totals.moveCount,
                        doneFiles = 0, totalBytes = totals.moveBytes, doneBytes = 0,
                        createdAt = now, updatedAt = now,
                        capacityBytes = if (SortMode.CAPACITY in selectedModes) capacityBytes else null,
                    ),
                )
            }
            container.database.moveLogDao().insertAll(
                plan.filter { it.action != PlanAction.SKIP_DUPLICATE }.mapIndexed { i, p ->
                    MoveLogEntity(
                        jobId = newJobId, seq = i, sourceDocId = p.documentId,
                        displayName = p.displayName, mime = p.mime,
                        destFolder = p.destinationFolder, destDocId = null,
                        destName = p.destinationName, sizeBytes = p.sizeBytes,
                        status = "PLANNED",
                        detail = when (p.action) {
                            PlanAction.RENAME -> "renamed:${p.displayName}"
                            PlanAction.REPLACE -> "replace"
                            else -> null
                        },
                    )
                },
            )
            withContext(Dispatchers.Main) {
                updateJobId(newJobId)
                step = WizardStep.PREVIEW
            }
        }
    }

    fun apply(context: Context) {
        val id = jobId ?: return
        SortWorker.enqueue(context, id)
        step = WizardStep.APPLY
    }

    /**
     * B-02: pause/cancel are cooperative — a flag per job. The current file
     * finishes and is logged, then the worker stops; no coroutine is killed
     * mid-file, so nothing is stuck as RUNNING.
     */
    fun pause(context: Context) {
        val id = jobId ?: return
        SortWorker.requestStop(id, SortWorker.RequestedState.PAUSED)
    }

    fun cancel(context: Context) {
        val id = jobId ?: return
        SortWorker.requestStop(id, SortWorker.RequestedState.CANCELLED)
    }

    fun resume(context: Context) {
        val id = jobId ?: return
        SortWorker.enqueue(context, id)
    }

    fun undo(context: Context) {
        val id = jobId ?: return
        undoBusy = true
        container.appScope.launch {
            val result = container.undoManager.undoJob(id)
            withContext(Dispatchers.Main) {
                undoBusy = false
                undoResult = result.restored to result.failed
            }
        }
    }

    /** B-12: a reset clears EVERY field and re-applies the saved defaults. */
    fun reset() {
        step = WizardStep.FOLDER
        treeUri = null
        treeLabel = ""
        files = emptyList()
        subFolders = emptyList()
        selectedModes = setOf(SortMode.FILE_TYPE)
        policy = DuplicatePolicy.SKIP
        granularity = DateGranularity.MONTH
        nameRules.clear()
        updateJobId(null)
        warnings = emptyList()
        hasBlockingWarning = false
        hasReplacePlan = false
        planTotals = RuleEngine.PlanTotals(0, 0, 0)
        undoResult = null
        undoBusy = false
        suggestions = emptyList()
        livePreview = emptyList()
        scanError = null
        scanning = false
        mediaReadDenied = false
        pendingDefaultTree = null
        dateStyleIso = true
        capacityBytes = null
        capacityOrder = CapacityOrder.SEQUENTIAL
        capacityPrefix = CapacityPacker.DEFAULT_PREFIX
        capacityUnit = "GB"
        capacityPresetIndex = -1
        capacityError = null
        capacityEstimate = null
        userAdjusted = false
        // Re-apply saved defaults asynchronously (same path as init).
        viewModelScope.launch {
            val s = container.settingsRepository.snapshot()
            if (!userAdjusted) {
                selectedModes = setOf(s.defaultSortMode)
                policy = s.defaultDuplicatePolicy
                granularity = s.defaultDateGranularity
                dateStyleIso = s.namingStyleIso
                capacityBytes = s.capacityDefaultBytes
                capacityOrder = s.capacityDefaultOrder
                capacityPrefix = s.capacityDefaultPrefix
                capacityUnit = s.capacityDefaultUnit
            }
            s.defaultDestTreeUri?.let { pendingDefaultTree = it }
            defaultsApplied = true
        }
    }
}
