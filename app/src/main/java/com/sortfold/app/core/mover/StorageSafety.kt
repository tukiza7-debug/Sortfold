package com.sortfold.app.core.mover

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract

/** Safety checks and storage estimates for a planned sort. */
object StorageSafety {

    enum class TreeRisk { NONE, STORAGE_ROOT, APP_PRIVATE, SYSTEM }

    /**
     * Classifies a picked tree so the UI can warn (storage root) or block
     * (app-private, system) destinations. Tree ids look like "primary:DCIM".
     */
    fun classifyTree(treeUri: Uri): TreeRisk {
        val id = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return TreeRisk.SYSTEM
        return classifyTreeId(id)
    }

    /** Pure string core of [classifyTree]; unit-testable without Android. */
    fun classifyTreeId(treeDocumentId: String): TreeRisk {
        val id = treeDocumentId.lowercase()
        val path = id.substringAfter(':', "")
        if (path == "android" || path.startsWith("android/data") ||
            path.startsWith("android/obb") || path.startsWith("android/")) {
            return TreeRisk.APP_PRIVATE
        }
        if (path.startsWith("system") || path.startsWith("proc") || path.startsWith("sys")) return TreeRisk.SYSTEM
        // A system/proc/sys volume itself (e.g. tree id "system:etc").
        if (id.startsWith("system:") || id.startsWith("proc:") || id.startsWith("sys:")) return TreeRisk.SYSTEM
        if (id.endsWith(":") || path.isEmpty()) return TreeRisk.STORAGE_ROOT
        return TreeRisk.NONE
    }

    fun isSameTree(a: Uri, b: Uri): Boolean = runCatching {
        DocumentsContract.getTreeDocumentId(a) == DocumentsContract.getTreeDocumentId(b)
    }.getOrDefault(a == b)

    /** Free bytes on primary internal storage; used for the low-storage block. */
    fun availableBytes(context: Context): Long = try {
        val dataDir = Environment.getDataDirectory()
        StatFs(dataDir.path).availableBytes
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    /** True when applying the plan would risk filling the disk. */
    fun isLowStorage(context: Context, planBytes: Long): Boolean =
        availableBytes(context) < planBytes * 2 + 100L * 1024 * 1024

    /** Hard block: destination needs more than what is free. */
    fun isInsufficientStorage(context: Context, planBytes: Long): Boolean =
        availableBytes(context) < planBytes + 50L * 1024 * 1024
}
