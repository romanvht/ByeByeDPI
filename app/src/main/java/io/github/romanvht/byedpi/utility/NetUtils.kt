package io.github.romanvht.byedpi.utility

import android.content.Context
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.services.appStatus
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** HTTP requests the app makes itself, such as downloading apps. */
object NetUtils {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    /**
     * Opens a connection. The app's own traffic never goes through its VPN, so while it is
     * connected the request is sent through the local proxy, and blocked sites can be reached.
     */
    fun open(context: Context, url: URL, timeoutMillis: Int = 30_000): HttpURLConnection {
        val proxy = localProxy(context)
        val connection = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }

    /** True while requests go through the local proxy. */
    fun usesProxy(context: Context): Boolean = localProxy(context) != null

    private fun localProxy(context: Context): Proxy? {
        if (appStatus.first != AppStatus.Running) return null
        val (ip, port) = context.getPreferences().getProxyIpAndPort()
        val portNumber = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress(ip, portNumber))
    }

    /** Downloads a small text file, such as a web page or a JSON file. */
    fun readText(context: Context, url: URL, maxBytes: Int, headers: Map<String, String> = emptyMap()): String {
        val connection = open(context, url)
        try {
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for ${url.host}")
            return connection.inputStream.use { readLimited(it, maxBytes) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream, maxBytes: Int): String {
        val buffer = ByteArray(8192)
        val output = java.io.ByteArrayOutputStream()
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            if (output.size() > maxBytes) throw IOException("Response is too large")
        }
        return output.toString("UTF-8")
    }
}
