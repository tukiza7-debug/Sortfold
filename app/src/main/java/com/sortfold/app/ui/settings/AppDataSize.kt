package com.sortfold.app.ui.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Computes how much space the app's data occupies. Must run off the main
 * thread: walking the cache and files directories blocked the UI whenever a
 * job had touched thousands of files (BUG-24).
 */
object AppDataSize {

    suspend fun compute(context: Context): Long = withContext(Dispatchers.IO) {
        computeSync(context)
    }

    fun computeSync(context: Context): Long {
        var total = 0L
        context.cacheDir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        context.filesDir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }
}
