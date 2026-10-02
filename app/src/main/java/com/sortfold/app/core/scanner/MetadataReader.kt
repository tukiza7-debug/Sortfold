package com.sortfold.app.core.scanner

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/**
 * Reads media metadata (dimensions, capture date) through content streams.
 * Works on SAF URIs without any storage permission because the caller already
 * holds a tree grant.
 */
object MetadataReader {

    private val NAME_DATE: Pattern = Pattern.compile("(20\\d{2})[-_.]?(\\d{2})[-_.]?(\\d{2})")

    suspend fun enrich(
        resolver: ContentResolver,
        files: List<com.sortfold.app.core.model.MediaFile>,
        treeUri: Uri,
        needDimensions: Boolean,
        needDates: Boolean,
        onProgress: (Int) -> Unit = {},
    ): List<com.sortfold.app.core.model.MediaFile> = withContext(Dispatchers.IO) {
        if (!needDimensions && !needDates) {
            onProgress(files.size)
            return@withContext files
        }
        var done = 0
        val enriched = files.map { file ->
            var out = file
            val uri = childUri(treeUri, file.documentId)
            try {
                if (needDimensions && (file.width == null || file.height == null)) {
                    val dim = dimensions(resolver, uri, file.mime)
                    if (dim != null) out = out.copy(width = dim.first, height = dim.second)
                }
                if (needDates && file.dateTakenMillis == null) {
                    val fromName = dateFromName(file.displayName)
                    val real = fromName ?: dateTaken(resolver, uri, file.mime)
                    if (real != null) out = out.copy(dateTakenMillis = real)
                }
            } catch (_: Exception) {
                // Metadata is best-effort; rules fall back to file dates and SD bucket.
            }
            done++
            if (done % 32 == 0) onProgress(done)
            out
        }
        // Always report the final count, otherwise progress sticks at the last
        // multiple of 32 for file counts that are not a multiple of 32.
        onProgress(done)
        enriched
    }

    fun dateFromName(name: String): Long? {
        val m = NAME_DATE.matcher(name)
        if (!m.find()) return null
        val year = m.group(1)!!.toInt()
        val month = m.group(2)!!.toInt()
        val day = m.group(3)!!.toInt()
        if (month !in 1..12 || day !in 1..31) return null
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }
        return cal.timeInMillis
    }

    private fun dimensions(resolver: ContentResolver, uri: Uri, mime: String?): Pair<Int, Int>? =
        when {
            mime?.startsWith("image/") == true -> imageDimensions(resolver, uri)
            mime?.startsWith("video/") == true -> videoDimensions(resolver, uri)
            else -> imageDimensions(resolver, uri) ?: videoDimensions(resolver, uri)
        }

    private fun imageDimensions(resolver: ContentResolver, uri: Uri): Pair<Int, Int>? = try {
        resolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
        }
    } catch (_: Exception) {
        null
    }

    private fun videoDimensions(resolver: ContentResolver, uri: Uri): Pair<Int, Int>? {
        val retriever = MediaMetadataRetriever()
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            } ?: return null
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (w != null && h != null) w to h else null
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun dateTaken(resolver: ContentResolver, uri: Uri, mime: String?): Long? = try {
        when {
            mime?.startsWith("video/") == true -> videoDate(resolver, uri)
            else -> imageDate(resolver, uri) ?: videoDate(resolver, uri)
        }
    } catch (_: Exception) {
        null
    }

    private fun imageDate(resolver: ContentResolver, uri: Uri): Long? = try {
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                ?: return@use null
            parseExifDate(raw)
        }
    } catch (_: Exception) {
        null
    }

    private fun videoDate(resolver: ContentResolver, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            } ?: return null
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) ?: return null
            val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            fmt.parse(raw)?.time
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    fun parseExifDate(raw: String): Long? = try {
        val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        fmt.parse(raw)?.time
    } catch (_: Exception) {
        null
    }

    fun childUri(treeUri: Uri, documentId: String): Uri =
        android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
}
