package com.sortfold.app.error

/**
 * Masks personal folder names in messages, stack traces and exported reports.
 * Default output keeps the storage root and the file name:
 * "primary:DCIM/My Trip/IMG_1.jpg" becomes "primary:/.../IMG_1.jpg".
 * content:// URIs keep only scheme and authority, so a tree URI like
 * content://com.android.externalstorage.documents/tree/primary%3APictures
 * becomes content://com.android.externalstorage.documents/...
 */
object PathMasker {

    private val contentUriRegex = Regex("""content://[^\s"',;)\]]+""")
    private val storagePathRegex = Regex("""/storage/[^\s"',;)\]]+""")
    private val colonTreePathRegex = Regex("""\b(?:primary|external_primary|home|emulated):[^\s"',;)\]]+""")

    /** Masks every path-like token inside free text (messages, stack traces). */
    fun maskText(text: String, includeFullPaths: Boolean): String {
        if (includeFullPaths) return text
        return text
            .replace(contentUriRegex) { m -> maskUri(m.value) }
            .replace(storagePathRegex) { m -> maskSlash(m.value) }
            .replace(colonTreePathRegex) { m -> mask(m.value, includeFullPaths = false) }
    }

    /** content://authority/anything -> content://authority/... */
    fun maskUri(uri: String): String {
        val withoutScheme = uri.removePrefix("content://")
        val authority = withoutScheme.substringBefore('/')
        return "content://$authority/..."
    }

    /** "primary:DCIM/My Trip/IMG_1.jpg" -> "primary:/.../IMG_1.jpg" */
    fun mask(path: String, includeFullPaths: Boolean): String {
        if (includeFullPaths) return path
        val normalized = path.replace('\\', '/')
        val root = normalized.substringBefore(':', "").ifEmpty { return maskSlash(normalized) }
        val rest = normalized.substringAfter(':', "")
        val fileName = rest.substringAfterLast('/')
        return "$root:/.../$fileName"
    }

    private fun maskSlash(path: String): String {
        val fileName = path.substringAfterLast('/')
        return ".../$fileName"
    }
}
