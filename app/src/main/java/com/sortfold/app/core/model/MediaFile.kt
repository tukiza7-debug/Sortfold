package com.sortfold.app.core.model

/**
 * A file discovered by the scanner. Pure data: no Android types, so the rule
 * engine can be unit tested directly.
 */
data class MediaFile(
    val documentId: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mime: String?,
    val isDirectory: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val dateTakenMillis: Long? = null,
) {
    val type: MediaType get() = MediaType.fromMimeAndName(mime, displayName)
    val extension: String get() = displayName.substringAfterLast('.', "").lowercase()

    /** Best-effort date: real capture date first, file date as fallback. */
    val effectiveDateMillis: Long get() = dateTakenMillis ?: lastModifiedMillis
}

/** What will happen to one file when the plan is applied. */
enum class PlanAction { MOVE, REPLACE, RENAME, SKIP_DUPLICATE }

/** One row of the dry-run preview. */
data class PlanItem(
    val documentId: String,
    val displayName: String,
    val sizeBytes: Long,
    val sourceLabel: String,
    val destinationFolder: String,
    val destinationName: String,
    val action: PlanAction,
    val reason: String? = null,
)
