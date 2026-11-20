package io.github.romanvht.byedpi.utility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppCatalogTest {

    private val assetFile = listOf(File("src/main/assets/blocked_apps.json"), File("app/src/main/assets/blocked_apps.json"))
        .first { it.isFile }

    private val json = assetFile.readText()
    private val catalog = AppCatalogUtils.parse(json)!!

    @Test
    fun builtInListIsComplete() {
        val entries = Regex("\"id\"\\s*:").findAll(json).count()
        // Every entry passes the checks, none is dropped
        assertEquals(entries, catalog.apps.size)
        assertTrue(catalog.updated.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals(catalog.apps.size, catalog.apps.map { it.id }.toSet().size)
    }

    @Test
    fun builtInListUsesKnownValues() {
        for (app in catalog.apps) {
            assertTrue("${app.id} has an unknown category", app.category in AppCatalogUtils.categories)
            val download = app.download ?: continue
            when (download.type) {
                AppCatalogUtils.DOWNLOAD_FDROID -> {}
                AppCatalogUtils.DOWNLOAD_MIRROR -> assertTrue("${app.id} needs the developer's signatures", app.signatures.isNotEmpty())
                AppCatalogUtils.DOWNLOAD_TEMPLATE -> assertTrue(AppCatalogUtils.isTrustedUrl(download.url))
                else -> assertTrue("${app.id} downloads from an untrusted site", AppCatalogUtils.isTrustedUrl(download.url))
            }
        }
    }

    @Test
    fun everyAppCanBeInstalledDirectly() {
        for (app in catalog.apps) {
            assertNotNull("${app.id} has no direct download", app.download)
        }
    }

    @Test
    fun robloxIsListedForTurkey() {
        val turkey = AppCatalogUtils.forCountry(catalog, "TR").blocked.map { it.first.id }
        assertTrue("roblox" in turkey)
        assertTrue("discord" in turkey)
    }

    @Test
    fun mirrorDownloadsNeedSignatures() {
        val parsed = AppCatalogUtils.parse(
            """
            {"version": 1, "updated": "2030-01-01", "apps": [
              {"id": "unsigned", "name": "Unsigned", "packages": ["com.example.a"], "play": "com.example.a",
               "download": {"type": "apkpure"}},
              {"id": "signed", "name": "Signed", "packages": ["com.example.b"],
               "download": {"type": "apkpure"}, "signatures": ["14:E8:82:B1:F7:37:54:66:03:05:B1:E5:50:30:F7:6F:E6:B2:35:3F:46:C4:E5:93:D5:69:93:3E:95:BC:F7:C3", "not a fingerprint"]}
            ]}
            """.trimIndent()
        )!!
        // Without signatures the mirror download is dropped; the app stays with Google Play
        assertNull(parsed.apps.first { it.id == "unsigned" }.download)
        val signed = parsed.apps.first { it.id == "signed" }
        assertNotNull(signed.download)
        assertEquals(setOf("14e882b1f73754660305b1e55030f76fe6b2353f46c4e593d569933e95bcf7c3"), signed.signatures)
    }

    @Test
    fun fingerprints() {
        assertEquals(
            "3b6a403ca91d56ab854517720203176640816d3f923f92cca16138f5cc3d0727",
            AppCatalogUtils.normalizeFingerprint("3B:6A:40:3C:A9:1D:56:AB:85:45:17:72:02:03:17:66:40:81:6D:3F:92:3F:92:CC:A1:61:38:F5:CC:3D:07:27"),
        )
        assertNull(AppCatalogUtils.normalizeFingerprint("3B:6A:40"))
        assertNull(AppCatalogUtils.normalizeFingerprint("zz".repeat(32)))
    }

    @Test
    fun trustedUrls() {
        assertTrue(AppCatalogUtils.isTrustedUrl("https://telegram.org/dl/android/apk"))
        assertTrue(AppCatalogUtils.isTrustedUrl("https://updates.signal.org/android/latest.json"))
        assertTrue(AppCatalogUtils.isTrustedUrl("https://scontent.whatsapp.net/v/file/WhatsApp.apk?x=1"))
        assertTrue(AppCatalogUtils.isTrustedUrl("https://dist.torproject.org/torbrowser/16.0/tor-browser-android-aarch64-16.0.apk"))
        assertFalse(AppCatalogUtils.isTrustedUrl("http://telegram.org/dl/android/apk"))
        assertFalse(AppCatalogUtils.isTrustedUrl("https://telegram.org.example.com/app.apk"))
        assertFalse(AppCatalogUtils.isTrustedUrl("https://faketelegram.org/app.apk"))
        assertFalse(AppCatalogUtils.isTrustedUrl("telegram.org/app.apk"))
        assertFalse(AppCatalogUtils.isTrustedUrl(null))
    }

    @Test
    fun brokenEntriesAreLeftOut() {
        val parsed = AppCatalogUtils.parse(
            """
            {"version": 1, "updated": "2030-01-01", "noPlay": ["CN", "XX"], "apps": [
              {"id": "good", "name": "Good", "category": "video", "packages": ["com.example.good"], "play": "com.example.good",
               "regions": {"TR": "blocked", "RU": "maybe", "XX": "blocked"}},
              {"id": "evil", "name": "Evil", "packages": ["com.example.evil"],
               "download": {"type": "apk", "url": "https://example.com/evil.apk"}},
              {"id": "no-package", "name": "No package", "packages": [], "play": "com.example.none"},
              {"id": "good", "name": "Duplicate", "packages": ["com.example.other"], "play": "com.example.other"}
            ]}
            """.trimIndent()
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals(listOf("good"), parsed.apps.map { it.id })
        assertEquals(mapOf("TR" to AppCatalogUtils.STATUS_BLOCKED), parsed.apps.single().regions)
        assertEquals(setOf("CN"), parsed.noPlay)
    }

    @Test
    fun unsupportedListsAreRejected() {
        assertNull(AppCatalogUtils.parse("not json"))
        assertNull(AppCatalogUtils.parse("""{"version": 2, "apps": [{"id": "a", "name": "A", "packages": ["a.b"], "play": "a.b"}]}"""))
        assertNull(AppCatalogUtils.parse("""{"version": 1, "apps": []}"""))
    }

    @Test
    fun appsForCountry() {
        val russia = AppCatalogUtils.forCountry(catalog, "RU")
        assertTrue(russia.blocked.isNotEmpty())
        assertTrue(russia.blocked.any { (app, status) -> app.id == "telegram" && status == AppCatalogUtils.STATUS_RESTRICTED })
        // Tools that are already listed as blocked are not shown twice
        val listed = russia.blocked.map { it.first.id }.toSet()
        assertTrue(russia.tools.none { it.id in listed })
        assertFalse(russia.noPlay)

        assertTrue(AppCatalogUtils.forCountry(catalog, "CN").noPlay)

        val unknown = AppCatalogUtils.forCountry(catalog, null)
        assertTrue(unknown.blocked.isEmpty())
        assertTrue(unknown.tools.isNotEmpty())
    }
}
