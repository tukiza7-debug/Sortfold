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
import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.MediaFile
import com.sortfold.app.core.model.NameRule
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.model.PlanItem
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.core.mover.StorageSafety
import com.sortfold.app.core.rules.ModeSuggester
import com.sortfold.app.core.rules.RuleEngine
import com.sortfold.app.core.rules.SortConfig
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortJobEntity
import com.sortfold.app.work.SortWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WizardStep { FOLDER, MODES, PREVIEW, APPLY, RESULT }

data class WizardWarning(val key: String, val blocking: Boolean)

/**
 * Holds the whole wizard across rotation and process death: the job and its
 * planned moves live in Room, in-memory settings survive via the ViewModel.
 */
class WizardViewModel(private val container: AppContainer) : ViewModel() {

    var step by mutableStateOf(WizardStep.FOLDER)
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
    }

    fun toggleMode(mode: SortMode, checked: Boolean) {
        userAdjusted = true
        selectedModes = if (checked) selectedModes + mode else selectedModes - mode
        refreshLivePreview()
    }

    fun choosePolicy(p: DuplicatePolicy) {
        userAdjusted = true
        policy = p
    }

    fun chooseGranularity(g: DateGranularity) {
        userAdjusted = true
        granularity = g
    }

    var suggestions by mutableStateOf<List<ModeSuggester.Suggestion>>(emptyList())
        private set
    var planTotals by mutableStateOf(RuleEngine.PlanTotals(0, 0, 0))
        private set
    var warnings by mutableStateOf<List<WizardWarning>>(emptyList())
        private set
    var hasBlockingWarning by mutableStateOf(false)
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
        livePreview = RuleEngine.plan(files.take(40), config)
    }

    private fun currentConfig() = SortConfig(
        modes = selectedModes,
        dateGranularity = granularity,
        dateStyleIso = dateStyleIso,
        nameRules = nameRules.toList(),
        duplicatePolicy = policy,
        treePath = treeLabel,
    )

    fun goToModes() {
        if (selectedModes.isEmpty()) selectedModes = setOf(SortMode.FILE_TYPE)
        step = WizardStep.MODES
        refreshLivePreview()
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
            val plan = RuleEngine.plan(files, config)
            val totals = RuleEngine.totals(plan)
            planTotals = totals

            val destTree = Uri.parse(uri)
            val w = ArrayList<WizardWarning>()
            if (totals.moveCount >= container.settingsRepository.snapshot().batchFileThreshold) {
                w += WizardWarning("batch_files", false)
            }
            if (totals.moveBytes >= container.settingsRepository.snapshot().batchBytesThreshold) {
                w += WizardWarning("batch_bytes", false)
            }
            if (StorageSafety.isInsufficientStorage(context, totals.moveBytes)) {
                w += WizardWarning("insufficient_storage", true)
            } else if (StorageSafety.isLowStorage(context, totals.moveBytes)) {
                w += WizardWarning("low_storage", false)
            }
            if (plan.any { it.action == PlanAction.REPLACE }) {
                w += WizardWarning("replacing", false)
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

            if (totals.moveCount == 0) {
                step = WizardStep.PREVIEW
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
                container.database.sortJobDao().updatePlan(newJobId, totals.moveCount, totals.moveBytes)
            } else {
                newJobId = container.database.sortJobDao().insert(
                    SortJobEntity(
                        treeUri = uri, destTreeUri = uri,
                        modesCsv = SortMode.ordered(selectedModes).joinToString(",") { it.name },
                        duplicatePolicy = policy.name,
                        status = "PLANNED", totalFiles = totals.moveCount,
                        doneFiles = 0, totalBytes = totals.moveBytes, doneBytes = 0,
                        createdAt = now, updatedAt = now,
                    ),
                )
            }
            container.database.moveLogDao().insertAll(
                plan.filter { it.action != PlanAction.SKIP_DUPLICATE }.mapIndexed { i, p ->
                    MoveLogEntity(
                        jobId = newJobId, seq = i, sourceDocId = p.documentId,
                        displayName = p.displayName, mime = null,
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

    fun pause(context: Context) {
        val id = jobId ?: return
        SortWorker.requestStop(id, SortWorker.RequestedState.PAUSED)
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork(SortWorker.workName(id))
    }

    fun cancel(context: Context) {
        val id = jobId ?: return
        SortWorker.requestStop(id, SortWorker.RequestedState.CANCELLED)
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork(SortWorker.workName(id))
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
        planTotals = RuleEngine.PlanTotals(0, 0, 0)
        undoResult = null
        suggestions = emptyList()
    }
}
