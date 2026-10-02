package com.sortfold.app

import com.sortfold.app.core.mover.StorageSafety
import com.sortfold.app.error.PathMasker
import org.junit.Assert.assertEquals
import org.junit.Test

class PathMaskerTest {

    @Test
    fun `masks middle segments by default`() {
        assertEquals(
            "primary:/.../IMG_1.jpg",
            PathMasker.mask("primary:DCIM/Camera/IMG_1.jpg", includeFullPaths = false),
        )
    }

    @Test
    fun `keeps full path when user opts in`() {
        assertEquals(
            "primary:DCIM/Camera/IMG_1.jpg",
            PathMasker.mask("primary:DCIM/Camera/IMG_1.jpg", includeFullPaths = true),
        )
    }

    @Test
    fun `handles paths without a storage root`() {
        assertEquals(".../file.bin", PathMasker.mask("/data/whatever/file.bin", false))
    }
}

class StorageSafetyTest {

    @Test
    fun `flags storage root`() {
        assertEquals(StorageSafety.TreeRisk.STORAGE_ROOT, StorageSafety.classifyTreeId("primary:"))
    }

    @Test
    fun `blocks app private trees`() {
        assertEquals(StorageSafety.TreeRisk.APP_PRIVATE, StorageSafety.classifyTreeId("primary:Android/data"))
        assertEquals(StorageSafety.TreeRisk.APP_PRIVATE, StorageSafety.classifyTreeId("primary:Android/obb/com.example"))
    }

    @Test
    fun `blocks system trees`() {
        // System volume roots and paths under them are hard-blocked.
        assertEquals(StorageSafety.TreeRisk.SYSTEM, StorageSafety.classifyTreeId("system:"))
        assertEquals(StorageSafety.TreeRisk.SYSTEM, StorageSafety.classifyTreeId("system:etc"))
        assertEquals(StorageSafety.TreeRisk.SYSTEM, StorageSafety.classifyTreeId("primary:system"))
    }

    @Test
    fun `normal folders pass`() {
        assertEquals(StorageSafety.TreeRisk.NONE, StorageSafety.classifyTreeId("primary:DCIM/Camera"))
        assertEquals(StorageSafety.TreeRisk.NONE, StorageSafety.classifyTreeId("home:Pictures"))
    }
}
