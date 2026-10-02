package com.sortfold.app.data.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub Releases client. The only network call the app makes.
 * Every failure mode is a typed [UpdateException]; nothing collapses into
 * "unknown error", and no token is ever sent or logged (the endpoint is
 * public and unauthenticated).
 */
class GithubApiClient(private val baseUrl: String = "https://api.github.com/") {

    @Serializable
    data class ReleaseAsset(val name: String = "", val browser_download_url: String = "")

    @Serializable
    data class Release(
        val tag_name: String = "",
        val name: String? = null,
        val body: String? = null,
        val html_url: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<ReleaseAsset> = emptyList(),
    )

    data class LatestRelease(
        val version: String,
        val notes: String,
        val pageUrl: String,
        val apkUrls: List<String>,
    )

    /** All distinct ways an update check can fail, each with its own user message. */
    sealed class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause) {
        /** No usable network (airplane mode, radio off). */
        class Offline(cause: Throwable? = null) : UpdateException("offline", cause)

        /** DNS/connect/read timeout or socket reset. */
        class Timeout(cause: Throwable? = null) : UpdateException("timeout", cause)

        /** /releases/latest returned 404: no published (non-draft) release yet. */
        class NoRelease(val httpStatus: Int = 404) : UpdateException("no-release")

        /** 403 or 429; [resetEpochSeconds] from X-RateLimit-Reset when present. */
        class RateLimited(val httpStatus: Int, val resetEpochSeconds: Long?) :
            UpdateException("rate-limited:$httpStatus")

        /** Any other non-2xx HTTP status. */
        class Http(val httpStatus: Int) : UpdateException("http:$httpStatus")

        /** Body could not be parsed as a GitHub release. */
        class Malformed(cause: Throwable? = null) : UpdateException("malformed-response", cause)

        /** 2xx parsed fine but the release carries no APK asset. */
        class NoApkAsset : UpdateException("no-apk-asset")
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latestRelease(repo: String): LatestRelease =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val request = Request.Builder()
                .url("${baseUrl}repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "sortfold-app")
                .build()
            val body: String = try {
                val response = http.newCall(request).execute()
                response.use { r ->
                    when {
                        r.code == 404 -> throw UpdateException.NoRelease()
                        r.code == 403 || r.code == 429 -> throw UpdateException.RateLimited(
                            r.code,
                            r.header("X-RateLimit-Reset")?.toLongOrNull(),
                        )
                        !r.isSuccessful -> throw UpdateException.Http(r.code)
                    }
                    r.body?.string() ?: throw UpdateException.Malformed()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: UpdateException) {
                throw e
            } catch (e: java.net.SocketTimeoutException) {
                throw UpdateException.Timeout(e)
            } catch (e: java.net.ConnectException) {
                throw UpdateException.Offline(e)
            } catch (e: java.net.UnknownHostException) {
                throw UpdateException.Offline(e)
            } catch (e: IOException) {
                throw UpdateException.Timeout(e)
            }
            val release = try {
                json.decodeFromString<Release>(body)
            } catch (e: Exception) {
                throw UpdateException.Malformed(e)
            }
            if (release.draft || release.prerelease) throw UpdateException.NoRelease()
            val apkUrls = release.assets
                .filter { it.name.endsWith(".apk") }
                .map { it.browser_download_url }
            if (apkUrls.isEmpty()) throw UpdateException.NoApkAsset()
            LatestRelease(
                version = release.tag_name.removePrefix("v"),
                notes = release.body.orEmpty().lineSequence().take(5).joinToString("\n"),
                pageUrl = release.html_url,
                apkUrls = apkUrls,
            )
        }
}

/** Pure semver-ish comparison, unit tested. Returns positive if a > b. */
object VersionCompare {
    fun compare(a: String, b: String): Int {
        val pa = a.split('.').map { it.filter { c -> c.isDigit() }.toLongOrNull() ?: 0L }
        val pb = b.split('.').map { it.filter { c -> c.isDigit() }.toLongOrNull() ?: 0L }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val va = pa.getOrElse(i) { 0L }
            val vb = pb.getOrElse(i) { 0L }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}
