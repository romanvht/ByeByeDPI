package io.github.romanvht.byedpi.data

import android.content.SharedPreferences

data class UISettings(
    val ip: String = "127.0.0.1",
    val port: Int = 1080,
    val maxConnections: Int = 512,
    val bufferSize: Int = 16384,
    val defaultTtl: Int = 0,
    val noDomain: Boolean = false,
    val desyncHttp: Boolean = true,
    val desyncHttps: Boolean = true,
    val desyncUdp: Boolean = true,
    val desyncMethod: DesyncMethod = DesyncMethod.OOB,
    val splitPosition: Int = 1,
    val splitAtHost: Boolean = false,
    val fakeTtl: Int = 8,
    val fakeSni: String = "www.iana.org",
    val oobChar: String = "a",
    val hostMixedCase: Boolean = false,
    val domainMixedCase: Boolean = false,
    val hostRemoveSpaces: Boolean = false,
    val tlsRecordSplit: Boolean = false,
    val tlsRecordSplitPosition: Int = 0,
    val tlsRecordSplitAtSni: Boolean = false,
    val hostsMode: HostsMode = HostsMode.Disable,
    val hosts: String? = null,
    val tcpFastOpen: Boolean = false,
    val udpFakeCount: Int = 1,
    val dropSack: Boolean = false,
    val fakeOffset: Int = 0,
) {
    enum class DesyncMethod {
        None, Split, Disorder, Fake, OOB, DISOOB;

        companion object {
            fun fromName(name: String): DesyncMethod =
                fromNameOrNull(name) ?: throw IllegalArgumentException("Unknown desync method: $name")

            fun fromNameOrNull(name: String): DesyncMethod? = when (name) {
                "none" -> None
                "split" -> Split
                "disorder" -> Disorder
                "fake" -> Fake
                "oob" -> OOB
                "disoob" -> DISOOB
                else -> null
            }
        }
    }

    enum class HostsMode {
        Disable, Blacklist, Whitelist;

        companion object {
            fun fromName(name: String): HostsMode =
                fromNameOrNull(name) ?: throw IllegalArgumentException("Unknown hosts mode: $name")

            fun fromNameOrNull(name: String): HostsMode? = when (name) {
                "disable" -> Disable
                "blacklist" -> Blacklist
                "whitelist" -> Whitelist
                else -> null
            }
        }
    }

    companion object {
        fun fromSharedPreferences(preferences: SharedPreferences): UISettings {
            val hostsMode = preferences.getString("byedpi_hosts_mode", null)?.let { HostsMode.fromNameOrNull(it) } ?: HostsMode.Disable

            val hosts = when (hostsMode) {
                HostsMode.Blacklist -> preferences.getString("byedpi_hosts_blacklist", null)
                HostsMode.Whitelist -> preferences.getString("byedpi_hosts_whitelist", null)
                else -> null
            }

            return UISettings(
                ip = preferences.getString("byedpi_proxy_ip", null) ?: "127.0.0.1",
                port = preferences.getString("byedpi_proxy_port", null)?.toIntOrNull() ?: 1080,
                maxConnections = preferences.getString("byedpi_max_connections", null)?.toIntOrNull() ?: 512,
                bufferSize = preferences.getString("byedpi_buffer_size", null)?.toIntOrNull() ?: 16384,
                defaultTtl = preferences.getString("byedpi_default_ttl", null)?.toIntOrNull() ?: 0,
                noDomain = preferences.getBoolean("byedpi_no_domain", false),
                desyncHttp = preferences.getBoolean("byedpi_desync_http", true),
                desyncHttps = preferences.getBoolean("byedpi_desync_https", true),
                desyncUdp = preferences.getBoolean("byedpi_desync_udp", true),
                desyncMethod = preferences.getString("byedpi_desync_method", null)?.let { DesyncMethod.fromNameOrNull(it) } ?: DesyncMethod.OOB,
                splitPosition = preferences.getString("byedpi_split_position", null)?.toIntOrNull() ?: 1,
                splitAtHost = preferences.getBoolean("byedpi_split_at_host", false),
                fakeTtl = preferences.getString("byedpi_fake_ttl", null)?.toIntOrNull() ?: 8,
                fakeSni = preferences.getString("byedpi_fake_sni", null) ?: "www.iana.org",
                oobChar = preferences.getString("byedpi_oob_data", null)?.takeIf { it.isNotEmpty() } ?: "a",
                hostMixedCase = preferences.getBoolean("byedpi_host_mixed_case", false),
                domainMixedCase = preferences.getBoolean("byedpi_domain_mixed_case", false),
                hostRemoveSpaces = preferences.getBoolean("byedpi_host_remove_spaces", false),
                tlsRecordSplit = preferences.getBoolean("byedpi_tlsrec_enabled", false),
                tlsRecordSplitPosition = preferences.getString("byedpi_tlsrec_position", null)?.toIntOrNull() ?: 0,
                tlsRecordSplitAtSni = preferences.getBoolean("byedpi_tlsrec_at_sni", false),
                tcpFastOpen = preferences.getBoolean("byedpi_tcp_fast_open", false),
                udpFakeCount = preferences.getString("byedpi_udp_fake_count", null)?.toIntOrNull() ?: 1,
                dropSack = preferences.getBoolean("byedpi_drop_sack", false),
                fakeOffset = preferences.getString("byedpi_fake_offset", null)?.toIntOrNull() ?: 0,
                hostsMode = hostsMode,
                hosts = hosts,
            )
        }
    }
}