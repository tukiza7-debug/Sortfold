package com.sortfold.app

import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.MediaFile
import com.sortfold.app.core.model.MediaType
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.model.ResolutionClass
import com.sortfold.app.core.model.SizeBucket
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.core.model.SourceApp
import com.sortfold.app.core.rules.RuleEngine
import com.sortfold.app.core.rules.SortConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun file(
        name: String,
        size: Long = 1000,
        mime: String? = "image/jpeg",
        modified: Long = 1_700_000_000_000,
        width: Int? = null,
        height: Int? = null,
    ) = MediaFile(
        documentId = "primary:test/$name",
        displayName = name,
        sizeBytes = size,
        lastModifiedMillis = modified,
        mime = mime,
        width = width,
        height = height,
    )

    @Test
    fun `file type mode buckets by mime`() {
        val config = SortConfig(modes = setOf(SortMode.FILE_TYPE))
        val plan = RuleEngine.plan(
            listOf(
                file("a.jpg", mime = "image/jpeg"),
                file("b.mp4", mime = "video/mp4"),
                file("c.pdf", mime = "application/pdf"),
            ),
            config,
        )
        assertEquals(listOf("Images", "Videos", "Other"), plan.map { it.destinationFolder })
    }

    @Test
    fun `extension falls back from name when mime is generic`() {
        val config = SortConfig(modes = setOf(SortMode.EXTENSION))
        val plan = RuleEngine.plan(listOf(file("clip.MKV", mime = "application/octet-stream")), config)
        assertEquals("mkv", plan[0].destinationFolder)
    }

    @Test
    fun `date mode produces year and month segments`() {
        val year = RuleEngine.dateSegment(1_700_000_000_000, DateGranularity.YEAR)
        val month = RuleEngine.dateSegment(1_700_000_000_000, DateGranularity.MONTH)
        assertEquals("2023", year)
        assertEquals("2023-11", month)
    }

    @Test
    fun `date mode prefers date taken over last modified`() {
        val config = SortConfig(modes = setOf(SortMode.DATE_TAKEN))
        val f = file("x.jpg").copy(dateTakenMillis = 1_600_000_000_000) // 2020-09
        val plan = RuleEngine.plan(listOf(f), config)
        assertEquals("2020-09", plan[0].destinationFolder)
    }

    @Test
    fun `source app detects camera screenshots whatsapp telegram downloads`() {
        assertEquals(SourceApp.CAMERA, SourceApp.fromPath("primary:DCIM/Camera/IMG_1.jpg"))
        assertEquals(SourceApp.SCREENSHOTS, SourceApp.fromPath("primary:Pictures/Screenshots/s.png"))
        assertEquals(SourceApp.WHATSAPP, SourceApp.fromPath("primary:Android/media/com.whatsapp/Media/x.jpg"))
        assertEquals(SourceApp.TELEGRAM, SourceApp.fromPath("primary:Pictures/org.telegram.messenger/t.mp4"))
        assertEquals(SourceApp.DOWNLOADS, SourceApp.fromPath("primary:Download/file.zip"))
        assertEquals(SourceApp.OTHER, SourceApp.fromPath("primary:Random/stuff.bin"))
    }

    @Test
    fun `resolution classes follow orientation then quality`() {
        assertEquals(ResolutionClass.PORTRAIT, ResolutionClass.of(1080, 1920))
        assertEquals(ResolutionClass.UHD_4K, ResolutionClass.of(3840, 2160))
        assertEquals(ResolutionClass.HD, ResolutionClass.of(1920, 1080))
        assertEquals(ResolutionClass.SD, ResolutionClass.of(640, 480))
        assertEquals(ResolutionClass.SD, ResolutionClass.of(null, null))
    }

    @Test
    fun `size buckets use thresholds`() {
        assertEquals(SizeBucket.SMALL, SizeBucket.of(500_000))
        assertEquals(SizeBucket.MEDIUM, SizeBucket.of(10L * 1024 * 1024))
        assertEquals(SizeBucket.LARGE, SizeBucket.of(60L * 1024 * 1024))
    }

    @Test
    fun `name pattern rules match prefix contains suffix`() {
        val config = SortConfig(
            modes = setOf(SortMode.NAME_PATTERN),
            nameRules = listOf(
                com.sortfold.app.core.model.NameRule(com.sortfold.app.core.model.NamePatternType.PREFIX, "IMG_", "Camera"),
                com.sortfold.app.core.model.NameRule(com.sortfold.app.core.model.NamePatternType.CONTAINS, "holiday", "Trip"),
            ),
        )
        val plan = RuleEngine.plan(
            listOf(file("IMG_1234.jpg"), file("my-holiday-beach.jpg"), file("other.png")),
            config,
        )
        assertEquals("Camera", plan[0].destinationFolder)
        assertEquals("Trip", plan[1].destinationFolder)
        assertEquals("", plan[2].destinationFolder)
    }

    @Test
    fun `combined modes nest in canonical priority order`() {
        val config = SortConfig(modes = setOf(SortMode.EXTENSION, SortMode.FILE_TYPE, SortMode.SIZE))
        val plan = RuleEngine.plan(listOf(file("a.jpg", size = 100_000_000)), config)
        assertEquals("Images/Large/jpg", plan[0].destinationFolder)
    }

    @Test
    fun `duplicate skip keeps existing destination name`() {
        val config = SortConfig(modes = setOf(SortMode.FILE_TYPE))
        val plan = RuleEngine.plan(listOf(file("same.jpg"), file("same.jpg")), config)
        assertEquals(PlanAction.MOVE, plan[0].action)
        assertEquals(PlanAction.SKIP_DUPLICATE, plan[1].action)
        assertEquals("duplicate", plan[1].reason)
    }

    @Test
    fun `duplicate rename generates first free candidate`() {
        val config = SortConfig(
            modes = setOf(SortMode.FILE_TYPE),
            duplicatePolicy = com.sortfold.app.core.model.DuplicatePolicy.RENAME,
        )
        val plan = RuleEngine.plan(
            listOf(file("same.jpg"), file("same.jpg"), file("same.jpg")),
            config,
        )
        assertEquals("same.jpg", plan[0].destinationName)
        assertEquals("same (2).jpg", plan[1].destinationName)
        assertEquals("same (3).jpg", plan[2].destinationName)
        assertEquals(PlanAction.RENAME, plan[1].action)
    }

    @Test
    fun `duplicate replace flags replacement`() {
        val config = SortConfig(
            modes = setOf(SortMode.FILE_TYPE),
            duplicatePolicy = com.sortfold.app.core.model.DuplicatePolicy.REPLACE,
        )
        val plan = RuleEngine.plan(listOf(file("same.jpg"), file("same.jpg")), config)
        assertEquals(PlanAction.MOVE, plan[0].action)
        assertEquals(PlanAction.REPLACE, plan[1].action)
    }

    @Test
    fun `directories are never planned`() {
        val dir = file("folder").copy(isDirectory = true, mime = null)
        val plan = RuleEngine.plan(listOf(dir), SortConfig(modes = setOf(SortMode.FILE_TYPE)))
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `totals count moves skips and bytes`() {
        val config = SortConfig(
            modes = setOf(SortMode.FILE_TYPE),
            duplicatePolicy = com.sortfold.app.core.model.DuplicatePolicy.SKIP,
        )
        val plan = RuleEngine.plan(
            listOf(file("a.jpg", size = 10), file("a.jpg", size = 20), file("b.jpg", size = 30)),
            config,
        )
        val totals = RuleEngine.totals(plan)
        assertEquals(2, totals.moveCount)
        assertEquals(1, totals.skipCount)
        assertEquals(40L, totals.moveBytes)
    }

    @Test
    fun `media type detection from extension`() {
        assertEquals(MediaType.IMAGE, MediaType.fromMimeAndName(null, "photo.heic"))
        assertEquals(MediaType.VIDEO, MediaType.fromMimeAndName(null, "movie.webm"))
        assertEquals(MediaType.OTHER, MediaType.fromMimeAndName(null, "notes.txt"))
    }
}
