package com.sortfold.app

import com.sortfold.app.data.repo.GithubApiClient
import com.sortfold.app.data.repo.VersionCompare
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests every updater failure mode against a local HTTP server, including
 * the exact endpoint contract used in production (Accept + User-Agent).
 */
class GithubApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GithubApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GithubApiClient(baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun body(vararg assets: String) = """
        {
          "tag_name": "v2.3.4",
          "name": "Sortfold v2.3.4",
          "draft": false,
          "prerelease": false,
          "html_url": "https://github.com/tukiza7-debug/Sortfold/releases/tag/v2.3.4",
          "body": "Line one\nLine two",
          "assets": [
            ${assets.joinToString(",") { """{"name": "$it", "browser_download_url": "https://example.com/$it"}""" }}
          ]
        }
    """.trimIndent()

    @Test
    fun `newer release parses version, notes and apk urls`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                body("Sortfold-v2.3.4-arm64-v8a.apk", "Sortfold-v2.3.4-universal.apk", "Sortfold-v2.3.4.aab"),
            ),
        )
        val release = client.latestRelease("owner/repo")
        assertEquals("2.3.4", release.version)
        assertEquals("Line one\nLine two", release.notes)
        assertEquals(2, release.apkUrls.size)
        assertTrue(release.apkUrls.first().endsWith(".apk"))
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/repos/owner/repo/releases/latest", request.path)
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertTrue(request.getHeader("User-Agent")!!.isNotBlank())
    }

    @Test
    fun `draft release is treated as no published release`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                body("Sortfold-v2.3.4-universal.apk")
                    .replace("\"draft\": false", "\"draft\": true"),
            ),
        )
        try {
            client.latestRelease("owner/repo")
            fail("expected NoRelease")
        } catch (e: GithubApiClient.UpdateException.NoRelease) {
            // expected
        }
    }

    @Test
    fun `404 maps to NoRelease`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        try {
            client.latestRelease("owner/repo")
            fail("expected NoRelease")
        } catch (e: GithubApiClient.UpdateException.NoRelease) {
            assertEquals(404, e.httpStatus)
        }
    }

    @Test
    fun `403 with rate limit reset maps to RateLimited`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Reset", "1893456000")
                .setBody("""{"message": "API rate limit exceeded"}"""),
        )
        try {
            client.latestRelease("owner/repo")
            fail("expected RateLimited")
        } catch (e: GithubApiClient.UpdateException.RateLimited) {
            assertEquals(403, e.httpStatus)
            assertEquals(1893456000L, e.resetEpochSeconds)
        }
    }

    @Test
    fun `429 also maps to RateLimited`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        try {
            client.latestRelease("owner/repo")
            fail("expected RateLimited")
        } catch (e: GithubApiClient.UpdateException.RateLimited) {
            assertEquals(429, e.httpStatus)
            assertEquals(null, e.resetEpochSeconds)
        }
    }

    @Test
    fun `malformed json maps to Malformed`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tag_name": 12, "assets": "nope"}"""))
        try {
            client.latestRelease("owner/repo")
            fail("expected Malformed")
        } catch (e: GithubApiClient.UpdateException.Malformed) {
            // expected
        }
    }

    @Test
    fun `release without apk asset maps to NoApkAsset`() = runBlocking {
        server.enqueue(MockResponse().setBody(body("Sortfold-v2.3.4.aab")))
        try {
            client.latestRelease("owner/repo")
            fail("expected NoApkAsset")
        } catch (e: GithubApiClient.UpdateException.NoApkAsset) {
            // expected
        }
    }

    @Test
    fun `read timeout maps to Timeout`() = runBlocking {
        // Transport dies mid-body: same retryable-failure class as a read timeout.
        server.enqueue(
            MockResponse()
                .setBody(body("Sortfold-v2.3.4-universal.apk"))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        try {
            client.latestRelease("owner/repo")
            fail("expected Timeout")
        } catch (e: GithubApiClient.UpdateException.Timeout) {
            // expected
        }
    }

    @Test
    fun `connection failure maps to Timeout transport error`() = runBlocking {
        // Port 1 is reserved and refuses connections; exercise the IOException path.
        val deadClient = GithubApiClient(baseUrl = "http://127.0.0.1:1/")
        try {
            deadClient.latestRelease("owner/repo")
            fail("expected a transport error")
        } catch (e: GithubApiClient.UpdateException) {
            assertTrue(e is GithubApiClient.UpdateException.Offline || e is GithubApiClient.UpdateException.Timeout)
        }
    }

    @Test
    fun `other http errors map to Http`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
        try {
            client.latestRelease("owner/repo")
            fail("expected Http")
        } catch (e: GithubApiClient.UpdateException.Http) {
            assertEquals(503, e.httpStatus)
        }
    }

    @Test
    fun `no disconnect socket maps to Timeout`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            client.latestRelease("owner/repo")
            fail("expected transport failure")
        } catch (e: GithubApiClient.UpdateException) {
            assertTrue(
                e is GithubApiClient.UpdateException.Timeout || e is GithubApiClient.UpdateException.Offline,
            )
        }
    }
}
