package com.sortfold.app.core.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.sortfold.app.core.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans a SAF document tree one level deep (non-recursive). Recursion is a
 * deliberate trade-off: cross-folder recursion surprises users and makes undo
 * harder to reason about. Sub-folder summary counts are surfaced instead.
 */
class MediaScanner(private val context: Context) {

    suspend fun scan(treeUri: Uri, onProgress: (done: Int) -> Unit = {}): ScanResult =
        withContext(Dispatchers.IO) { scanInternal(treeUri, onProgress) }

    private fun scanInternal(treeUri: Uri, onProgress: (Int) -> Unit): ScanResult {
        val resolver = context.contentResolver
        // A tree URI must be resolved with getTreeDocumentId; getDocumentId
        // would throw IllegalArgumentException on exactly this input.
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, rootDocId,
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val files = ArrayList<MediaFile>(256)
        val folders = ArrayList<String>()
        val cursor = try {
            resolver.query(childrenUri, projection, android.os.Bundle(), null)
        } catch (e: SecurityException) {
            throw ScanFailedException(ScanFailedException.Reason.PERMISSION_REVOKED, e)
        } catch (e: IllegalArgumentException) {
            throw ScanFailedException(ScanFailedException.Reason.PROVIDER_ERROR, e)
        }
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                val mime = c.getString(mimeCol)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                if (isDir) {
                    folders += name
                    continue
                }
                files += MediaFile(
                    documentId = c.getString(idCol),
                    displayName = name,
                    sizeBytes = c.getLong(sizeCol),
                    lastModifiedMillis = c.getLong(modCol),
                    mime = mime,
                )
                if (files.size % 64 == 0) onProgress(files.size)
            }
        }
        onProgress(files.size)
        return ScanResult(treeUri, files, folders)
    }

    data class ScanResult(
        val treeUri: Uri,
        val files: List<MediaFile>,
        val subFolders: List<String>,
    ) {
        val totalBytes: Long get() = files.sumOf { it.sizeBytes }
    }
}
