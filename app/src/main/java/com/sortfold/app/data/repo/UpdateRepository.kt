package com.sortfold.app.data.repo

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.sortfold.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks GitHub for a newer release and offers APK download through the
 * system DownloadManager. Never force-updates; purely informational.
 * Every failure is surfaced as a typed result so the Settings UI can show the
 * exact reason, and is logged to the Error Library by the caller.
 */
class UpdateRepository(
    private val context: Context,
    private val client: GithubApiClient = GithubApiClient(),
) {

    companion object {
        const val MIN_CHECK_INTERVAL_MS = 20 * 60 * 60 * 1000L // once per day, + 4h slack
        val REPO: String get() = BuildConfig.GITHUB_REPO
        val currentVersion: String get() = BuildConfig.VERSION_NAME
    }

    sealed class CheckResult {
        /** Checked; no newer version. */
        data object UpToDate : CheckResult()

        /** A newer published release exists; [release] carries notes + APK urls. */
        data class UpdateAvailable(val release: GithubApiClient.LatestRelease) : CheckResult()

        /** The check itself failed; [error] distinguishes the cause. */
        data class Failure(val error: GithubApiClient.UpdateException) : CheckResult()

        /** Throttled: auto-check ran less than a day ago. */
        data object Skipped : CheckResult()
    }

    suspend fun check(force: Boolean, lastCheckAt: Long): CheckResult = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - lastCheckAt < MIN_CHECK_INTERVAL_MS) {
            return@withContext CheckResult.Skipped
        }
        try {
            if (!isNetworkAvailable()) throw GithubApiClient.UpdateException.Offline()
            val latest = client.latestRelease(REPO)
            if (VersionCompare.isNewer(latest.version, currentVersion)) {
                CheckResult.UpdateAvailable(latest)
            } else {
                CheckResult.UpToDate
            }
        } catch (e: GithubApiClient.UpdateException) {
            CheckResult.Failure(e)
        }
    }

    /** Picks the APK matching this device's ABI, falling back to the universal build. */
    fun pickApkForDevice(apkUrls: List<String>): String? {
        val abis = Build.SUPPORTED_ABIS
        val candidates = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        for (abi in candidates) {
            if (abi in abis) {
                apkUrls.firstOrNull { it.contains("-$abi.apk") }?.let { return it }
            }
        }
        return apkUrls.firstOrNull { it.contains("-universal.apk") }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Enqueues the APK download; the user installs it from the notification. */
    fun enqueueApkDownload(context: Context, apkUrl: String, version: String): Long {
        require(Uri.parse(apkUrl).scheme == "https") { "apk download url must be https" }
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
