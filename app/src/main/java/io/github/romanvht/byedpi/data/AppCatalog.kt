package io.github.romanvht.byedpi.data

/**
 * The list of apps that are blocked in some countries, read from assets/blocked_apps.json
 * (or a newer copy downloaded from the project repository).
 *
 * Optional fields are nullable because the list is read with Gson, which does not use
 * Kotlin default values.
 */
data class AppCatalog(
    val version: Int = 0,
    /** Date of the last update, yyyy-MM-dd. */
    val updated: String? = null,
    /** Countries (ISO 3166 codes) where Google Play is not available. */
    val noPlay: List<String>? = null,
    val apps: List<CatalogApp>? = null,
)

data class CatalogApp(
    val id: String? = null,
    val name: String? = null,
    val category: String? = null,
    /** Every package name the app is known by. The first one is the one its own download has. */
    val packages: List<String>? = null,
    /** Package name on Google Play, or null when the app is not there. */
    val play: String? = null,
    val website: String? = null,
    /** Listed for every country, under the helpful tools. */
    val tool: Boolean? = null,
    val download: CatalogDownload? = null,
    /**
     * SHA-256 fingerprints of the developer's signing certificates, as the developer publishes them
     * in https://<their site>/.well-known/assetlinks.json. A download is installed only when it is
     * signed with one of them. Required for mirror downloads.
     */
    val signatures: List<String>? = null,
    /** Country code to a status: "blocked" or "restricted" (slowed down or partly blocked). */
    val regions: Map<String, String>? = null,
)

data class CatalogDownload(
    /**
     * "apk" (direct link), "json" (Signal-style update file), "fdroid", "page" (link found on a
     * web page), "template" (link built from a version number) or "apkpure" (the APKPure mirror).
     */
    val type: String? = null,
    val url: String? = null,
    /** For "page": a regular expression that finds the APK link on the page. */
    val pattern: String? = null,
    /** For "template": the field of the JSON file at [url] that holds the version. */
    val field: String? = null,
    /** For "template": the download link, with {version} and {abi} in it. */
    val template: String? = null,
    /** For "template": Android ABI names to the names used in the link. */
    val abis: Map<String, String>? = null,
)

/** A checked catalog entry with all the required fields. */
data class BlockedApp(
    val id: String,
    val name: String,
    val category: String,
    val packages: List<String>,
    val playPackage: String?,
    val website: String?,
    val tool: Boolean,
    val download: CatalogDownload?,
    /** Lowercase hex SHA-256 certificate fingerprints, without colons. */
    val signatures: Set<String>,
    val regions: Map<String, String>,
)
