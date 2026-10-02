package com.sortfold.app.error

import android.content.Context
import com.sortfold.app.data.db.ErrorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the Error Library export: a zip with a readable .txt report, the
 * structured .json data and a moves.csv covering the same period.
 */
class ErrorExporter(private val context: Context) {

    @Serializable
    data class ErrorJson(
        val timestamp: Long,
        val module: String,
        val severity: String,
        val type: String,
        val message: String,
        val stackTrace: String?,
        val jobId: Long?,
        val appVersion: String,
        val androidVersion: String,
        val deviceModel: String,
        val versionCode: Long = 0,
        val buildId: String = "",
    )

    data class ExportResult(val file: File, val count: Int)

    companion object {
        /** RFC-4180 field escaping: quotes doubled, field wrapped when needed. */
        fun csvField(raw: String): String {
            val needsQuoting = raw.contains(',') || raw.contains('"') ||
                raw.contains('\n') || raw.contains('\r')
            return if (needsQuoting) "\"${raw.replace("\"", "\"\"")}\"" else raw
        }
    }

    suspend fun export(
        errors: List<ErrorEntity>,
        movesCsv: String,
        includeFullPaths: Boolean,
    ): ExportResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Millisecond stamp + collision loop: rapid exports used to overwrite
        // each other's zip when two landed in the same second (BUG-15).
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        var zip = File(dir, "sortfold-errors-$stamp.zip")
        var n = 1
        while (zip.exists()) {
            zip = File(dir, "sortfold-errors-$stamp-${n++}.zip")
        }

        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.putNextEntry(ZipEntry("report.txt"))
            out.write(buildReadableReport(errors, includeFullPaths).toByteArray())
            out.closeEntry()

            out.putNextEntry(ZipEntry("errors.json"))
            val json = Json { prettyPrint = true }
            out.write(
                json.encodeToString(
                    errors.map {
                        ErrorJson(
                            it.timestamp, it.module, it.severity, it.type,
                            PathMasker.maskText(it.message, includeFullPaths),
                            it.stackTrace?.let { s -> PathMasker.maskText(s, includeFullPaths) },
                            it.jobId, it.appVersion, it.androidVersion, it.deviceModel,
                            it.versionCode, it.buildId,
                        )
                    },
                ).toByteArray(),
            )
            out.closeEntry()

            out.putNextEntry(ZipEntry("moves.csv"))
            out.write(movesCsv.toByteArray())
            out.closeEntry()
        }
        ExportResult(zip, errors.size)
    }

    private fun buildReadableReport(errors: List<ErrorEntity>, includeFullPaths: Boolean): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("Sortfold error report")
            appendLine("Generated: ${fmt.format(Date())}")
            appendLine("Entries: ${errors.size}")
            appendLine()
            var lastDay = ""
            errors.forEach { e ->
                val day = fmt.format(Date(e.timestamp))
                if (day.substring(0, 10) != lastDay) {
                    lastDay = day.substring(0, 10)
                    appendLine("== $lastDay ==")
                }
                appendLine("[$day] ${e.severity} / ${e.module} / ${e.type}")
                appendLine("Message: ${PathMasker.maskText(e.message, includeFullPaths)}")
                if (e.jobId != null) appendLine("Job: #${e.jobId}")
                appendLine("Device: ${e.deviceModel}, Android ${e.androidVersion}, app ${e.appVersion} (${e.versionCode}) ${e.buildId}".trimEnd())
                e.stackTrace?.let { appendLine(PathMasker.maskText(it, includeFullPaths)) }
                appendLine()
            }
            appendLine("Note: personal folder paths are ${if (includeFullPaths) "included" else "masked to file names only"}.")
        }
    }
}
