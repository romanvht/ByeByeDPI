package io.github.romanvht.byedpi.utility

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.romanvht.byedpi.data.BlockedApp
import io.github.romanvht.byedpi.data.CatalogDownload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/**
 * Downloads apps, checks them and hands them to Android's installer.
 * Downloads keep running when the screen is closed or rotated.
 */
object AppDownloads {
    private const val TAG = "AppDownloads"
    private const val ATTEMPTS = 4
    private const val MIRROR_API = "https://tapi.pureapk.com/v3/get_app_his_version"

    sealed interface State {
        data object Preparing : State
        data class Downloading(val bytes: Long, val total: Long) : State
        data object Checking : State
        /** Ready to install: one APK, or the base APK and its splits. */
        data class Ready(val files: List<File>) : State
        data object Installing : State
        /** Android waits for the user to confirm the installation. */
        data class Confirm(val intent: Intent) : State
        /** [files] are kept when only the installation failed, so it can be tried again without a new download. */
        data class Failed(val reason: Reason, val files: List<File>? = null) : State
    }

    enum class Reason { Network, WrongFile, NeedsNewerAndroid, UnsupportedDevice, InstallFailed, InstallConflict }

    private class Source(val url: URL, val sha256: String? = null, val sha1: String? = null, val bundle: Boolean = false)

    private class Unavailable(val reason: Reason, message: String) : Exception(message)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val mutableStates = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = mutableStates.asStateFlow()

    /** Files of the bundles being installed, to offer them again when the installation fails. */
    private val installing = mutableMapOf<String, List<File>>()

    fun start(context: Context, app: BlockedApp) {
        val download = app.download ?: return
        val appContext = context.applicationContext
        synchronized(jobs) {
            if (jobs[app.id]?.isActive == true) return
            setState(app.id, State.Preparing)
            jobs[app.id] = scope.launch {
                val state = try {
                    State.Ready(download(appContext, app, download))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Unavailable) {
                    Log.w(TAG, "${app.id} can't be installed: ${e.message}")
                    State.Failed(e.reason)
                } catch (e: Exception) {
                    Log.w(TAG, "Download of ${app.id} failed", e)
                    State.Failed(Reason.Network)
                }
                if (state is State.Failed) appDir(appContext, app.id)?.deleteRecursively()
                setState(app.id, state)
            }
        }
    }

    fun cancel(context: Context, appId: String) {
        synchronized(jobs) { jobs.remove(appId)?.cancel() }
        appDir(context, appId)?.deleteRecursively()
        setState(appId, null)
    }

    /** Forgets a finished download, for example after the app was installed. */
    fun clear(context: Context, appId: String, deleteFiles: Boolean) {
        synchronized(jobs) {
            if (jobs[appId]?.isActive == true) return
            jobs.remove(appId)
        }
        if (deleteFiles) appDir(context, appId)?.deleteRecursively()
        setState(appId, null)
    }

    /** After a failed installation: offers to install the same files again. */
    fun keepFiles(appId: String, files: List<File>) {
        if (files.all { it.isFile }) setState(appId, State.Ready(files)) else setState(appId, null)
    }

    /** Deletes old downloads that are not in use. */
    fun cleanUp(context: Context) {
        val dir = ApkInstallUtils.apkDir(context) ?: return
        val busy = states.value.keys
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in busy && file.lastModified() < dayAgo) file.deleteRecursively()
        }
    }

    // region Installing

    /**
     * Opens Android's installer for a finished download. A single APK goes to the system installer
     * screen; a split bundle is installed with a PackageInstaller session.
     */
    fun install(activity: Activity, app: BlockedApp): Boolean {
        val ready = states.value[app.id] as? State.Ready ?: return false
        if (ready.files.any { !it.isFile }) {
            clear(activity, app.id, deleteFiles = true)
            return false
        }
        if (ready.files.size == 1) return ApkInstallUtils.install(activity, ready.files.first())

        val appContext = activity.applicationContext
        setState(app.id, State.Installing)
        synchronized(installing) { installing[app.id] = ready.files }
        scope.launch {
            try {
                ApkInstallUtils.installSession(appContext, app.id, app.packages.first(), ready.files)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start installing ${app.id}", e)
                onInstallFailed(app.id, Reason.InstallFailed)
            }
        }
        return true
    }

    /** The confirmation screen was shown; waiting for the result. */
    fun onConfirmationShown(appId: String) {
        if (states.value[appId] is State.Confirm) setState(appId, State.Installing)
    }

    fun onConfirmationNeeded(appId: String, intent: Intent) = setState(appId, State.Confirm(intent))

    fun onInstalled(context: Context, appId: String) {
        synchronized(installing) { installing.remove(appId) }
        appDir(context, appId)?.deleteRecursively()
        setState(appId, null)
    }

    fun onInstallCancelled(appId: String) {
        val files = synchronized(installing) { installing.remove(appId) }
        if (files != null) keepFiles(appId, files) else setState(appId, null)
    }

    fun onInstallFailed(appId: String, reason: Reason) {
        val files = synchronized(installing) { installing.remove(appId) }
        setState(appId, State.Failed(reason, files))
    }

    // endregion

    private fun setState(appId: String, state: State?) {
        mutableStates.update { if (state == null) it - appId else it + (appId to state) }
    }

    /** Each app's downloads are kept in their own folder. */
    private fun appDir(context: Context, appId: String): File? =
        ApkInstallUtils.apkDir(context)?.let { File(it, appId) }

    private suspend fun download(context: Context, app: BlockedApp, download: CatalogDownload): List<File> {
        val dir = appDir(context, app.id) ?: throw IOException("No storage for downloads")
        dir.deleteRecursively()
        if (!dir.mkdirs()) throw IOException("Could not create $dir")

        val source = resolve(context, app, download)
        val file = File(dir, if (source.bundle) "bundle.xapk" else "${app.id}.apk")
        transfer(context, app.id, source.url, file)

        setState(app.id, State.Checking)
        when {
            source.sha256 != null -> if (!digest(file, "SHA-256").equals(source.sha256, ignoreCase = true)) {
                throw Unavailable(Reason.WrongFile, "SHA-256 does not match")
            }
            source.sha1 != null -> if (!digest(file, "SHA-1").equals(source.sha1, ignoreCase = true)) {
                throw Unavailable(Reason.WrongFile, "SHA-1 does not match")
            }
        }

        val files = if (source.bundle) extractBundle(file, dir, app.packages.first()) else listOf(file)
        verify(context, app, files.first())
        return files
    }

    /** The base APK must be the expected app, signed with one of the developer's keys when they are known. */
    private fun verify(context: Context, app: BlockedApp, base: File) {
        val info = ApkInstallUtils.archiveInfo(context, base)
            ?: throw Unavailable(Reason.WrongFile, "not a valid APK")
        if (info.packageName !in app.packages) {
            throw Unavailable(Reason.WrongFile, "package ${info.packageName}")
        }
        if (app.signatures.isNotEmpty() && info.fingerprints.none { it in app.signatures }) {
            throw Unavailable(Reason.WrongFile, "signed with ${info.fingerprints}")
        }
    }

    private fun extractBundle(bundle: File, dir: File, packageName: String): List<File> {
        val files = ZipFile(bundle).use { zip ->
            val manifest = zip.getEntry("manifest.json")?.let { entry ->
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            val names = try {
                BundleUtils.apkFiles(manifest, zip.entries().asSequence().map { it.name }.toList(), packageName)
            } catch (e: BundleUtils.BundleException) {
                throw Unavailable(Reason.WrongFile, e.message.orEmpty())
            }
            names.mapIndexed { index, name ->
                val target = File(dir, if (index == 0) "base.apk" else "split$index.apk")
                zip.getInputStream(zip.getEntry(name)).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) }
                }
                target
            }
        }
        bundle.delete()
        return files
    }

    /** Finds the link to the APK file. */
    private fun resolve(context: Context, app: BlockedApp, download: CatalogDownload): Source {
        return when (download.type) {
            AppCatalogUtils.DOWNLOAD_APK -> Source(URL(download.url))

            AppCatalogUtils.DOWNLOAD_JSON -> {
                // Signal's update file: {"url": "...apk", "sha256sum": "..."}
                val json = JsonParser.parseString(NetUtils.readText(context, URL(download.url), 64 * 1024)).asJsonObject
                val url = json.get("url")?.asString
                if (!AppCatalogUtils.isTrustedUrl(url)) throw IOException("Untrusted link in the update file")
                Source(URL(url), sha256 = json.get("sha256sum")?.asString?.takeIf { it.length == 64 })
            }

            AppCatalogUtils.DOWNLOAD_FDROID -> {
                val packageName = app.packages.first()
                val json = JsonParser.parseString(
                    NetUtils.readText(context, URL("https://f-droid.org/api/v1/packages/$packageName"), 64 * 1024)
                ).asJsonObject
                val versionCode = json.get("suggestedVersionCode")?.asLong ?: throw IOException("No version on F-Droid")
                Source(URL("https://f-droid.org/repo/${packageName}_$versionCode.apk"))
            }

            AppCatalogUtils.DOWNLOAD_PAGE -> {
                // The link changes with every version, so it is read from the official download page.
                val page = NetUtils.readText(context, URL(download.url), 2 * 1024 * 1024)
                val link = Regex(download.pattern.orEmpty()).find(page)?.value
                    ?.replace("&amp;", "&")
                    ?: throw IOException("No download link on the page")
                if (!AppCatalogUtils.isTrustedUrl(link)) throw IOException("Untrusted link on the page")
                Source(URL(link))
            }

            AppCatalogUtils.DOWNLOAD_TEMPLATE -> {
                // For example Tor Browser: the version comes from an update file, the link is built from it.
                val json = JsonParser.parseString(NetUtils.readText(context, URL(download.url), 256 * 1024)).asJsonObject
                val version = json.get(download.field.orEmpty())?.asString
                    ?.takeIf { Regex("[0-9A-Za-z._-]{1,40}").matches(it) }
                    ?: throw IOException("No version in the update file")
                val abi = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { download.abis?.get(it) }
                    ?: throw Unavailable(Reason.UnsupportedDevice, "no build for ${Build.SUPPORTED_ABIS.toList()}")
                val link = download.template.orEmpty().replace("{version}", version).replace("{abi}", abi)
                if (!AppCatalogUtils.isTrustedUrl(link)) throw IOException("Untrusted link $link")
                Source(URL(link))
            }

            AppCatalogUtils.DOWNLOAD_MIRROR -> resolveMirror(context, app.packages.first())

            else -> throw IOException("Unknown download type ${download.type}")
        }
    }

    /**
     * Asks the APKPure mirror for the newest version, in the variant for this device's processor.
     * What it returns is not trusted: the file is installed only with the developer's signature.
     */
    private fun resolveMirror(context: Context, packageName: String): Source {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val device = JsonObject().apply {
            add("device_info", JsonObject().apply {
                addProperty("os_ver", Build.VERSION.SDK_INT.toString())
                add("abis", JsonArray().apply { add(abi) })
            })
        }
        val url = URL("$MIRROR_API?package_name=${URLEncoder.encode(packageName, "UTF-8")}&hl=en")
        val text = NetUtils.readText(
            context, url, 4 * 1024 * 1024,
            headers = mapOf("Ual-Access-Businessid" to "projecta", "Ual-Access-ProjectA" to device.toString()),
        )

        val version = JsonParser.parseString(text).asJsonObject.get("version_list")
            ?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()?.asJsonObject
            ?: throw IOException("$packageName is not on the mirror")
        if (version.string("package_name") != packageName) throw Unavailable(Reason.WrongFile, "mirror sent another app")

        val minSdk = version.string("sdk_version")?.toIntOrNull()
        if (minSdk != null && minSdk > Build.VERSION.SDK_INT) {
            throw Unavailable(Reason.NeedsNewerAndroid, "needs API $minSdk")
        }
        val nativeCode = version.get("native_code")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonPrimitive }?.map { it.asString }.orEmpty()
        if (nativeCode.isNotEmpty() && Build.SUPPORTED_ABIS.none { it in nativeCode }) {
            throw Unavailable(Reason.UnsupportedDevice, "built for $nativeCode")
        }
        if (version.get("is_o_b_b")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
            throw Unavailable(Reason.UnsupportedDevice, "needs expansion files")
        }

        val asset = version.get("asset")?.takeIf { it.isJsonObject }?.asJsonObject ?: throw IOException("No file on the mirror")
        val link = asset.string("url")?.takeIf { it.startsWith("https://") } ?: throw IOException("No https link on the mirror")
        val bundle = when (asset.string("type")) {
            "APK" -> false
            "XAPK" -> true
            else -> throw Unavailable(Reason.UnsupportedDevice, "unknown file type ${asset.string("type")}")
        }
        return Source(
            url = URL(link),
            sha256 = asset.string("file_sha256")?.takeIf { it.length == 64 },
            sha1 = asset.string("sha1")?.takeIf { it.length == 40 },
            bundle = bundle,
        )
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    /** Downloads the file. Interrupted downloads continue where they stopped when the server allows it. */
    private suspend fun transfer(context: Context, appId: String, url: URL, file: File) {
        // Retries use the address after redirects, so that they continue the same file.
        var source = url
        var attempt = 0
        while (true) {
            try {
                transferOnce(context, appId, source, file) { source = it }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (attempt >= ATTEMPTS) throw e
                Log.w(TAG, "Download interrupted, trying again ($attempt)", e)
                delay(2000L * attempt)
            }
        }
    }

    private suspend fun transferOnce(context: Context, appId: String, url: URL, file: File, onRedirect: (URL) -> Unit) {
        val existing = if (file.exists()) file.length() else 0L
        val connection = NetUtils.open(context, url)
        try {
            if (existing > 0) connection.setRequestProperty("Range", "bytes=$existing-")
            val code = connection.responseCode
            onRedirect(connection.url)
            // The whole file was already there; the checks after the download find a broken one.
            if (code == 416 && existing > 0) return
            if (code != 200 && code != 206) throw IOException("HTTP $code")

            val resumed = code == 206 && existing > 0
            val length = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            val total = if (length < 0) -1L else if (resumed) existing + length else length
            var bytes = if (resumed) existing else 0L

            connection.inputStream.use { input ->
                FileOutputStream(file, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytes += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 250) {
                            lastReport = now
                            setState(appId, State.Downloading(bytes, total))
                        }
                    }
                }
            }

            if (total > 0 && bytes != total) throw IOException("Download incomplete: $bytes of $total")
        } finally {
            connection.disconnect()
        }
    }

    private fun digest(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
