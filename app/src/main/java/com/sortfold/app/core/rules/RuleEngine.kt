package com.sortfold.app.core.rules

import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.MediaFile
import com.sortfold.app.core.model.MediaType
import com.sortfold.app.core.model.NameRule
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.model.PlanItem
import com.sortfold.app.core.model.ResolutionClass
import com.sortfold.app.core.model.SizeBucket
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.core.model.SourceApp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything the engine needs to produce a plan for one folder scan. */
data class SortConfig(
    val modes: Set<SortMode>,
    val dateGranularity: DateGranularity = DateGranularity.MONTH,
    val dateStyleIso: Boolean = true,
    val nameRules: List<NameRule> = emptyList(),
    val duplicatePolicy: com.sortfold.app.core.model.DuplicatePolicy =
        com.sortfold.app.core.model.DuplicatePolicy.SKIP,
    /** display name of the source tree, e.g. "DCIM" — used for source-app guessing */
    val treePath: String = "",
)

/**
 * Turns a scan into a dry-run plan. Folder segments are plain ASCII names so
 * that destinations stay stable across locales and file systems.
 */
object RuleEngine {

    fun plan(files: List<MediaFile>, config: SortConfig, existingNames: Set<String> = emptySet()): List<PlanItem> {
        val ordered = SortMode.ordered(config.modes)
        val taken = HashSet(existingNames)
        return files.filter { !it.isDirectory }.map { file ->
            val folder = ordered.joinToString("/") { segmentFor(it, file, config) }
            if (folder.isEmpty()) {
                // No mode selected: nothing to do, still surface the file so the
                // preview explains itself.
                PlanItem(file.documentId, file.displayName, file.sizeBytes, file.displayName, "", file.displayName, PlanAction.SKIP_DUPLICATE, "no-sort-mode")
            } else {
                val desired = file.displayName
                val (finalName, action, reason) = resolveCollision(desired, taken, config.duplicatePolicy)
                if (action == PlanAction.SKIP_DUPLICATE) {
                    PlanItem(file.documentId, desired, file.sizeBytes, file.displayName, folder, finalName, action, reason)
                } else {
                    taken += finalName
                    PlanItem(file.documentId, desired, file.sizeBytes, file.displayName, folder, finalName, action, reason)
                }
            }
        }
    }

    private fun resolveCollision(
        desired: String,
        taken: Set<String>,
        policy: com.sortfold.app.core.model.DuplicatePolicy,
    ): Triple<String, PlanAction, String?> = when (policy) {
        com.sortfold.app.core.model.DuplicatePolicy.SKIP ->
            if (desired in taken) Triple(desired, PlanAction.SKIP_DUPLICATE, "duplicate") else Triple(desired, PlanAction.MOVE, null)
        com.sortfold.app.core.model.DuplicatePolicy.RENAME ->
            if (desired in taken) {
                val candidate = com.sortfold.app.core.model.DuplicatePolicy.renameCandidate(taken, desired)
                Triple(candidate, PlanAction.RENAME, "renamed:$desired")
            } else Triple(desired, PlanAction.MOVE, null)
        com.sortfold.app.core.model.DuplicatePolicy.REPLACE ->
            Triple(desired, if (desired in taken) PlanAction.REPLACE else PlanAction.MOVE, if (desired in taken) "replaces:$desired" else null)
    }

    fun segmentFor(mode: SortMode, file: MediaFile, config: SortConfig): String = when (mode) {
        SortMode.FILE_TYPE -> when (file.type) {
            MediaType.IMAGE -> "Images"
            MediaType.VIDEO -> "Videos"
            MediaType.OTHER -> "Other"
        }
        SortMode.DATE_TAKEN -> dateSegment(file.effectiveDateMillis, config.dateGranularity, iso = config.dateStyleIso)
        SortMode.SOURCE_APP -> SourceApp.fromPath(config.treePath).label
        SortMode.RESOLUTION -> ResolutionClass.of(file.width, file.height).label
        SortMode.SIZE -> SizeBucket.of(file.sizeBytes).label
        SortMode.EXTENSION -> file.extension.ifEmpty { "unknown" }
        SortMode.NAME_PATTERN -> firstMatchingRule(file.displayName, config.nameRules)?.targetFolder ?: ""
    }

    fun dateSegment(
        epochMillis: Long,
        granularity: DateGranularity,
        zone: ZoneId = ZoneId.systemDefault(),
        iso: Boolean = true,
    ): String {
        val date: LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return when (granularity) {
            DateGranularity.YEAR -> date.year.toString()
            DateGranularity.MONTH ->
                if (iso) "%04d-%02d".format(date.year, date.monthValue)
                else date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + date.year
        }
    }

    fun firstMatchingRule(fileName: String, rules: List<NameRule>): NameRule? =
        rules.firstOrNull { it.matches(fileName) }

    /** Combined plan totals, used for the confirm dialog and warnings. */
    data class PlanTotals(val moveCount: Int, val skipCount: Int, val moveBytes: Long)

    fun totals(items: List<PlanItem>): PlanTotals = PlanTotals(
        moveCount = items.count { it.action != PlanAction.SKIP_DUPLICATE },
        skipCount = items.count { it.action == PlanAction.SKIP_DUPLICATE },
        moveBytes = items.filter { it.action != PlanAction.SKIP_DUPLICATE }.sumOf { it.sizeBytes },
    )
}
