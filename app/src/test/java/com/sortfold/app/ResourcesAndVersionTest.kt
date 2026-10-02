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

/**
 * Language quality gate (BUG 5): the Indonesian file must not contain Malay
 * words, and no locale may leave a user-facing string untranslated (equal to
 * the English value) unless the value is legitimately language-neutral.
 */
class TranslationQualityTest {

    private fun moduleDir(): File {
        val cwd = File(System.getProperty("user.dir")!!)
        return if (File(cwd, "src/main/res/values/strings.xml").exists()) cwd else File(cwd, "app")
    }

    private fun readStrings(file: File): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        file.readText().lines().forEach { line ->
            val m = Regex("""<string name="([^"]+)">(.*?)</string>""").find(line) ?: return@forEach
            out[m.groupValues[1]] = m.groupValues[2]
        }
        return out
    }

    /** Distinctly Malay words that must never appear in the Indonesian UI. */
    private val malayInIndonesian = listOf(
        "daripada", "Ralat", "ralat", "Pemindah\\b", "urungkan", "Urungkan",
        "muat turun", "dimuat turun", "di muat turun", "\\bkekal\\b", "kekalkan",
        "selepas", "sejarah", "Sejarah", "Lalai", "lalai", "Amaran", "amaran",
        "Eksport", "eksport", "Laluan", "laluan", "Kandungan", "kandungan",
        "dihantar", "bateri", "\\bmahu\\b", "\\bwujud\\b", "dikesan",
        "tersenarai", "Corak", "corak", "Lain-lain", "lain-lain", "dipadam",
        "memadam", "ditutup rapat", "penyimpanan awan", "Kemas kini", "kemas kini",
        "penjadualan", "Tetapan", "tetapan", "setiap masa", "bila-bila masa", "\\bboleh\\b",
    )

    @Test
    fun `indonesian file contains no Malay vocabulary`() {
        val res = File(moduleDir(), "src/main/res")
        val text = File(res, "values-in/strings.xml").readText()
        val offenders = malayInIndonesian.filter { Regex(it).containsMatchIn(text) }
        assertTrue("Malay words found in values-in: $offenders", offenders.isEmpty())
    }

    @Test
    fun `no locale leaves a translatable string in English`() {
        val res = File(moduleDir(), "src/main/res")
        val base = readStrings(File(res, "values/strings.xml"))

        // Values that are legitimately identical across languages.
        val neutral = setOf(
            "app_name", "lang_en", "lang_ms", "lang_ar", "lang_zh", "lang_in", "about_github",
            "severity_info", "preview_move_to", "errors_group_count", "notif_progress_x_of_y",
            "mode_extension_desc",  // a list of file extensions: language-neutral
            "history_auto_tag",  // "Auto" is natural in both Indonesian and Malay
        )

        listOf("values-in", "values-ms", "values-ar", "values-zh-rCN").forEach { locale ->
            val translated = readStrings(File(res, "$locale/strings.xml"))
            val untranslated = base.filter { (key, enValue) ->
                key !in neutral &&
                    translated[key] == enValue &&
                    Regex("[A-Za-z]{3,}").containsMatchIn(enValue) &&
                    !enValue.contains("%")  // placeholder-only strings carry no words
            }
            assertTrue(
                "$locale leaves strings untranslated: ${untranslated.keys}",
                untranslated.isEmpty(),
            )
        }
    }

    @Test
    fun `english words are not used for severity and module labels`() {
        val res = File(moduleDir(), "src/main/res")
        val indonesian = readStrings(File(res, "values-in/strings.xml"))
        // The device report showed raw English "Error" in the Indonesian UI.
        assertTrue(indonesian["severity_error"] != "Error")
        assertTrue(indonesian["errors_title"] != "Error Library")
    }
}
