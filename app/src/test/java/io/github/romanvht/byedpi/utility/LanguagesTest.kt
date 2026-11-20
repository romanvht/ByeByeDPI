package io.github.romanvht.byedpi.utility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Keeps the language picker, the Android 13+ locale config and the translations in sync. */
class LanguagesTest {

    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private val appLanguages = SettingsUtils.languages.map { Locale.forLanguageTag(it.first) }.toSet()

    @Test
    fun localeConfigListsTheSameLanguages() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(res, "xml/locales_config.xml"))
        val nodes = document.getElementsByTagName("locale")
        val configured = (0 until nodes.length)
            .map { Locale.forLanguageTag((nodes.item(it) as Element).getAttribute("android:name")) }
            .toSet()

        assertEquals(appLanguages, configured)
    }

    @Test
    fun everyLanguageHasTranslations() {
        for (locale in appLanguages) {
            if (locale.language == "en") continue
            val dir = File(res, resourceDir(locale))
            assertTrue("Missing ${dir.name}/strings.xml", File(dir, "strings.xml").isFile)
        }
    }

    @Test
    fun everyTranslationIsInTheLanguageList() {
        val pattern = Regex("values-([a-z]{2,3})(?:-r([A-Z]{2}))?")
        val translated = res.listFiles().orEmpty()
            .filter { File(it, "strings.xml").isFile }
            .mapNotNull { pattern.matchEntire(it.name) }
            .map { match ->
                val (language, region) = match.destructured
                Locale.forLanguageTag(if (region.isEmpty()) language else "$language-$region")
            }

        for (locale in translated) {
            assertTrue("$locale has translations but is not in SettingsUtils.languages", locale in appLanguages)
        }
    }

    private fun resourceDir(locale: Locale): String {
        // Android keeps the old code "in" for Indonesian in resource folders.
        val language = if (locale.language == "id") "in" else locale.language
        return "values-$language" + if (locale.country.isEmpty()) "" else "-r${locale.country}"
    }
}
