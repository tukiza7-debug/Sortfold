package com.sortfold.app

import com.sortfold.app.core.model.CapacityOrder
import com.sortfold.app.core.model.MediaFile
import com.sortfold.app.core.model.PlanAction
import com.sortfold.app.core.model.SortMode
import com.sortfold.app.core.rules.CapacityPacker
import com.sortfold.app.core.rules.RuleEngine
import com.sortfold.app.core.rules.SortConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.2.0 Part A: the capacity packer. Every requirement from the feature brief
 * is asserted here: exact fit, oversized files, ordering modes, zero-byte
 * files, deterministic output, auto-sort top-up and a 10,000-file budget.
 */
class CapacityPackerTest {

    private val GB = 1_000_000_000L

    private fun file(
        name: String,
        size: Long,
        modified: Long = 1_700_000_000_000,
        mime: String? = "video/mp4",
    ) = MediaFile(
        documentId = "primary:src/$name",
        displayName = name,
        sizeBytes = size,
        lastModifiedMillis = modified,
        mime = mime,
    )

    private fun config(bytes: Long?, order: CapacityOrder = CapacityOrder.SEQUENTIAL, prefix: String = "Part") =
        SortConfig(modes = setOf(SortMode.CAPACITY), capacityBytes = bytes, capacityOrder = order, capacityPrefix = prefix)

    // ---- prefix sanitising ----

    @Test
    fun `prefix is trimmed stripped capped and falls back to Part`() {
        assertEquals("Part", CapacityPacker.sanitizePrefix(null))
        assertEquals("Part", CapacityPacker.sanitizePrefix("   "))
        // Only illegal characters -> empty -> fallback.
        assertEquals("Part", CapacityPacker.sanitizePrefix("/\\:*?\"<>|"))
        assertEquals("Vacation2024", CapacityPacker.sanitizePrefix(" Vacation2024 "))
        assertEquals(24, CapacityPacker.sanitizePrefix("x".repeat(40)).length)
    }

    @Test
    fun `folder numbers are zero padded to two digits and grow past 99`() {
        assertEquals("Part 01", CapacityPacker.folderName("Part", 1))
        assertEquals("Part 99", CapacityPacker.folderName("Part", 99))
        assertEquals("Part 100", CapacityPacker.folderName("Part", 100))
    }

    // ---- basic packing ----

    @Test
    fun `exact fit at the cap boundary stays in the folder`() {
        val result = CapacityPacker.pack(listOf(file("a", GB)), GB, CapacityOrder.SEQUENTIAL, "Part")
        assertEquals(1, result.folders.size)
        assertEquals(GB, result.folders[0].totalBytes)
        assertTrue(result.oversized.isEmpty())
    }

    @Test
    fun `a file exactly the cap is not oversized`() {
        val result = CapacityPacker.pack(listOf(file("a", GB)), GB, CapacityOrder.BEST_FIT, "Part")
        assertTrue(result.oversized.isEmpty())
        assertEquals(1, result.folders.single().files.size)
    }

    @Test
    fun `a file larger than the cap goes to Oversized and is never in a part`() {
        val big = file("big.mov", 3 * GB + 1)
        val result = CapacityPacker.pack(listOf(big, file("small.mp4", 100)), GB, CapacityOrder.SEQUENTIAL, "Part")
        assertEquals(listOf(big), result.oversized)
        assertTrue(result.folders.all { f -> f.files.none { it.documentId == big.documentId } })
    }

    @Test
    fun `engine routes oversized files into an Oversized folder inside their group`() {
        val plan = RuleEngine.plan(
            listOf(file("huge.mp4", 2 * GB)),
            config(GB),
        )
        assertEquals(1, plan.size)
        assertEquals("Oversized", plan[0].destinationFolder)
        assertEquals("oversized", plan[0].reason)
    }

    @Test
    fun `oversized warning count matches the plan`() {
        val plan = RuleEngine.plan(
            listOf(file("huge1.mp4", 2 * GB), file("huge2.mp4", 3 * GB), file("ok.mp4", 5)),
            config(GB),
        )
        assertEquals(2, RuleEngine.oversizedCount(plan))
    }

    @Test
    fun `many tiny files fill folders in order and respect the cap strictly`() {
        val files = (1..250).map { file("v%03d.mp4".format(it), 40L * 1000 * 1000) } // 40 MB each
        val result = CapacityPacker.pack(files, GB, CapacityOrder.SEQUENTIAL, "Part")
        // 25 files per GB -> 10 folders
        assertEquals(10, result.folders.size)
        result.folders.forEach { assertTrue(it.totalBytes <= GB) }
        assertEquals("Part 01", result.folders.first().name)
        assertEquals("Part 10", result.folders.last().name)
    }

    @Test
    fun `zero byte files count as zero and stay in the current folder`() {
        val files = listOf(file("a.mp4", GB), file("z.meta", 0), file("y.meta", 0))
        val result = CapacityPacker.pack(files, GB, CapacityOrder.SEQUENTIAL, "Part")
        assertEquals(1, result.folders.size)
        assertEquals(3, result.folders[0].files.size)
        assertEquals(GB, result.folders[0].totalBytes)
    }

    @Test
    fun `capacity nests inside the other modes as the last level`() {
        val images = listOf(
            file("IMG_1.jpg", 600L * 1000 * 1000, mime = "image/jpeg"),
            file("IMG_2.jpg", 600L * 1000 * 1000, mime = "image/jpeg"),
            file("IMG_3.jpg", 600L * 1000 * 1000, mime = "image/jpeg"),
            file("VID_1.mp4", 700L * 1000 * 1000),
        )
        val cfg = SortConfig(
            modes = setOf(SortMode.FILE_TYPE, SortMode.CAPACITY),
            capacityBytes = GB,
        )
        val plan = RuleEngine.plan(images, cfg)
        val folders = plan.map { it.destinationFolder }.distinct()
        // Images group needs 2 part folders (600+600 -> Part 01, 600 -> Part 02), Videos one.
        assertTrue("Images/Part 01" in folders)
        assertTrue("Images/Part 02" in folders)
        assertTrue("Videos/Part 01" in folders)
        plan.forEach { assertTrue(it.destinationFolder.substringAfter('/').startsWith("Part ")) }
    }

    // ---- ordering modes ----

    @Test
    fun `sequential keeps effective date order then natural name order`() {
        val files = listOf(
            file("b.mp4", 400L * 1000 * 1000, modified = 2000),
            file("a10.mp4", 400L * 1000 * 1000, modified = 3000),
            file("a2.mp4", 400L * 1000 * 1000, modified = 3000),
            file("a1.mp4", 400L * 1000 * 1000, modified = 1000),
        )
        val result = CapacityPacker.pack(files, GB, CapacityOrder.SEQUENTIAL, "Part")
        // Dates ascending: a1 (1000), b (2000), a2+a10 (3000, natural: a2 before a10).
        val order = result.folders.flatMap { it.files }.map { it.displayName }
        assertEquals(listOf("a1.mp4", "b.mp4", "a2.mp4", "a10.mp4"), order)
    }

    @Test
    fun `best fit produces fewer folders than sequential`() {
        val files = listOf(
            file("a.mp4", 600L * 1000 * 1000),
            file("b.mp4", 500L * 1000 * 1000),
            file("c.mp4", 400L * 1000 * 1000),
            file("d.mp4", 300L * 1000 * 1000),
            file("e.mp4", 200L * 1000 * 1000),
        )
        val seq = CapacityPacker.pack(files, GB, CapacityOrder.SEQUENTIAL, "Part").folders.size
        val best = CapacityPacker.pack(files, GB, CapacityOrder.BEST_FIT, "Part").folders.size
        // FFD: 600+400 = 1GB, 500+300+200 = 1GB -> 2 folders.
        assertEquals(2, best)
        assertTrue("best fit should not use more folders", best <= seq)
    }

    // ---- determinism ----

    @Test
    fun `same input gives the same plan`() {
        val files = (1..50).map { file("f$it.mp4", (it * 37_000_000L) % 900_000_000L) }
        val a = CapacityPacker.pack(files, GB, CapacityOrder.BEST_FIT, "Part")
        val b = CapacityPacker.pack(files, GB, CapacityOrder.BEST_FIT, "Part")
        assertEquals(a.folders.map { it.name }, b.folders.map { it.name })
        assertEquals(a.folders.map { it.files.map { f -> f.displayName } }, b.folders.map { it.files.map { f -> f.displayName } })
        assertEquals(a.oversized.map { it.displayName }, b.oversized.map { it.displayName })
    }

    // ---- auto-sort top-up ----

    @Test
    fun `existing partial folder is topped up before new folders open`() {
        val files = listOf(
            file("new1.mp4", 300L * 1000 * 1000),
            file("new2.mp4", 300L * 1000 * 1000),
        )
        val existing = listOf(CapacityPacker.ExistingFolder("Part 01", 1, 500L * 1000 * 1000))
        val result = CapacityPacker.pack(files, GB, CapacityOrder.SEQUENTIAL, "Part", existing)
        assertEquals(2, result.folders.size)
        // Part 01 topped to 800 MB, then Part 02 continues the numbering.
        assertEquals("Part 01", result.folders[0].name)
        assertEquals(800L * 1000 * 1000, result.folders[0].totalBytes)
        assertEquals("Part 02", result.folders[1].name)
    }

    @Test
    fun `numbering continues after the highest existing number`() {
        val files = (1..60).map { file("v$it.mp4", 100L * 1000 * 1000) }
        val existing = listOf(
            CapacityPacker.ExistingFolder("Part 03", 3, 900L * 1000 * 1000),
            CapacityPacker.ExistingFolder("Part 07", 7, GB),
        )
        val result = CapacityPacker.pack(files, GB, CapacityOrder.SEQUENTIAL, "Part", existing)
        // Part 03 is the last partial (Part 07 is full): 100 MB fits, then new folders from 08.
        assertEquals("Part 03", result.folders.first().name)
        assertEquals("Part 08", result.folders[1].name)
        result.folders.forEach { assertTrue(it.totalBytes <= GB) }
    }

    @Test
    fun `full existing folders are never topped up`() {
        val files = listOf(file("x.mp4", 100L * 1000 * 1000))
        val existing = listOf(CapacityPacker.ExistingFolder("Part 01", 1, GB))
        val result = CapacityPacker.pack(files, GB, CapacityOrder.SEQUENTIAL, "Part", existing)
        assertTrue(result.folders.none { it.name == "Part 01" })
        assertEquals("Part 02", result.folders.single().name)
    }

    @Test
    fun `engine consumes existing usage keyed by full folder path`() {
        val files = listOf(file("v1.mp4", 300L * 1000 * 1000))
        val cfg = config(GB).copy(
            existingFolderUsage = mapOf("Part 01" to 700L * 1000 * 1000),
        )
        val plan = RuleEngine.plan(files, cfg)
        assertEquals("Part 01", plan.single().destinationFolder)
    }

    // ---- units ----

    @Test
    fun `decimal vs binary unit changes the cap the presets produce`() {
        val decimalGb = com.sortfold.app.ui.common.unitBytes("GB", decimal = true)
        val binaryGb = com.sortfold.app.ui.common.unitBytes("GB", decimal = false)
        assertEquals(1_000_000_000L, decimalGb)
        assertEquals(1_073_741_824L, binaryGb)
        assertTrue(binaryGb > decimalGb)
    }

    // ---- scale ----

    @Test
    fun `ten thousand files pack well under one second`() {
        val files = (1..10_000).map { file("v$it.mp4", 5L * 1000 * 1000) } // 50 GB total
        val start = System.nanoTime()
        val result = CapacityPacker.pack(files, 2 * GB, CapacityOrder.SEQUENTIAL, "Part")
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(25, result.folders.size)
        assertTrue("packing 10k files took ${elapsedMs}ms", elapsedMs < 1000)
    }

    // ---- duplicate policy interplay ----

    @Test
    fun `plan actions survive capacity packing with existing names`() {
        val files = listOf(file("clip.mp4", 100L * 1000 * 1000))
        val plan = RuleEngine.plan(files, config(GB), existingNames = mapOf("Part 01" to setOf("clip.mp4")))
        assertEquals(PlanAction.SKIP_DUPLICATE, plan.single().action)
        assertEquals("duplicate", plan.single().reason)
    }
}
