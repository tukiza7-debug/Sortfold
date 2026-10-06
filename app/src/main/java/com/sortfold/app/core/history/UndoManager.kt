package com.sortfold.app.core.history

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.data.db.MoveLogEntity
import com.sortfold.app.data.db.SortfoldDatabase

/**
 * One-tap undo of a sort operation. Walks the move log backwards and restores
 * every moved file to its original folder, then removes the sorted copy.
 *
 * 1.2.0 (B-10): copyBack verifies sizes, resolves collisions explicitly and
 * removes the sorted copy plus any emptied folders itself, so this manager
 * only records outcomes.
 */
class UndoManager(private val context: Context, private val db: SortfoldDatabase) {

    data class UndoResult(val restored: Int, val failed: Int, val stoppedAtFailure: Boolean)

    suspend fun undoJob(jobId: Long): UndoResult {
        val job = db.sortJobDao().byId(jobId) ?: return UndoResult(0, 0, false)
        // UNDONE is final; UNDOING is recoverable — a process death mid-undo used
        // to leave the job stuck in that state with no way to retry (BUG-11).
        if (job.status == "UNDONE") return UndoResult(0, 0, false)
        db.sortJobDao().updateProgress(jobId, "UNDOING", job.doneFiles, job.doneBytes, System.currentTimeMillis(), null)

        val mover = Mover(context)
        val treeUri = Uri.parse(job.treeUri)
        val moved = db.moveLogDao().byJob(jobId).filter { it.status == "MOVED" }
        var restored = 0
        var failed = 0
        var stopped = false

        // B-06-style cache: the original folder's names, loaded once per folder.
        val nameCache = HashMap<String, MutableSet<String>>()

        for (row in moved.sortedByDescending { it.seq }) {
            val destUriString = row.destDocId
            if (destUriString == null) {
                failed++
                continue
            }
            // destDocId stores the full destination document URI string.
            val destUri = Uri.parse(destUriString)
            val docId = runCatching { DocumentsContract.getDocumentId(destUri) }.getOrNull()
            if (docId == null) {
                failed++
                continue
            }
            // Parent of the ORIGINAL location; Mover.parentDocIdOf handles
            // root-level files ("primary:top.jpg" -> "primary:") correctly.
            val originalParentDocId = Mover.parentDocIdOf(row.sourceDocId)
            val names = nameCache.getOrPut(originalParentDocId) {
                runCatching { mover.listNames(treeUri, originalParentDocId) }.getOrElse { emptySet() }.toMutableSet()
            }
            val outcome = try {
                mover.copyBack(treeUri, docId, originalParentDocId, row.displayName, row.mime, existingNames = names)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                Mover.Outcome.Failed(e.message ?: "restore-error")
            }
            when (outcome) {
                is Mover.Outcome.Moved -> {
                    restored++
                    names += outcome.destName
                    db.moveLogDao().updateStatus(row.id, "UNDONE", null, "restored")
                }
                is Mover.Outcome.SkippedDuplicate -> {
                    restored++
                    db.moveLogDao().updateStatus(row.id, "UNDONE", null, "already-present")
                }
                is Mover.Outcome.Failed -> {
                    failed++
                    db.moveLogDao().updateStatus(row.id, "MOVED", row.destDocId, outcome.detail)
                    stopped = true
                    break
                }
            }
        }

        val finalStatus = if (failed == 0 && restored == moved.size) "UNDONE" else "PARTIAL"
        val doneFiles = (job.doneFiles - restored).coerceAtLeast(0)
        db.sortJobDao().updateProgress(jobId, finalStatus, doneFiles, job.doneBytes, System.currentTimeMillis(), null)
        return UndoResult(restored, failed, stopped)
    }
}
