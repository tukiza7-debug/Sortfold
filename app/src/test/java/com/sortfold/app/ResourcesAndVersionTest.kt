package com.sortfold.app

import com.sortfold.app.data.repo.VersionCompare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Strings parity: every language must define exactly the same string and
 * plural names as the default (English) resource. This test fails the build
 * when a translation is missing or a stray key is left behind.
 */
class StringsParityTest {

    private fun moduleDir(): File {
        // Gradle sets the working directory to the app module for unit tests.
        val cwd = File(System.getProperty("user.dir")!!)
        return if (File(cwd, "src/main/res/values/strings.xml").exists()) cwd else {
            File(cwd, "app")
        }
    }

    private fun keysOf(file: File): Set<String> {
        val regex = Regex("""<(string|plurals) name="([^"]+)"""")
        return regex.findAll(file.readText()).map { it.groupValues[2] }.toSet()
    }

    @Test
    fun `all languages define the same keys as the default locale`() {
        val res = File(moduleDir(), "src/main/res")
        val base = keysOf(File(res, "values/strings.xml"))
        assertTrue("base strings must not be empty", base.size > 100)

        val locales = listOf("values-ms", "values-in", "values-ar", "values-zh-rCN")
        val problems = ArrayList<String>()
        for (locale in locales) {
            val f = File(res, "$locale/strings.xml")
            if (!f.exists()) {
                problems += "$locale: strings.xml missing"
                continue
            }
            val keys = keysOf(f)
            val missing = base - keys
            val extra = keys - base
            if (missing.isNotEmpty()) problems += "$locale missing: $missing"
            if (extra.isNotEmpty()) problems += "$locale extra: $extra"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `format placeholders match the default locale`() {
        val res = File(moduleDir(), "src/main/res")
        val baseFile = File(res, "values/strings.xml")
        val placeholder = Regex("""%(\d\$)?[sdf]""")
        val base = baseFile.readText().lines()
            .filter { it.contains("<string name=") }
            .associate { line ->
                val name = Regex("""name="([^"]+)""").find(line)!!.groupValues[1]
                name to placeholder.findAll(line).map { it.value.removePrefix("%").substringAfter("$") }.toSortedSet()
            }

        listOf("values-ms", "values-in", "values-ar", "values-zh-rCN").forEach { locale ->
            val f = File(res, "$locale/strings.xml")
            if (f.exists()) {
                f.readText().lines().filter { it.contains("<string name=") }.forEach { line ->
                    val name = Regex("""name="([^"]+)""").find(line)?.groupValues[1] ?: return@forEach
                    val expected = base[name] ?: return@forEach
                    val actual = placeholder.findAll(line)
                        .map { it.value.removePrefix("%").substringAfter("$") }.toSortedSet()
                    if (name in setOf("wizard_rule_summary", "preview_move_to")) {
                        // Directional arrow strings may differ; positional args still must match.
                    }
                    assertTrue(
                        "$locale/$name placeholder mismatch: expected $expected found $actual",
                        actual == expected,
                    )
                }
            }
        }
    }
}

class VersionCompareTest {

    @Test
    fun `compares numerically not lexicographically`() {
        assertTrue(VersionCompare.isNewer("1.10.0", "1.9.0"))
        assertFalse(VersionCompare.isNewer("1.9.0", "1.10.0"))
        assertTrue(VersionCompare.isNewer("2.0.0", "1.9.9"))
        assertFalse(VersionCompare.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun `handles v prefix and short versions`() {
        assertEquals(0, VersionCompare.compare("1.2.3", "1.2.3"))
        assertTrue(VersionCompare.isNewer("v1.2.4", "1.2.3"))
        assertTrue(VersionCompare.isNewer("1.3", "1.2.9"))
    }
}
