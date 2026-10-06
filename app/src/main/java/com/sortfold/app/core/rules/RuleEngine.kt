package com.sortfold.app.core.rules

import com.sortfold.app.core.model.CapacityOrder
import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.DuplicatePolicy
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
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.SKIP,
    /** display name of the source tree, e.g. "DCIM" — used for source-app guessing */
    val treePath: String = "",
    /** 1.2.0 CAPACITY: max bytes per part folder; null = unlimited/unset. */
    val capacityBytes: Long? = null,
    val capacityOrder: CapacityOrder = CapacityOrder.SEQUENTIAL,
    /** Sanitised by [CapacityPacker.sanitizePrefix] before use. */
    val capacityPrefix: String = CapacityPacker.DEFAULT_PREFIX,
    /** Decimal GB (1,000,000,000 bytes) when true; binary GiB otherwise. UI-only. */
    val capacityUnitDecimal: Boolean = true,
    /**
     * Auto-sort top-up: full destination folder path (e.g. "Images/Part 01")
     * to bytes already stored there, from folders created by earlier runs.
     */
    val existingFolderUsage: Map<String, Long> = emptyMap(),
) {
    /** True when the plan actually applies the capacity split. */
    val capacityActive: Boolean
        get() = SortMode.CAPACITY in modes && capacityBytes != null && capacityBytes > 0
}

/**
 * Turns a scan into a dry-run plan. Folder segments are plain ASCII names so
 * that destinations stay stable across locales and file systems.
 */
object RuleEngine {

    /**
     * @param existingNames destination folder path -> names already on disk
     *   there (B-01: without this the plan cannot know what RENAME/REPLACE
     *   would actually do). Folders not present in the map are treated as
     *   empty.
     */
    fun plan(
        files: List<MediaFile>,
        config: SortConfig,
        existingNames: Map<String, Set<String>> = emptyMap(),
    ): List<PlanItem> {
        val ordered = SortMode.ordered(config.modes).filter { it != SortMode.CAPACITY }
        // Name collisions are scoped PER DESTINATION FOLDER: two files with the
        // same name heading to different folders never collide (a global set
        // used to rename files whose destination was actually free).
        val takenByFolder = HashMap<String, MutableSet<String>>()
        val plan = ArrayList<PlanItem>(files.size)
        val candidates = files.filter { !it.isDirectory }

        if (!config.capacityActive) {
            for (file in candidates) {
                // Empty segments (e.g. NAME_PATTERN with no matching rule) are
                // dropped, so destinations never end up with trailing slashes.
                val folder = ordered.map { segmentFor(it, file, config) }
                    .filter { it.isNotEmpty() }
                    .joinToString("/")
                plan += resolveInto(file, folder, takenByFolder, existingNames, config)
            }
            return plan
        }

        // CAPACITY is always the LAST nesting level: group by the folder path
        // produced by the other modes and pack each group independently, so a
        // file's part segment depends on the other files in its group.
        val capacityBytes = config.capacityBytes!!
        val cleanPrefix = CapacityPacker.sanitizePrefix(config.capacityPrefix)
        val groups = LinkedHashMap<String, MutableList<MediaFile>>()
        for (file in candidates) {
            val base = ordered.map { segmentFor(it, file, config) }
                .filter { it.isNotEmpty() }
                .joinToString("/")
            groups.getOrPut(base) { ArrayList() } += file
        }
        for ((base, group) in groups) {
            val usage = existingFoldersForGroup(base, cleanPrefix, config.existingFolderUsage)
            val packed = CapacityPacker.pack(group, capacityBytes, config.capacityOrder, cleanPrefix, usage)
            for (part in packed.folders) {
                val full = if (base.isEmpty()) part.name else "$base/${part.name}"
                for (file in part.files) {
                    plan += resolveInto(file, full, takenByFolder, existingNames, config)
                }
            }
            if (packed.oversized.isNotEmpty()) {
                val full = if (base.isEmpty()) CapacityPacker.OVERSIZED_FOLDER else "$base/${CapacityPacker.OVERSIZED_FOLDER}"
                for (file in packed.oversized) {
                    val item = resolveInto(file, full, takenByFolder, existingNames, config)
                    plan += item.copy(reason = item.reason ?: "oversized")
                }
            }
        }
        return plan
    }

    /** Existing part folders below [base] translated into packer terms. */
    private fun existingFoldersForGroup(
        base: String,
        prefix: String,
        usage: Map<String, Long>,
    ): List<CapacityPacker.ExistingFolder> {
        val prefixPath = if (base.isEmpty()) "" else "$base/"
        return usage.mapNotNull { (path, bytes) ->
            val name = path.removePrefix(prefixPath)
            if (base.isEmpty() && path.contains('/')) return@mapNotNull null
            if (name.isEmpty() || name.contains('/')) return@mapNotNull null
            val index = CapacityPacker.parseIndex(name, prefix) ?: return@mapNotNull null
            CapacityPacker.ExistingFolder(name, index, bytes)
        }
    }

    /** Collision handling scoped to one destination folder. */
    private fun resolveInto(
        file: MediaFile,
        folder: String,
        takenByFolder: HashMap<String, MutableSet<String>>,
        existingNames: Map<String, Set<String>>,
        config: SortConfig,
    ): PlanItem {
        if (folder.isEmpty()) {
            // No mode selected: nothing to do, still surface the file so the
            // preview explains itself.
            return PlanItem(
                file.documentId, file.displayName, file.sizeBytes, file.displayName,
                "", file.displayName, PlanAction.SKIP_DUPLICATE, "no-sort-mode", file.mime,
            )
        }
        val taken = takenByFolder.getOrPut(folder) {
            // Seed with the real names on disk for this folder; in-app plan
            // collisions are tracked exactly on top (B-01).
            HashSet(existingNames[folder] ?: emptySet())
        }
        val desired = file.displayName
        val (finalName, action, reason) = resolveCollision(desired, taken, config.duplicatePolicy)
        if (action == PlanAction.SKIP_DUPLICATE) {
            return PlanItem(file.documentId, desired, file.sizeBytes, file.displayName, folder, finalName, action, reason, file.mime)
        }
        taken += finalName
        return PlanItem(file.documentId, desired, file.sizeBytes, file.displayName, folder, finalName, action, reason, file.mime)
    }

    private fun resolveCollision(
        desired: String,
        taken: Set<String>,
        policy: DuplicatePolicy,
    ): Triple<String, PlanAction, String?> = when (policy) {
        DuplicatePolicy.SKIP ->
            if (desired in taken) Triple(desired, PlanAction.SKIP_DUPLICATE, "duplicate") else Triple(desired, PlanAction.MOVE, null)
        DuplicatePolicy.RENAME ->
            if (desired in taken) {
                val candidate = DuplicatePolicy.renameCandidate(taken, desired)
                Triple(candidate, PlanAction.RENAME, "renamed:$desired")
            } else Triple(desired, PlanAction.MOVE, null)
        DuplicatePolicy.REPLACE ->
            Triple(desired, if (desired in taken) PlanAction.REPLACE else PlanAction.MOVE, if (desired in taken) "replaces:$desired" else null)
    }

    fun segmentFor(mode: SortMode, file: MediaFile, config: SortConfig): String = when (mode) {
        SortMode.FILE_TYPE -> when (file.type) {
            MediaType.IMAGE -> "Images"
            MediaType.VIDEO -> "Videos"
            MediaType.OTHER -> "Other"
        }
        SortMode.DATE_TAKEN -> dateSegment(file.effectiveDateMillis, config.dateGranularity, iso = config.dateStyleIso)
        SortMode.SOURCE_APP -> SourceApp.forFile(file.displayName, config.treePath).label
        SortMode.RESOLUTION -> ResolutionClass.of(file.width, file.height).label
        SortMode.SIZE -> SizeBucket.of(file.sizeBytes).label
        SortMode.EXTENSION -> file.extension.ifEmpty { "unknown" }
        SortMode.NAME_PATTERN -> firstMatchingRule(file.displayName, config.nameRules)?.targetFolder ?: ""
        SortMode.CAPACITY -> "" // never a direct segment; applied by packing per group
    }

    /** Distinct base folder paths (without the capacity part) a plan would use. */
    fun baseFolders(files: List<MediaFile>, config: SortConfig): Set<String> {
        val ordered = SortMode.ordered(config.modes).filter { it != SortMode.CAPACITY }
        return files.filter { !it.isDirectory }
            .map { f -> ordered.map { segmentFor(it, f, config) }.filter { it.isNotEmpty() }.joinToString("/") }
            .toSet()
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

    /** Files the packer could not fit under the cap (reason "oversized"). */
    fun oversizedCount(items: List<PlanItem>): Int = items.count { it.reason == "oversized" }
}
