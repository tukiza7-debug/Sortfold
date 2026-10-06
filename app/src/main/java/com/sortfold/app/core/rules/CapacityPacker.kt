package com.sortfold.app.core.rules

import com.sortfold.app.core.model.CapacityOrder
import com.sortfold.app.core.model.MediaFile

/**
 * Pure-Kotlin packer for the CAPACITY sort mode (1.2.0): distributes files
 * into "Part NN" folders that never exceed a byte capacity.
 *
 * No Android types — fully unit testable. The engine groups files by the
 * folder path produced by the other selected modes and calls [pack] once per
 * group, so a file's final segment always depends on the other files in its
 * group, never on the file alone.
 */
object CapacityPacker {

    /** Fixed name of the folder that receives files larger than the cap. */
    const val OVERSIZED_FOLDER = "Oversized"

    const val DEFAULT_PREFIX = "Part"

    /** One already-existing part folder from an earlier run (auto-sort top-up). */
    data class ExistingFolder(
        val name: String,
        val index: Int,
        val usedBytes: Long,
    )

    data class PartFolder(
        val name: String,
        val files: List<MediaFile>,
        val totalBytes: Long,
    )

    data class PackResult(
        /** Part folders in order; every total is strictly <= capacityBytes. */
        val folders: List<PartFolder>,
        /** Files larger than the cap: they go to "Oversized" inside their group. */
        val oversized: List<MediaFile>,
    )

    /**
     * Sanitises the user-chosen folder prefix: trims, removes characters that
     * are illegal in folder names, caps length at 24 and falls back to "Part".
     */
    fun sanitizePrefix(raw: String?): String {
        val cleaned = (raw.orEmpty())
            .trim()
            .replace(Regex("[/\\\\:*?\"<>|]"), "")
        val capped = if (cleaned.length > 24) cleaned.take(24) else cleaned
        return capped.ifEmpty { DEFAULT_PREFIX }
    }

    /** "Part 01" (prefix "Part") -> 1; "Part 100" -> 100; non-members -> null. */
    fun parseIndex(folderName: String, prefix: String): Int? {
        val rest = folderName.removePrefix("$prefix ")
        if (rest == folderName || rest.isEmpty()) return null
        return rest.toIntOrNull()?.takeIf { it > 0 }
    }

    /** Zero-padded to at least two digits: Part 01 .. Part 99, Part 100. */
    fun folderName(prefix: String, index: Int): String = "$prefix %02d".format(index)

    /**
     * Packs [files] into part folders of at most [capacityBytes].
     *
     * - SEQUENTIAL: effective date ascending, then natural name order; fill a
     *   folder until the next file does not fit. The last partially filled
     *   [existing] folder is topped up first.
     * - BEST_FIT: First-Fit-Decreasing (size descending into the first folder
     *   with room); produces the fewest folders. All existing folders are
     *   candidates in index order.
     * - Deterministic: same input, same plan.
     * - Zero-byte / unknown-size files count as 0 bytes and stay in the
     *   current folder.
     * - New folder numbering continues after the highest existing index.
     */
    fun pack(
        files: List<MediaFile>,
        capacityBytes: Long,
        order: CapacityOrder,
        prefix: String,
        existing: List<ExistingFolder> = emptyList(),
    ): PackResult {
        require(capacityBytes > 0) { "capacityBytes must be positive" }
        val cleanPrefix = sanitizePrefix(prefix)
        if (files.isEmpty()) return PackResult(emptyList(), emptyList())

        val oversized = files.filter { it.sizeBytes > capacityBytes }
        val fits = files.filter { it.sizeBytes <= capacityBytes }
        if (fits.isEmpty()) return PackResult(emptyList(), oversized.sortedWith(naturalSizeTiebreak()))

        val existingSorted = existing
            .filter { parseIndex(it.name, cleanPrefix) != null || it.index > 0 }
            .distinctBy { it.index }
            .sortedBy { it.index }
        val startIndex = (existingSorted.maxOfOrNull { it.index } ?: 0) + 1

        // Open folders: existing ones keep their name and usage; new ones get
        // appended as packing proceeds.
        val open = ArrayList<OpenFolder>(existingSorted.size + 8)
        for (ex in existingSorted) {
            open += OpenFolder(ex.name, ex.usedBytes, ArrayList())
        }

        when (order) {
            CapacityOrder.SEQUENTIAL -> {
                val sorted = fits.sortedWith(
                    compareBy({ it.effectiveDateMillis }, { compareNatural(it.displayName) }),
                )
                // Top up only the LAST partially filled folder, then continue
                // numbering after the highest existing number.
                val lastPartial = open.lastOrNull { it.usedBytes < capacityBytes }
                for (file in sorted) {
                    val target = lastPartial
                        ?.takeIf { it.usedBytes + file.sizeBytes <= capacityBytes }
                        ?: open.lastOrNull { it.isNew && it.usedBytes + file.sizeBytes <= capacityBytes }
                        ?: newFolder(open, cleanPrefix, startIndex).also {
                            open += it
                        }
                    target.add(file)
                }
            }
            CapacityOrder.BEST_FIT -> {
                val sorted = fits.sortedWith(
                    compareByDescending<MediaFile> { it.sizeBytes }.thenBy { compareNatural(it.displayName) },
                )
                for (file in sorted) {
                    val target = open.firstOrNull { it.usedBytes + file.sizeBytes <= capacityBytes }
                        ?: newFolder(open, cleanPrefix, startIndex).also { open += it }
                    target.add(file)
                }
            }
        }

        val folders = open
            // Existing folders that received nothing stay out of the plan: the
            // engine only emits folders that receive new files this run.
            .filter { it.received.isNotEmpty() }
            .map { PartFolder(it.name, it.received.toList(), it.usedBytes) }
        return PackResult(folders, oversized.sortedWith(naturalSizeTiebreak()))
    }

    private class OpenFolder(
        val name: String,
        var usedBytes: Long,
        val received: MutableList<MediaFile>,
    ) {
        val isNew: Boolean get() = received.isNotEmpty() || usedBytes == 0L
        fun add(file: MediaFile) {
            received += file
            usedBytes += file.sizeBytes
        }
    }

    private fun newFolder(open: List<OpenFolder>, prefix: String, startIndex: Int): OpenFolder {
        // Numbering continues after the highest existing/new index so repeated
        // runs never collide with a folder that already exists on disk.
        var index = startIndex
        val taken = open.mapTo(HashSet()) { it.name }
        var name = folderName(prefix, index)
        while (name in taken) {
            index++
            name = folderName(prefix, index)
        }
        return OpenFolder(name, 0L, ArrayList())
    }

    private fun naturalSizeTiebreak() = compareBy<MediaFile> { compareNatural(it.displayName) }

    /** Stable natural-order comparator key for a display name. */
    private fun compareNatural(name: String): NaturalKey = NaturalKey(name)

    private class NaturalKey(val s: String) : Comparable<NaturalKey> {
        private val chunks: List<Any> by lazy(LazyThreadSafetyMode.NONE) { chunk(s) }

        private fun chunk(s: String): List<Any> {
            val out = ArrayList<Any>()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c.isDigit()) {
                    var j = i
                    while (j < s.length && s[j].isDigit()) j++
                    // Strip leading zeros so 002 == 2; keep digits for ties.
                    val digits = s.substring(i, j)
                    val magnitude = digits.toLongOrNull() ?: Long.MAX_VALUE
                    out += magnitude
                    out += digits.length // "002" vs "2": shorter original wins deterministically
                    i = j
                } else {
                    var j = i
                    while (j < s.length && !s[j].isDigit()) j++
                    out += s.substring(i, j).lowercase()
                    i = j
                }
            }
            return out
        }

        @Suppress("UNCHECKED_CAST")
        override fun compareTo(other: NaturalKey): Int {
            val a = chunks
            val b = other.chunks
            val n = minOf(a.size, b.size)
            for (i in 0 until n) {
                val x = a[i]
                val y = b[i]
                val cmp = when {
                    x is Long && y is Long -> x.compareTo(y)
                    x is String && y is String -> x.compareTo(y)
                    // Long before String keeps the order total (digits run first).
                    x is Long -> -1
                    else -> 1
                }
                if (cmp != 0) return cmp
            }
            return a.size.compareTo(b.size)
        }
    }
}
