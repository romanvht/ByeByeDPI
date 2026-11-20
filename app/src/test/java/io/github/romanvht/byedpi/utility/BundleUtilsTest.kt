package io.github.romanvht.byedpi.utility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleUtilsTest {

    private val entries = listOf("manifest.json", "icon.png", "wp.wattpad.apk", "config.arm64_v8a.apk", "config.hdpi.apk", "config.tr.apk")

    private val manifest = """
        {"xapk_version": 2, "package_name": "wp.wattpad", "version_code": "3836",
         "split_apks": [
           {"file": "config.hdpi.apk", "id": "config.hdpi"},
           {"file": "wp.wattpad.apk", "id": "base"},
           {"file": "config.arm64_v8a.apk", "id": "config.arm64_v8a"},
           {"file": "config.tr.apk", "id": "config.tr"}
         ]}
    """.trimIndent()

    @Test
    fun baseApkComesFirst() {
        assertEquals(
            listOf("wp.wattpad.apk", "config.hdpi.apk", "config.arm64_v8a.apk", "config.tr.apk"),
            BundleUtils.apkFiles(manifest, entries, "wp.wattpad"),
        )
    }

    @Test
    fun bundleWithoutManifest() {
        assertEquals(
            listOf("wp.wattpad.apk", "config.arm64_v8a.apk", "config.hdpi.apk", "config.tr.apk"),
            BundleUtils.apkFiles(null, entries, "wp.wattpad"),
        )
    }

    @Test
    fun bundleForAnotherAppIsRejected() {
        assertThrows(BundleUtils.BundleException::class.java) { BundleUtils.apkFiles(manifest, entries, "com.example.other") }
    }

    @Test
    fun missingOrUnsafeFilesAreRejected() {
        val missing = manifest.replace("config.tr.apk\", \"id\"", "config.de.apk\", \"id\"")
        assertThrows(BundleUtils.BundleException::class.java) { BundleUtils.apkFiles(missing, entries, "wp.wattpad") }

        val unsafe = manifest.replace("config.tr.apk\", \"id\"", "../../evil.apk\", \"id\"")
        assertThrows(BundleUtils.BundleException::class.java) {
            BundleUtils.apkFiles(unsafe, entries + "../../evil.apk", "wp.wattpad")
        }
    }

    @Test
    fun expansionFilesAreNotSupported() {
        val withObb = manifest.replace("\"version_code\": \"3836\",", "\"version_code\": \"3836\", \"expansions\": [{\"file\": \"Android/obb/x.obb\"}],")
        assertThrows(BundleUtils.BundleException::class.java) { BundleUtils.apkFiles(withObb, entries, "wp.wattpad") }
    }

    @Test
    fun safeNames() {
        assertTrue(BundleUtils.isSafeName("config.arm64_v8a.apk"))
        assertFalse(BundleUtils.isSafeName("Android/obb/main.apk"))
        assertFalse(BundleUtils.isSafeName("..\\evil.apk"))
        assertFalse(BundleUtils.isSafeName("manifest.json"))
    }
}
