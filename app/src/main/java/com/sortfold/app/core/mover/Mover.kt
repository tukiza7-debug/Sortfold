package com.sortfold.app.core.mover

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SAF file mover. Every file is moved as: create destination -> stream copy ->
 * verify size -> delete source. If any step fails the partial destination is
 * removed, so a file is never left half-moved.
 */
class Mover(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    sealed interface Outcome {
        data class Moved(val destUri: Uri, val destName: String) : Outcome
        data object SkippedDuplicate : Outcome
        data class Failed(val detail: String) : Outcome
    }

    suspend fun ensureFolder(treeUri: Uri, parentDocId: String, segments: List<String>): String =
        withContext(Dispatchers.IO) {
            var current = parentDocId
            for (segment in segments) {
                current = childFolderDocId(treeUri, current, segment)
                    ?: createFolder(treeUri, current, segment)
            }
            current
        }

    private fun childFolderDocId(treeUri: Uri, parentDocId: String, name: String): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, parentDocId,
        )
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            android.os.Bundle(),
            null,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                if (c.getString(nameCol) == name && c.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return c.getString(idCol)
                }
            }
        }
        return null
    }

    private fun createFolder(treeUri: Uri, parentDocId: String, name: String): String {
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val created = DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw IllegalStateException("create-folder-failed:$name")
        return DocumentsContract.getDocumentId(created)
    }

    suspend fun moveOne(
        treeUri: Uri,
        sourceDocId: String,
        destFolderDocId: String,
        displayName: String,
        mime: String?,
        action: com.sortfold.app.core.model.PlanAction,
    ): Outcome = withContext(Dispatchers.IO) {
        val sourceUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, sourceDocId)
        val sourceSize = querySize(sourceUri)
        val existing = listNames(treeUri, destFolderDocId)
        val policy = when (action) {
            com.sortfold.app.core.model.PlanAction.SKIP_DUPLICATE -> return@withContext Outcome.SkippedDuplicate
            com.sortfold.app.core.model.PlanAction.RENAME ->
                com.sortfold.app.core.model.DuplicatePolicy.renameCandidate(existing, displayName)
            com.sortfold.app.core.model.PlanAction.REPLACE -> displayName
            com.sortfold.app.core.model.PlanAction.MOVE ->
                if (displayName in existing) return@withContext Outcome.SkippedDuplicate else displayName
        }

        // Replace: remove the old destination file first.
        if (action == com.sortfold.app.core.model.PlanAction.REPLACE && displayName in existing) {
            val oldDoc = childDocId(treeUri, destFolderDocId, displayName) ?: return@withContext Outcome.Failed("replace-target-missing")
            deleteDoc(treeUri, oldDoc)
        }

        val destUri = runCatching {
            DocumentsContract.createDocument(
                resolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, destFolderDocId),
                mime ?: "application/octet-stream",
                policy,
            )
        }.getOrNull() ?: return@withContext Outcome.Failed("create-file-failed")

        val copied = runCatching {
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.flush()
                } ?: throw IllegalStateException("output-stream-null")
            } ?: throw IllegalStateException("input-stream-null")
        }.isSuccess

        val sizeOk = copied && sourceSize >= 0 && querySize(destUri) == sourceSize
        if (!copied || !sizeOk) {
            runCatching { deleteDoc(treeUri, DocumentsContract.getDocumentId(destUri)) }
            return@withContext Outcome.Failed(if (copied) "size-mismatch" else "copy-failed")
        }

        // Same-name copy into the same folder would mean the source IS the
        // destination document; detect via document id equality.
        val destDocId = DocumentsContract.getDocumentId(destUri)
        if (destDocId == sourceDocId) {
            return@withContext Outcome.Moved(destUri, policy)
        }

        val deleted = runCatching { deleteDoc(treeUri, sourceDocId) }.getOrDefault(false)
        if (!deleted) {
            // Source locked: roll back the copy so nothing is duplicated.
            runCatching { deleteDoc(treeUri, destDocId) }
            return@withContext Outcome.Failed("source-delete-failed")
        }
        Outcome.Moved(destUri, policy)
    }

    private fun childDocId(treeUri: Uri, parentDocId: String, name: String): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), android.os.Bundle(), null)
            ?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (c.moveToNext()) {
                    if (c.getString(nameCol) == name) return c.getString(idCol)
                }
            }
        return null
    }

    fun listNames(treeUri: Uri, parentDocId: String): Set<String> {
        val out = HashSet<String>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), android.os.Bundle(), null)?.use { c ->
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (c.moveToNext()) out += c.getString(nameCol)
        }
        return out
    }

    private fun querySize(uri: Uri): Long = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), android.os.Bundle(), null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun deleteDoc(treeUri: Uri, docId: String): Boolean {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return DocumentsContract.deleteDocument(resolver, docUri)
    }

    /** Copies a file back to its original folder during undo. */
    suspend fun copyBack(
        treeUri: Uri,
        fromDocId: String,
        destFolderDocId: String,
        displayName: String,
        mime: String?,
    ): Outcome = withContext(Dispatchers.IO) {
        val fromUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fromDocId)
        val destUri = runCatching {
            DocumentsContract.createDocument(
                resolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, destFolderDocId),
                mime ?: "application/octet-stream",
                displayName,
            )
        }.getOrNull() ?: return@withContext Outcome.Failed("restore-create-failed")
        val ok = runCatching {
            resolver.openInputStream(fromUri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                    output.flush()
                } ?: throw IllegalStateException("output-stream-null")
            } ?: throw IllegalStateException("input-stream-null")
        }.isSuccess
        if (!ok) {
            runCatching { deleteDoc(treeUri, DocumentsContract.getDocumentId(destUri)) }
            Outcome.Failed("restore-copy-failed")
        } else {
            Outcome.Moved(destUri, displayName)
        }
    }

    companion object {
        /** Root document of a tree; every destination folder is created below it. */
        fun treeRootDocId(treeUri: Uri): String =
            DocumentsContract.getTreeDocumentId(treeUri)
    }
}
