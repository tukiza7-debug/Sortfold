package com.sortfold.app.data.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** GitHub Releases client. The only network call the app makes. */
class GithubApiClient {

    @Serializable
    data class ReleaseAsset(val name: String = "", val browser_download_url: String = "")

    @Serializable
    data class Release(
        val tag_name: String = "",
        val name: String? = null,
        val body: String? = null,
        val html_url: String = "",
        val assets: List<ReleaseAsset> = emptyList(),
    )

    data class LatestRelease(val version: String, val notes: String, val pageUrl: String, val apkUrls: List<String>)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latestRelease(repo: String): LatestRelease? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "sortfold-app")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val release = json.decodeFromString<Release>(body)
            return LatestRelease(
                version = release.tag_name.removePrefix("v"),
                notes = release.body.orEmpty().lineSequence().take(5).joinToString("\n"),
                pageUrl = release.html_url,
                apkUrls = release.assets.filter { it.name.endsWith(".apk") }.map { it.browser_download_url },
            )
        }
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
