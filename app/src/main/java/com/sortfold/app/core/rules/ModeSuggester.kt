package com.sortfold.app.core.rules

import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.MediaFile
import com.sortfold.app.core.model.MediaType
import com.sortfold.app.core.model.SortMode

/**
 * Heuristics that look at a scan and propose the most useful sort mode.
 * Returns ranked suggestions with a machine reason key the UI translates.
 */
object ModeSuggester {

    data class Suggestion(val mode: SortMode, val reasonKey: String)

    fun suggest(files: List<MediaFile>, treePath: String): List<Suggestion> {
        val media = files.filter { !it.isDirectory }
        if (media.isEmpty()) return listOf(Suggestion(SortMode.FILE_TYPE, "empty"))

        // 1.2.0: very large folders want a capacity split first — the user is
        // clearly fighting storage limits, not organizing by content.
        val totalBytes = media.sumOf { it.sizeBytes }
        if (totalBytes >= SizeThresholds.BIG_TOTAL_BYTES) {
            return listOf(Suggestion(SortMode.CAPACITY, "big-total"))
        }

        val images = media.count { it.type == MediaType.IMAGE }
        val videos = media.count { it.type == MediaType.VIDEO }
        val mixedTypes = images > 0 && videos > 0

        val withDates = media.mapNotNull { it.dateTakenMillis }.distinct()
        if (withDates.size >= 2) {
            val sorted = withDates.sorted()
            val spanDays = ((sorted.last() - sorted.first()) / 86_400_000L).coerceAtLeast(0)
            if (spanDays >= 60) {
                return listOf(Suggestion(SortMode.DATE_TAKEN, "dates-span"))
            }
        }

        val sourceApp = com.sortfold.app.core.model.SourceApp.fromPath(treePath)
        if (sourceApp != com.sortfold.app.core.model.SourceApp.OTHER) {
            return listOf(Suggestion(SortMode.SOURCE_APP, "known-source:${sourceApp.name.lowercase()}"))
        }

        val exts = media.map { it.extension }.filter { it.isNotEmpty() }.distinct()
        if (exts.size >= 4) {
            return listOf(Suggestion(SortMode.EXTENSION, "many-extensions"))
        }

        val sizes = media.map { it.sizeBytes }
        val largeCount = sizes.count { it >= SizeThresholds.LARGE_MIN }
        if (largeCount > 0 && largeCount < media.size) {
            return listOf(Suggestion(SortMode.SIZE, "mixed-sizes"))
        }

        val dimensions = media.filter { it.width != null && it.height != null }
        val portrait = dimensions.count { (it.height ?: 0) > (it.width ?: 0) }
        if (portrait in 1 until dimensions.size) {
            return listOf(Suggestion(SortMode.RESOLUTION, "mixed-orientation"))
        }

        if (mixedTypes) {
            return listOf(Suggestion(SortMode.FILE_TYPE, "mixed-media"))
        }
        return listOf(Suggestion(SortMode.EXTENSION, "uniform-type"))
    }
}

object SizeThresholds {
    const val LARGE_MIN: Long = 20L * 1024 * 1024 // 20 MB, informational threshold for suggestions

    /** 1.2.0: at this scan total the CAPACITY split is suggested (big-total). */
    const val BIG_TOTAL_BYTES: Long = 8_000_000_000L // 8 GB, decimal units
}
