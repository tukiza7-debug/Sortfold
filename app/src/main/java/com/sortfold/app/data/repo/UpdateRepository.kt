package com.sortfold.app.data.repo

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.sortfold.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks GitHub for a newer release and offers APK download through the
 * system DownloadManager. Never force-updates; purely informational.
 */
class UpdateRepository(private val context: Context, private val client: GithubApiClient = GithubApiClient()) {

    companion object {
        const val REPO = "tukiza7-debug/Sortfold"
        const val MIN_CHECK_INTERVAL_MS = 20 * 60 * 60 * 1000L // once per day, + 4h slack
        val currentVersion: String get() = BuildConfig.VERSION_NAME
    }

    data class CheckResult(val updateAvailable: Boolean, val latest: GithubApiClient.LatestRelease?)

    suspend fun check(force: Boolean, lastCheckAt: Long): CheckResult = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - lastCheckAt < MIN_CHECK_INTERVAL_MS) {
            return@withContext CheckResult(false, null)
        }
        val latest = runCatching { client.latestRelease(REPO) }.getOrNull()
        val newer = latest != null && VersionCompare.isNewer(latest.version, currentVersion)
        CheckResult(newer, latest)
    }

    /** Enqueues the APK download; the user installs it from the notification. */
    fun enqueueApkDownload(context: Context, apkUrl: String, version: String): Long {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Sortfold $version")
            .setDescription("Sortfold update")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Sortfold-$version.apk")
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }
}
