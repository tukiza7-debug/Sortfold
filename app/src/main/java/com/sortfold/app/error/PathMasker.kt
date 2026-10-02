package com.sortfold.app.error

/**
 * Masks personal folder names in exported reports. Default output keeps the
 * storage root and the file name: "primary:DCIM/My Trip/IMG_1.jpg" becomes
 * "primary:/.../IMG_1.jpg".
 */
object PathMasker {

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
