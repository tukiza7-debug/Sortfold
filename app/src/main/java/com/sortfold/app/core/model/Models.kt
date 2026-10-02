package com.sortfold.app.core.model

/** What kind of content a scanned file is. */
enum class MediaType {
    IMAGE, VIDEO, OTHER;

    companion object {
        fun fromMimeAndName(mime: String?, name: String): MediaType {
            val m = mime?.lowercase().orEmpty()
            return when {
                m.startsWith("image/") -> IMAGE
                m.startsWith("video/") -> VIDEO
                else -> fromExtension(name.substringAfterLast('.', "").lowercase())
            }
        }

        private fun fromExtension(ext: String): MediaType = when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif" -> IMAGE
            "mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts", "mpg", "mpeg" -> VIDEO
            else -> OTHER
        }
    }
}

/** The seven supported sort modes, in canonical nesting priority. */
enum class SortMode(val priority: Int) {
    FILE_TYPE(0),
    DATE_TAKEN(1),
    SOURCE_APP(2),
    RESOLUTION(3),
    SIZE(4),
    EXTENSION(5),
    NAME_PATTERN(6);

    companion object {
        /** Selected modes applied in canonical order, regardless of how the user picked them. */
        fun ordered(selected: Collection<SortMode>): List<SortMode> =
            selected.sortedBy { it.priority }
    }
}

/** Coarse size buckets for the SIZE sort mode. */
enum class SizeBucket(val label: String) {
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large");

    companion object {
        const val SMALL_MAX_BYTES: Long = 1L * 1024 * 1024        // < 1 MB
        const val MEDIUM_MAX_BYTES: Long = 50L * 1024 * 1024      // 1 MB .. 50 MB

        fun of(sizeBytes: Long): SizeBucket = when {
            sizeBytes < SMALL_MAX_BYTES -> SMALL
            sizeBytes < MEDIUM_MAX_BYTES -> MEDIUM
            else -> LARGE
        }
    }
}

/** Resolution / orientation buckets for the RESOLUTION sort mode. */
enum class ResolutionClass(val label: String) {
    PORTRAIT("Portrait"),
    SD("SD"),
    HD("HD"),
    UHD_4K("4K");

    companion object {
        const val HD_SHORT_SIDE: Int = 720
        const val UHD_SHORT_SIDE: Int = 2160

        /**
         * Orientation wins for portrait media; otherwise quality buckets apply.
         * Square media falls into the quality buckets.
         */
        fun of(width: Int?, height: Int?): ResolutionClass {
            val w = width ?: return SD
            val h = height ?: return SD
            if (w <= 0 || h <= 0) return SD
            if (h > w) return PORTRAIT
            val shortSide = minOf(w, h)
            return when {
                shortSide >= UHD_SHORT_SIDE || maxOf(w, h) >= 3840 -> UHD_4K
                shortSide >= HD_SHORT_SIDE -> HD
                else -> SD
            }
        }
    }
}

/** Source app guesses derived from well-known folder layout. */
enum class SourceApp(val label: String) {
    CAMERA("Camera"),
    SCREENSHOTS("Screenshots"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    DOWNLOADS("Downloads"),
    OTHER("Other");

    companion object {
        fun fromPath(path: String): SourceApp {
            val p = path.lowercase().replace('\\', '/')
            return when {
                p.contains("screenshot") -> SCREENSHOTS
                p.contains("whatsapp") || p.contains("com.whatsapp") -> WHATSAPP
                p.contains("telegram") || p.contains("org.telegram") || p.contains("org.telegram.messenger") -> TELEGRAM
                p.contains("download") -> DOWNLOADS
                p.contains("dcim") || p.contains("camera") -> CAMERA
                else -> OTHER
            }
        }
    }
}

/** User choice for destination files that already exist. */
enum class DuplicatePolicy {
    SKIP, RENAME, REPLACE;

    companion object {
        /** Finds the first free "name (n).ext" variant, n >= 2. */
        fun renameCandidate(existing: Set<String>, desired: String): String {
            if (desired !in existing) return desired
            val dot = desired.lastIndexOf('.')
            val base = if (dot > 0) desired.substring(0, dot) else desired
            val ext = if (dot > 0) desired.substring(dot) else ""
            var n = 2
            while (true) {
                val candidate = "$base ($n)$ext"
                if (candidate !in existing) return candidate
                n++
            }
        }
    }
}

/** One user-defined name rule for the NAME_PATTERN sort mode. */
enum class NamePatternType { PREFIX, SUFFIX, CONTAINS }

data class NameRule(
    val patternType: NamePatternType,
    val value: String,
    val targetFolder: String,
) {
    fun matches(fileName: String): Boolean {
        val name = fileName.lowercase()
        val v = value.trim().lowercase()
        if (v.isEmpty()) return false
        return when (patternType) {
            NamePatternType.PREFIX -> name.startsWith(v)
            NamePatternType.SUFFIX -> name.endsWith(v)
            NamePatternType.CONTAINS -> name.contains(v)
        }
    }
}

/** Date granularity for the DATE_TAKEN sort mode. */
enum class DateGranularity { YEAR, MONTH }
