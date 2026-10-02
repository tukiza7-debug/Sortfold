package com.sortfold.app.error

import android.content.Context
import com.sortfold.app.SortfoldApp
import kotlinx.coroutines.runBlocking

/**
 * Saves an uncaught exception into the Error Library before the process dies,
 * then hands the crash to the system handler. The next launch shows a
 * "closed unexpectedly" banner that opens the Error Library.
 */
class CrashHandler(
    private val context: Context,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            runBlocking {
                val container = (context as SortfoldApp).container
                container.errorRepository.log(
                    module = "crash",
                    severity = ErrorRepository.Severity.CRASH,
                    type = throwable.javaClass.simpleName.ifEmpty { "Throwable" },
                    message = throwable.message ?: throwable.toString(),
                    stackTrace = throwable.stackTraceToString(),
                )
                container.settingsRepository.setCrashedLastRun(true)
            }
        } catch (_: Exception) {
            // Never let the reporter itself crash again.
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun install(context: Context) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is CrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext, current))
        }
    }
}
