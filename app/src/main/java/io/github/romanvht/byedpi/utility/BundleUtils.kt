package io.github.romanvht.byedpi.utility

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Reads split APK bundles (.xapk): a ZIP with the base APK, its split APKs and a manifest.json
 * that lists them, as the APKPure mirror serves apps that Google Play delivers in parts.
 */
object BundleUtils {

    class BundleException(message: String) : Exception(message)

    /**
     * The APK files to install from a bundle, the base APK first.
     *
     * @param manifest the bundle's manifest.json, or null when it has none
     * @param entries the names of all files in the bundle
     * @param packageName the package the bundle must contain
     */
    fun apkFiles(manifest: String?, entries: List<String>, packageName: String): List<String> {
        val json = manifest?.let {
            try {
                JsonParser.parseString(it).asJsonObject
            } catch (e: Exception) {
                throw BundleException("manifest.json is not valid")
            }
        }

        if (json != null) {
            val bundlePackage = json.string("package_name")
            if (bundlePackage != null && bundlePackage != packageName) {
                throw BundleException("bundle is for $bundlePackage")
            }
            if ((json.get("expansions")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0) > 0) {
                throw BundleException("bundle needs expansion files")
            }
        }

        val listed = json?.get("split_apks")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { item -> item.string("file")?.let { file -> file to item.string("id") } }
            .orEmpty()

        val files = if (listed.isNotEmpty()) {
            val base = listed.firstOrNull { it.second == "base" }?.first
                ?: throw BundleException("no base APK in manifest.json")
            listOf(base) + listed.map { it.first }.filter { it != base }
        } else {
            // Without a manifest: every APK at the top level, the one named after the package first
            val apks = entries.filter { it.endsWith(".apk") && '/' !in it }
            val base = apks.firstOrNull { it == "$packageName.apk" } ?: apks.firstOrNull { it == "base.apk" }
                ?: throw BundleException("no base APK in the bundle")
            listOf(base) + apks.filter { it != base }
        }

        for (file in files) {
            if (!isSafeName(file)) throw BundleException("unsafe file name $file")
            if (file !in entries) throw BundleException("$file is missing from the bundle")
        }
        return files.distinct()
    }

    /** A plain APK file name, so it cannot point outside the folder it is extracted to. */
    fun isSafeName(name: String): Boolean =
        name.endsWith(".apk") && '/' !in name && '\\' !in name && ".." !in name && name.length in 5..200

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString
}
