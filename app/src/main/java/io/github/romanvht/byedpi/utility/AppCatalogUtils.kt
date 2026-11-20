package io.github.romanvht.byedpi.utility

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.AppCatalog
import io.github.romanvht.byedpi.data.BlockedApp
import io.github.romanvht.byedpi.data.CatalogApp
import io.github.romanvht.byedpi.data.CatalogDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URL

/** The list of apps that are blocked in some countries. */
object AppCatalogUtils {
    private const val TAG = "AppCatalog"
    private const val FILE_NAME = "blocked_apps.json"
    private const val SUPPORTED_VERSION = 1
    private const val MAX_SIZE = 512 * 1024

    const val STATUS_BLOCKED = "blocked"
    const val STATUS_RESTRICTED = "restricted"
    private val statuses = setOf(STATUS_BLOCKED, STATUS_RESTRICTED)

    const val DOWNLOAD_APK = "apk"
    const val DOWNLOAD_JSON = "json"
    const val DOWNLOAD_FDROID = "fdroid"
    const val DOWNLOAD_PAGE = "page"
    const val DOWNLOAD_TEMPLATE = "template"
    /** The APKPure mirror, for apps that are only on Google Play. Needs the developer's signatures. */
    const val DOWNLOAD_MIRROR = "apkpure"

    val categories = setOf("messenger", "social", "video", "game", "reading", "knowledge", "browser", "vpn", "store")

    /**
     * Apps are only downloaded from these sites (and their subdomains), also when the list itself
     * was downloaded. Adding a site needs an app update, so a changed list cannot point anywhere else.
     * Mirror downloads are the exception: they are trusted only when they carry the developer's signature.
     */
    val trustedHosts = setOf(
        "telegram.org", "signal.org", "whatsapp.com", "whatsapp.net", "psiphon.ca", "f-droid.org", "torproject.org",
    )

    private val idPattern = Regex("[a-z0-9_-]{1,40}")
    private val packagePattern = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")
    private val datePattern = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val fingerprintPattern = Regex("[0-9a-f]{64}")

    data class Catalog(val updated: String, val noPlay: Set<String>, val apps: List<BlockedApp>) {
        companion object {
            val EMPTY = Catalog("", emptySet(), emptyList())
        }
    }

    /** The apps blocked in one country, and the tools that help anywhere. */
    data class CountryApps(
        val blocked: List<Pair<BlockedApp, String>>,
        val tools: List<BlockedApp>,
        val noPlay: Boolean,
    )

    enum class UpdateResult { Updated, UpToDate, Failed }

    @Volatile
    private var cached: Catalog? = null

    /** Reads the newest list: the one inside the app, or a newer one downloaded with [update]. */
    fun load(context: Context): Catalog {
        cached?.let { return it }

        val bundled = try {
            context.assets.open(FILE_NAME).bufferedReader().use { parse(it.readText()) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read the built-in list", e)
            null
        } ?: Catalog.EMPTY

        val saved = try {
            savedFile(context).takeIf { it.baseFile.isFile }?.let { parse(String(it.readFully())) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the downloaded list", e)
            null
        }

        val catalog = if (saved != null && saved.updated > bundled.updated) saved else bundled
        cached = catalog
        return catalog
    }

    /** Downloads the latest list from the project repository. */
    suspend fun update(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val text = try {
            NetUtils.readText(context, URL(context.getString(R.string.catalog_url)), MAX_SIZE)
        } catch (e: Exception) {
            Log.w(TAG, "Could not download the list", e)
            return@withContext UpdateResult.Failed
        }

        val catalog = parse(text) ?: return@withContext UpdateResult.Failed
        if (catalog.updated <= load(context).updated) return@withContext UpdateResult.UpToDate

        try {
            val file = savedFile(context)
            val stream = file.startWrite()
            try {
                stream.write(text.toByteArray())
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not save the list", e)
            return@withContext UpdateResult.Failed
        }

        cached = catalog
        UpdateResult.Updated
    }

    private fun savedFile(context: Context) = AtomicFile(File(context.filesDir, FILE_NAME))

    /** Reads and checks a list. Broken entries are left out; null when the whole list is unusable. */
    fun parse(json: String): Catalog? {
        val raw = try {
            Gson().fromJson(json, AppCatalog::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "The list is not valid JSON", e)
            null
        } ?: return null

        if (raw.version != SUPPORTED_VERSION) {
            Log.w(TAG, "Unsupported list version ${raw.version}")
            return null
        }

        val apps = mutableListOf<BlockedApp>()
        for (entry in raw.apps.orEmpty()) {
            val app = check(entry) ?: continue
            if (apps.none { it.id == app.id }) apps.add(app)
        }
        if (apps.isEmpty()) return null

        return Catalog(
            updated = raw.updated?.takeIf { datePattern.matches(it) }.orEmpty(),
            noPlay = raw.noPlay.orEmpty().filter { RegionUtils.isCountryCode(it) }.toSet(),
            apps = apps,
        )
    }

    private fun check(entry: CatalogApp?): BlockedApp? {
        if (entry == null) return null
        val id = entry.id?.takeIf { idPattern.matches(it) } ?: return null
        val name = entry.name?.trim()?.takeIf { it.isNotEmpty() && it.length <= 60 } ?: return null
        val packages = entry.packages.orEmpty().filter { packagePattern.matches(it) }
        if (packages.isEmpty()) return null

        val play = entry.play?.takeIf { packagePattern.matches(it) }
        val website = entry.website?.takeIf { isHttps(it) }
        val signatures = entry.signatures.orEmpty().mapNotNull { normalizeFingerprint(it) }.toSet()
        val download = entry.download?.takeIf { isUsable(it, signatures) }
        if (play == null && website == null && download == null) return null

        val regions = entry.regions.orEmpty()
            .filter { (country, status) -> RegionUtils.isCountryCode(country) && status in statuses }

        return BlockedApp(
            id = id,
            name = name,
            category = entry.category?.takeIf { it in categories }.orEmpty(),
            packages = packages,
            playPackage = play,
            website = website,
            tool = entry.tool == true,
            download = download,
            signatures = signatures,
            regions = regions,
        )
    }

    /** "14:E8:82:…" or "14e882…" to lowercase hex without colons; null when it is not a SHA-256 fingerprint. */
    fun normalizeFingerprint(value: String): String? =
        value.replace(":", "").trim().lowercase().takeIf { fingerprintPattern.matches(it) }

    private fun isUsable(download: CatalogDownload, signatures: Set<String>): Boolean = when (download.type) {
        DOWNLOAD_FDROID -> true
        DOWNLOAD_MIRROR -> signatures.isNotEmpty()
        DOWNLOAD_TEMPLATE -> isTrustedUrl(download.url) && !download.field.isNullOrBlank() &&
            !download.abis.isNullOrEmpty() && isTrustedUrl(download.template?.replace("{version}", "1")?.replace("{abi}", "a"))
        DOWNLOAD_APK, DOWNLOAD_JSON -> isTrustedUrl(download.url)
        DOWNLOAD_PAGE -> isTrustedUrl(download.url) && download.pattern?.let {
            try {
                Regex(it)
                true
            } catch (e: Exception) {
                false
            }
        } == true
        else -> false
    }.also { usable -> if (!usable) Log.w(TAG, "Unusable download: ${download.type} ${download.url}") }

    private fun isHttps(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme == "https" && !uri.host.isNullOrEmpty()
    } catch (e: Exception) {
        false
    }

    /** True for an https link to one of the [trustedHosts]. */
    fun isTrustedUrl(url: String?): Boolean {
        if (url == null || !isHttps(url)) return false
        val host = try {
            URI(url).host.lowercase()
        } catch (e: Exception) {
            return false
        }
        return trustedHosts.any { host == it || host.endsWith(".$it") }
    }

    fun forCountry(catalog: Catalog, country: String?): CountryApps {
        val blocked = if (country == null) {
            emptyList()
        } else {
            catalog.apps.mapNotNull { app -> app.regions[country]?.let { status -> app to status } }
        }
        val listed = blocked.map { it.first.id }.toSet()
        return CountryApps(
            blocked = blocked,
            tools = catalog.apps.filter { it.tool && it.id !in listed },
            noPlay = country != null && country in catalog.noPlay,
        )
    }
}
