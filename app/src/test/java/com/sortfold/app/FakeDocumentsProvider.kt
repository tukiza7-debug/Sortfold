package com.sortfold.app

import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.nio.file.Files

/**
 * File-backed stand-in for the platform ExternalStorageProvider, rooted at a
 * temp directory. Document ids look like the real ones ("primary:Pictures/a.jpg")
 * and streams, sizes and dates come from real files, so scanner, mover and
 * undo run end-to-end on the JVM.
 */
class FakeDocumentsProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "com.android.externalstorage.documents"

        lateinit var root: File

        /** Test hook simulating a revoked tree grant. */
        @Volatile
        var failQueriesWithSecurityException: Boolean = false

        /** Test hook: the provider returns a null cursor (provider failure). */
        @Volatile
        var failQueriesWithNullCursor: Boolean = false

        /** Test hook: openDocument throws SecurityException (file locked/protected). */
        @Volatile
        var failOpensWithSecurityException: Boolean = false

        fun reset() {
            root = Files.createTempDirectory("sortfold-fake").toFile()
            failQueriesWithSecurityException = false
            failQueriesWithNullCursor = false
            failOpensWithSecurityException = false
        }

        fun fileFor(docId: String): File {
            val rel = docId.substringAfter(':')
            return if (rel.isEmpty()) root else File(root, rel)
        }

        fun docIdFor(file: File): String = "primary:" + file.relativeTo(root).infixSeparated()

        private fun File.infixSeparated(): String =
            toPath().joinToString("/") { it.toString() }

        fun childrenOf(parentDocId: String): List<File> =
            fileFor(parentDocId).listFiles()?.sortedBy { it.name } ?: emptyList()

        // Helpers used by tests to seed the tree.
        fun addFile(parentDocId: String, name: String, mime: String, content: ByteArray = ByteArray(10)): File {
            val f = File(fileFor(parentDocId), name)
            f.parentFile?.mkdirs()
            f.writeBytes(content)
            f.setLastModified(1_714_560_000_000)
            return f
        }

        fun addFolder(parentDocId: String, name: String): File {
            val f = File(fileFor(parentDocId), name)
            f.mkdirs()
            return f
        }

        fun install(contextProvider: () -> Unit) {
            val info = ProviderInfo().apply {
                authority = AUTHORITY
                exported = true
                grantUriPermissions = true
                readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
                writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
            }
            org.robolectric.android.controller.ContentProviderController
                .of(FakeDocumentsProvider())
                .create(info)
        }
    }

    override fun onCreate(): Boolean = true

    /** Mirrors ExternalStorageProvider: tree members are path-prefix descendants.
     *  A storage-root parent ("primary:") is a prefix directly, without a slash. */
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId == parentDocumentId ||
            documentId.startsWith("$parentDocumentId/") ||
            (parentDocumentId.endsWith(":") && documentId.startsWith(parentDocumentId))

    override fun queryRoots(projection: Array<out String>?) = MatrixCursor(
        projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID),
    ).apply {
        newRow().add(DocumentsContract.Root.COLUMN_ROOT_ID, "primary")
            .add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "primary")
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?) = MatrixCursor(
        projection ?: defaultProjection(),
    ).apply {
        val f = fileFor(documentId)
        if (!f.exists()) throw java.io.FileNotFoundException(documentId)
        newRow()
            .add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, f.name)
            .add(DocumentsContract.Document.COLUMN_MIME_TYPE, if (f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else mimeFor(f))
            .add(DocumentsContract.Document.COLUMN_SIZE, if (f.isDirectory) 0L else f.length())
            .add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, f.lastModified())
            .add(DocumentsContract.Document.COLUMN_FLAGS, 0)
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): android.database.Cursor? = if (failQueriesWithNullCursor) {
        null
    } else MatrixCursor(projection ?: defaultProjection()).apply {
        if (failQueriesWithSecurityException) throw SecurityException("Permission revoked: $parentDocumentId")
        if (!fileFor(parentDocumentId).exists()) throw java.io.FileNotFoundException(parentDocumentId)
        for (f in childrenOf(parentDocumentId)) {
            newRow()
                .add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docIdFor(f))
                .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, f.name)
                .add(DocumentsContract.Document.COLUMN_MIME_TYPE, if (f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else mimeFor(f))
                .add(DocumentsContract.Document.COLUMN_SIZE, if (f.isDirectory) 0L else f.length())
                .add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, f.lastModified())
                .add(DocumentsContract.Document.COLUMN_FLAGS, 0)
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (failOpensWithSecurityException) {
            throw SecurityException("Document locked: $documentId")
        }
        val f = fileFor(documentId)
        val p = ParcelFileDescriptor.MODE_READ_ONLY
        return when {
            mode == "r" -> ParcelFileDescriptor.open(f, p)
            else -> ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode("w"))
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        var name = displayName
        val existing = childrenOf(parentDocumentId).map { it.name }.toSet()
        if (mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
            var i = 1
            while (name in existing) {
                val dot = displayName.lastIndexOf('.')
                name = if (dot > 0) "${displayName.substring(0, dot)} ($i).${displayName.substring(dot + 1)}" else "$displayName ($i)"
                i++
            }
        }
        val f = File(fileFor(parentDocumentId), name)
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) f.mkdirs() else f.createNewFile()
        return docIdFor(f)
    }

    override fun deleteDocument(documentId: String) {
        fileFor(documentId).deleteRecursively()
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val f = fileFor(documentId)
        val target = File(f.parentFile, displayName)
        if (!f.renameTo(target)) throw java.lang.IllegalStateException("rename failed: $documentId -> $displayName")
        return docIdFor(target)
    }

    override fun getDocumentType(documentId: String): String {
        val f = fileFor(documentId)
        if (!f.exists()) throw java.io.FileNotFoundException(documentId)
        return if (f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else mimeFor(f)
    }

    private fun mimeFor(f: File): String = when (f.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun defaultProjection() = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS,
    )
}
