package com.sortfold.app

import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.core.rules.ModeSuggester
import com.sortfold.app.core.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeSuggesterTest {

    private fun file(name: String, size: Long = 100, dateTaken: Long? = null) = MediaFile(
        documentId = "id-$name",
        displayName = name,
        sizeBytes = size,
        lastModifiedMillis = 0,
        mime = if (name.endsWith(".mp4")) "video/mp4" else "image/jpeg",
        dateTakenMillis = dateTaken,
    )

    @Test
    fun `empty folder suggests file type with empty reason`() {
        val s = ModeSuggester.suggest(emptyList(), "DCIM")
        assertEquals(SortMode.FILE_TYPE, s.first().mode)
        assertEquals("empty", s.first().reasonKey)
    }

    @Test
    fun `wide date span suggests date mode`() {
        val files = listOf(
            file("a.jpg", dateTaken = 1_500_000_000_000),
            file("b.jpg", dateTaken = 1_700_000_000_000),
        )
        assertEquals(SortMode.DATE_TAKEN, ModeSuggester.suggest(files, "").first().mode)
    }

    @Test
    fun `known source folder suggests source app mode`() {
        val files = listOf(file("a.jpg"), file("b.jpg"))
        val s = ModeSuggester.suggest(files, "Screenshots")
        assertEquals(SortMode.SOURCE_APP, s.first().mode)
        assertTrue(s.first().reasonKey.startsWith("known-source:"))
    }

    @Test
    fun `many extensions suggest extension mode`() {
        val files = listOf(
            file("a.jpg"), file("b.png"), file("c.mp4"), file("d.mkv"),
        )
        assertEquals(SortMode.EXTENSION, ModeSuggester.suggest(files, "").first().mode)
    }

    @Test
    fun `mixed sizes suggest size mode`() {
        val files = listOf(
            file("a.jpg", size = 100),
            file("b.jpg", size = 100 * 1024 * 1024),
        )
        assertEquals(SortMode.SIZE, ModeSuggester.suggest(files, "").first().mode)
    }

    @Test
    fun `mixed media types suggest file type mode`() {
        val files = listOf(file("a.jpg"), file("b.mp4"))
        assertEquals(SortMode.FILE_TYPE, ModeSuggester.suggest(files, "").first().mode)
    }
}


class DuplicatePolicyTest {

    @Test
    fun `rename candidate finds first free slot`() {
        assertEquals("a.jpg", DuplicatePolicy.renameCandidate(emptySet(), "a.jpg"))
        assertEquals("a (2).jpg", DuplicatePolicy.renameCandidate(setOf("a.jpg"), "a.jpg"))
        assertEquals(
            "a (3).jpg",
            DuplicatePolicy.renameCandidate(setOf("a.jpg", "a (2).jpg"), "a.jpg"),
        )
        assertEquals(
            // Convention: keep the last extension, insert the counter before it
            // (matches what file managers and browsers do for double extensions).
            "a.tar (2).gz",
            DuplicatePolicy.renameCandidate(setOf("a.tar.gz"), "a.tar.gz"),
        )
        assertEquals(
            "noext (2)",
            DuplicatePolicy.renameCandidate(setOf("noext"), "noext"),
        )
    }
}
