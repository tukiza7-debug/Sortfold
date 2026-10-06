package com.sortfold.app.core.mover

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract

/** Safety checks and storage estimates for a planned sort. */
object StorageSafety {

    enum class TreeRisk { NONE, STORAGE_ROOT, APP_PRIVATE, SYSTEM }

    /**
     * Storage verdict for a planned sort (B-05). Moves inside one tree are
     * done one file at a time — or with moveDocument for zero extra space —
     * so the real peak need is the LARGEST SINGLE FILE, never the plan total.
     */
    enum class SpaceVerdict {
        /** Plenty of room (or moveDocument makes extra space unnecessary). */
        OK,
        /** Non-blocking warning. */
        LOW,
        /** Hard block: the largest file would not fit. */
        INSUFFICIENT,
        /** Volume could not be determined: warn instead of block. */
        UNKNOWN,
    }

    /** Classic block threshold: keep 50 MB of headroom. */
    private const val HEADROOM_BYTES: Long = 50L * 1024 * 1024

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

    /** Free bytes on primary internal storage. */
    fun availableBytes(context: Context): Long = try {
        val dataDir = Environment.getDataDirectory()
        StatFs(dataDir.path).availableBytes
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    /**
     * Free bytes on the volume that actually hosts the picked tree, or null
     * when the volume cannot be resolved (B-05: SD-card / USB trees used to be
     * checked against the wrong, internal volume). `StorageVolume.getDirectory`
     * needs API 30; on 29 the volume simply cannot be resolved and the caller
     * degrades to the warn-only verdict.
     */
    fun freeBytesForTree(context: Context, treeUri: Uri): Long? = runCatching {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val volumeId = treeDocId.substringBefore(':', "primary")
        if (volumeId.equals("primary", ignoreCase = true)) {
            StatFs(Environment.getExternalStorageDirectory().path).availableBytes
        } else if (android.os.Build.VERSION.SDK_INT >= 30) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volume = sm.storageVolumes.firstOrNull { v ->
                runCatching { v.uuid?.equals(volumeId, ignoreCase = true) == true }.getOrDefault(false)
            }
            val dir = volume?.directory ?: return@runCatching null
            StatFs(dir.path).availableBytes
        } else {
            null
        }
    }.getOrNull()

    /**
     * Volume-aware pre-check for a plan.
     *
     * - [moveSupported] (moveDocument available for the tree): extra space is
     *   never needed, so the block is skipped entirely.
     * - Otherwise the largest file plus headroom must fit on the tree's own
     *   volume; a smaller margin triggers the non-blocking LOW warning.
     * - An undeterminable volume warns (UNKNOWN) instead of blocking.
     */
    fun evaluate(
        context: Context,
        treeUri: Uri,
        largestFileBytes: Long,
        moveSupported: Boolean,
    ): SpaceVerdict {
        if (moveSupported) return SpaceVerdict.OK
        val free = freeBytesForTree(context, treeUri) ?: return SpaceVerdict.UNKNOWN
        val need = (largestFileBytes.coerceAtLeast(0L)) + HEADROOM_BYTES
        return when {
            free < need -> SpaceVerdict.INSUFFICIENT
            free < need * 2 + 100L * 1024 * 1024 -> SpaceVerdict.LOW
            else -> SpaceVerdict.OK
        }
    }

    /** Legacy checks kept for callers without a tree context. */
    fun isLowStorage(context: Context, planBytes: Long): Boolean =
        availableBytes(context) < planBytes * 2 + 100L * 1024 * 1024

    /** Hard block: destination needs more than what is free. */
    fun isInsufficientStorage(context: Context, planBytes: Long): Boolean =
        availableBytes(context) < planBytes + HEADROOM_BYTES
}
