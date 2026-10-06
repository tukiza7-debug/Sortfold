package com.sortfold.app.core.mover

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * SAF file mover.
 *
 * 1.2.0: when the provider supports it, files move with
 * [DocumentsContract.moveDocument] — instant and zero extra space (B-04).
 * Otherwise the verified copy+delete fallback runs with a 1 MiB buffer loop,
 * cancellation checks and byte progress (B-03). Either way the sequence is:
 * resolve collisions -> create/move -> verify -> delete source, so a file is
 * never left half-moved.
 */
class Mover(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    sealed interface Outcome {
        data class Moved(val destUri: Uri, val destName: String) : Outcome
        data object SkippedDuplicate : Outcome
        data class Failed(val detail: String) : Outcome
    }

    /** Byte-level progress of an in-file copy (B-03); suspending so workers can persist. */
    fun interface CopyProgress {
        suspend fun onBytes(copiedBytes: Long)
    }

    companion object {
        /** 1 MiB copy chunks: large enough for throughput, small enough for smooth cancel. */
        const val COPY_BUFFER_SIZE: Int = 1024 * 1024

        /** Root document of a tree; every destination folder is created below it. */
        fun treeRootDocId(treeUri: Uri): String =
            DocumentsContract.getTreeDocumentId(treeUri)

        /**
         * Parent document id of a child document id.
         * "primary:Pics/a.jpg" -> "primary:Pics"; "primary:top.jpg" -> "primary:"
         * (the storage root itself — a plain substringBeforeLast('/') would return
         * the file id and undo would try to create a child inside a file).
         */
        fun parentDocIdOf(docId: String): String {
            val slash = docId.lastIndexOf('/')
            if (slash <= 0) return docId.substringBefore(':') + ":"
            return docId.substring(0, slash)
        }
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

    /**
     * Resolves a folder path WITHOUT creating anything (auto-sort top-up and
     * B-01 pre-planning). Returns null when any segment is missing.
     */
    fun resolveFolder(treeUri: Uri, parentDocId: String, segments: List<String>): String? {
        var current: String? = parentDocId
        for (segment in segments) {
            current = current?.let { childFolderDocId(treeUri, it, segment) } ?: return null
        }
        return current
    }

    data class Entry(
        val name: String,
        val docId: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
    )

    /** Direct children of a folder (non-recursive), used by auto-sort top-up. */
    fun listEntries(treeUri: Uri, parentDocId: String): List<Entry> {
        val out = ArrayList<Entry>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            android.os.Bundle(),
            null,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                val isDir = c.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR
                out += Entry(name, c.getString(idCol), isDir, c.getLong(sizeCol))
            }
        }
        return out
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

    /**
     * Tree-level move support probe for the storage pre-check (B-05). The
     * per-file decision in [moveOne] re-checks the source document itself.
     */
    fun supportsMove(treeUri: Uri): Boolean {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return false
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        return runCatching {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), android.os.Bundle(), null)?.use { c ->
                if (c.moveToFirst()) {
                    (c.getLong(0) and DocumentsContract.Document.FLAG_SUPPORTS_MOVE.toLong()) != 0L
                } else false
            } ?: false
        }.getOrDefault(false)
    }

    private fun sourceSupportsMove(treeUri: Uri, sourceDocId: String): Boolean {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, sourceDocId)
        return runCatching {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), android.os.Bundle(), null)?.use { c ->
                if (c.moveToFirst()) {
                    (c.getLong(0) and DocumentsContract.Document.FLAG_SUPPORTS_MOVE.toLong()) != 0L
                } else false
            } ?: false
        }.getOrDefault(false)
    }

    /**
     * @param existingNames names already present in the destination folder.
     *   Callers keeping a per-folder cache (B-06) pass their up-to-date set;
     *   when null the folder is listed once here.
     */
    suspend fun moveOne(
        treeUri: Uri,
        sourceDocId: String,
        destFolderDocId: String,
        displayName: String,
        mime: String?,
        action: com.sortfold.app.core.model.PlanAction,
        existingNames: Set<String>? = null,
        progress: CopyProgress? = null,
    ): Outcome = withContext(Dispatchers.IO) {
        val sourceUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, sourceDocId)
        val sourceSize = querySize(sourceUri)
        // No-op guard: a file already living in its destination folder must not
        // be copied onto itself (providers would answer with "photo (1).jpg").
        if (parentDocIdOf(sourceDocId) == destFolderDocId) {
            return@withContext Outcome.SkippedDuplicate
        }
        val existing = existingNames ?: listNames(treeUri, destFolderDocId)
        val policy = when (action) {
            com.sortfold.app.core.model.PlanAction.SKIP_DUPLICATE -> return@withContext Outcome.SkippedDuplicate
            com.sortfold.app.core.model.PlanAction.RENAME ->
                com.sortfold.app.core.model.DuplicatePolicy.renameCandidate(existing, displayName)
            com.sortfold.app.core.model.PlanAction.REPLACE -> displayName
            com.sortfold.app.core.model.PlanAction.MOVE ->
                if (displayName in existing) return@withContext Outcome.SkippedDuplicate else displayName
        }
        val replacing = action == com.sortfold.app.core.model.PlanAction.REPLACE && displayName in existing

        // B-04: same-tree moveDocument is instant and needs no extra space.
        // It is only safe when the destination name is free — a REPLACE over an
        // existing name goes through the verified copy+delete path so the old
        // file is destroyed strictly after the new copy is intact.
        if (!replacing && sourceSupportsMove(treeUri, sourceDocId)) {
            val moved = runCatching {
                moveDocument(
                    treeUri, sourceDocId, destFolderDocId, policy, mime,
                )
            }.getOrNull()
            if (moved != null) {
                return@withContext Outcome.Moved(moved.first, moved.second)
            }
            // Unsupported or thrown: fall through to the copy path below.
        }

        // Copy FIRST, destroy second: the existing destination (REPLACE) and the
        // source are only removed once the new copy is verified, so a failed
        // copy can never lose data.
        val destUri = runCatching {
            DocumentsContract.createDocument(
                resolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, destFolderDocId),
                mime ?: "application/octet-stream",
                policy,
            )
        }.getOrNull() ?: return@withContext Outcome.Failed("create-file-failed")

        // B-08: some providers silently rename ("name (1).jpg") or would store
        // a wrong type; claim the requested name back immediately — but never
        // by overwriting a name that is already taken (REPLACE renames strictly
        // after the old file is destroyed, below).
        var targetUri = destUri
        if (queryDisplayName(targetUri) != policy && policy !in existing) {
            val renamed = runCatching { DocumentsContract.renameDocument(resolver, targetUri, policy) }.getOrNull()
            if (renamed != null) targetUri = renamed
        }

        val copyResult = try {
            copyStream(sourceUri, targetUri, progress)
            kotlin.Result.success(Unit)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // B-03: cancelled mid-file — the partial destination must go, and
            // the cancellation itself is never swallowed.
            withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { deleteDoc(treeUri, DocumentsContract.getDocumentId(targetUri)) }
            }
            throw ce
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
        val copied = copyResult.isSuccess
        val sizeOk = copied && sourceSize >= 0 && querySize(targetUri) == sourceSize
        if (!copied || !sizeOk) {
            runCatching { deleteDoc(treeUri, DocumentsContract.getDocumentId(targetUri)) }
            val detail = when {
                copyResult.exceptionOrNull() is SecurityException -> "permission-denied"
                copied -> "size-mismatch"
                else -> "copy-failed"
            }
            return@withContext Outcome.Failed(detail)
        }

        // Same-document copy: nothing further to delete.
        val destDocId = DocumentsContract.getDocumentId(targetUri)
        if (destDocId == sourceDocId) {
            return@withContext Outcome.Moved(targetUri, policy)
        }

        // REPLACE: now that the copy is verified, remove the old destination.
        var finalUri = targetUri
        if (replacing) {
            val oldDoc = childDocId(treeUri, destFolderDocId, displayName)
            if (oldDoc != null && oldDoc != destDocId) {
                runCatching { deleteDoc(treeUri, oldDoc) }
            }
            // The provider may have auto-renamed our copy ("photo (1).jpg")
            // because the old file still existed; claim the real name back.
            if (queryDisplayName(finalUri) != displayName) {
                runCatching {
                    DocumentsContract.renameDocument(resolver, finalUri, displayName)
                        ?.let { finalUri = it }
                }
            }
        }
        val finalName = queryDisplayName(finalUri) ?: policy

        val deleted = runCatching { deleteDoc(treeUri, sourceDocId) }.getOrDefault(false)
        if (!deleted) {
            // Source locked: roll back the copy so nothing is duplicated.
            runCatching { deleteDoc(treeUri, DocumentsContract.getDocumentId(finalUri)) }
            return@withContext Outcome.Failed("source-delete-failed")
        }
        Outcome.Moved(finalUri, finalName)
    }

    private suspend fun moveDocument(
        treeUri: Uri,
        sourceDocId: String,
        destFolderDocId: String,
        displayName: String,
        mime: String?,
    ): Pair<Uri, String> {
        val sourceUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, sourceDocId)
        val sourceParentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, parentDocIdOf(sourceDocId),
        )
        val targetParentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, destFolderDocId)
        val moved = DocumentsContract.moveDocument(
            resolver, sourceUri, sourceParentUri, targetParentUri,
        ) ?: throw IllegalStateException("move-document-null")
        // A provider may still auto-rename on a hidden collision; claim the
        // requested name back so the plan stays truthful.
        var finalUri = moved
        if (queryDisplayName(finalUri) != displayName) {
            runCatching {
                DocumentsContract.renameDocument(resolver, finalUri, displayName)?.let { finalUri = it }
            }
        }
        val finalName = queryDisplayName(finalUri) ?: displayName
        return finalUri to finalName
    }

    /**
     * 1 MiB chunked copy (B-03): cooperative cancellation inside the file,
     * byte progress to [progress]; on cancellation the partial destination is
     * deleted by the caller in a NonCancellable block — here we only stop.
     */
    private suspend fun copyStream(from: Uri, to: Uri, progress: CopyProgress?) {
        val input = resolver.openInputStream(from) ?: throw IllegalStateException("input-stream-null")
        val output = resolver.openOutputStream(to) ?: run {
            input.close()
            throw IllegalStateException("output-stream-null")
        }
        input.use { input2 ->
            output.use { output2 ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input2.read(buffer)
                    if (read == -1) break
                    output2.write(buffer, 0, read)
                    total += read
                    progress?.onBytes(total)
                }
                output2.flush()
            }
        }
    }

    /**
     * Deletes a partially copied destination after a mid-file cancellation.
     * Must be called from a NonCancellable context by the worker (B-03).
     */
    fun deletePartial(treeUri: Uri, destUri: Uri) {
        runCatching { deleteDoc(treeUri, DocumentsContract.getDocumentId(destUri)) }
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

    /** Child of a folder by name; null when absent. */
    fun findChild(treeUri: Uri, parentDocId: String, name: String): Entry? =
        listEntries(treeUri, parentDocId).firstOrNull { it.name == name }

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

    /** Public size probe used by the worker's IN_FLIGHT reconciliation (B-11). -1 = missing. */
    fun documentSize(treeUri: Uri, docId: String): Long = querySize(
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
    )

    /** Public delete used by the worker's reconciliation and retry paths. */
    fun deleteDocument(treeUri: Uri, docId: String): Boolean = runCatching {
        deleteDoc(treeUri, docId)
    }.getOrDefault(false)

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), android.os.Bundle(), null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun deleteDoc(treeUri: Uri, docId: String): Boolean {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return DocumentsContract.deleteDocument(resolver, docUri)
    }

    /**
     * Restores a file to its original folder during undo (B-10):
     * moveDocument first, then verified copy+delete with size check, explicit
     * collision handling and post-undo empty-folder cleanup.
     */
    suspend fun copyBack(
        treeUri: Uri,
        fromDocId: String,
        destFolderDocId: String,
        displayName: String,
        mime: String?,
        existingNames: Set<String>? = null,
        progress: CopyProgress? = null,
    ): Outcome = withContext(Dispatchers.IO) {
        val fromUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fromDocId)
        val existing = existingNames ?: listNames(treeUri, destFolderDocId)
        val sourceSize = querySize(fromUri)

        // Prefer moving the document back: instant, no extra space, no name
        // games — unless the original name is already taken there.
        if (displayName !in existing && sourceSupportsMove(treeUri, fromDocId)) {
            val moved = runCatching {
                moveDocument(treeUri, fromDocId, destFolderDocId, displayName, mime)
            }.getOrNull()
            if (moved != null) {
                cleanupEmptyFolders(treeUri, parentDocIdOf(fromDocId))
                return@withContext Outcome.Moved(moved.first, moved.second)
            }
        }

        // Collision policy for undo: never silently stack "name (1).jpg".
        // Restore under a numbered name and say so in the log.
        val targetName = if (displayName in existing) {
            com.sortfold.app.core.model.DuplicatePolicy.renameCandidate(existing, displayName)
        } else displayName

        val destUri = runCatching {
            DocumentsContract.createDocument(
                resolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, destFolderDocId),
                mime ?: "application/octet-stream",
                targetName,
            )
        }.getOrNull() ?: return@withContext Outcome.Failed("restore-create-failed")
        val ok = try {
            copyStream(fromUri, destUri, progress)
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { deleteDoc(treeUri, DocumentsContract.getDocumentId(destUri)) }
            }
            throw ce
        } catch (_: Exception) {
            false
        }
        val sizeOk = ok && sourceSize >= 0 && querySize(destUri) == sourceSize
        if (!ok || !sizeOk) {
            runCatching { deleteDoc(treeUri, DocumentsContract.getDocumentId(destUri)) }
            return@withContext Outcome.Failed(if (ok) "restore-size-mismatch" else "restore-copy-failed")
        }
        val finalName = queryDisplayName(destUri) ?: targetName
        // The sorted copy goes away only after the restore is verified.
        runCatching { deleteDoc(treeUri, fromDocId) }
        cleanupEmptyFolders(treeUri, parentDocIdOf(fromDocId))
        Outcome.Moved(destUri, finalName)
    }

    /**
     * B-10: after a successful undo, folders the sort created (part folders,
     * Oversized, bucket folders) are removed when they end up empty, so no
     * litter stays behind. Walks upwards but never deletes the tree root.
     * Best-effort by design: a concurrent delete or a missing folder must
     * never fail the undo itself.
     */
    private fun cleanupEmptyFolders(treeUri: Uri, folderDocId: String) {
        runCatching {
            var current: String? = folderDocId
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            var guard = 0
            while (current != null && current != rootDocId && guard < 16) {
                guard++
                val children = listEntries(treeUri, current)
                if (children.isNotEmpty()) return
                val parent = parentDocIdOf(current)
                val deleted = deleteDoc(treeUri, current)
                if (!deleted) return
                current = parent
            }
        }
    }
}
