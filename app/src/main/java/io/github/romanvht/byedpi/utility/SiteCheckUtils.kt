package io.github.romanvht.byedpi.utility

import android.util.Log
import io.github.romanvht.byedpi.data.SiteResult
import io.github.romanvht.byedpi.data.median
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.coroutineContext

class SiteCheckUtils(
    private val proxyIp: String,
    private val proxyPort: Int
) {

    suspend fun checkSitesAsync(
        sites: List<String>,
        requestsCount: Int,
        requestTimeout: Long,
        concurrentRequests: Int = 20,
        fullLog: Boolean,
        onSiteChecked: (suspend (SiteResult) -> Unit)? = null
    ): List<SiteResult> {
        val semaphore = Semaphore(concurrentRequests)
        return withContext(Dispatchers.IO) {
            // Own TLS session cache for each check. With the shared one, the next strategy
            // would resume sessions saved by the previous one and look faster than it is
            val sslSocketFactory = SSLContext.getInstance("TLS").apply { init(null, null, null) }.socketFactory
            sites.map { site ->
                async {
                    semaphore.withPermit {
                        val result = checkSiteAccess(site, requestsCount, requestTimeout, sslSocketFactory)
                        coroutineContext.ensureActive()
                        if (fullLog) {
                            onSiteChecked?.invoke(result)
                        }
                        result
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun checkSiteAccess(
        site: String,
        requestsCount: Int,
        timeout: Long,
        sslSocketFactory: SSLSocketFactory
    ): SiteResult = withContext(Dispatchers.IO) {
        var responseCount = 0
        val responseTimes = mutableListOf<Long>()

        val formattedUrl = if (site.startsWith("http://") || site.startsWith("https://")) site
        else "https://$site"

        val url = try {
            URL(formattedUrl)
        } catch (_: Exception) {
            Log.e("SiteChecker", "Invalid URL: $formattedUrl")
            return@withContext SiteResult(site, 0, requestsCount)
        }

        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyIp, proxyPort))

        repeat(requestsCount) { attempt ->
            coroutineContext.ensureActive()
            Log.i("SiteChecker", "Attempt ${attempt + 1}/$requestsCount for $site")

            var connection: HttpURLConnection? = null
            val startTime = System.nanoTime()
            try {
                connection = url.openConnection(proxy) as HttpURLConnection
                if (connection is HttpsURLConnection) {
                    connection.sslSocketFactory = sslSocketFactory
                }
                connection.connectTimeout = (timeout * 1000).toInt()
                connection.readTimeout = (timeout * 1000).toInt()
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Connection", "close")

                val responseCode = connection.responseCode
                val declaredLength = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    connection.contentLengthLong
                } else {
                    connection.contentLength.toLong()
                }

                var actualLength = 0L
                try {
                    val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    if (inputStream != null) {
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        val limit = if (declaredLength > 0) declaredLength else 1024L * 1024

                        while (actualLength < limit) {
                            coroutineContext.ensureActive()
                            val remaining = limit - actualLength
                            val toRead = if (remaining > buffer.size) buffer.size else remaining.toInt()
                            bytesRead = inputStream.read(buffer, 0, toRead)
                            if (bytesRead == -1) break
                            actualLength += bytesRead
                        }
                    }
                } catch (_: IOException) {
                    // Stream reading failed
                }

                if (declaredLength <= 0L || actualLength >= declaredLength) {
                    val responseTime = (System.nanoTime() - startTime) / 1_000_000
                    Log.i("SiteChecker", "Response for $site: $responseCode, Declared: $declaredLength, Actual: $actualLength, Time: $responseTime ms")
                    responseTimes.add(responseTime)
                    responseCount++
                } else {
                    Log.w("SiteChecker", "Block detected for $site, Declared: $declaredLength, Actual: $actualLength")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SiteChecker", "Error accessing $site: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }

        SiteResult(site, responseCount, requestsCount, responseTimes.median())
    }
}
