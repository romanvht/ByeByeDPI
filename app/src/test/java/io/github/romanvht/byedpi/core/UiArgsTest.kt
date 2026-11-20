package io.github.romanvht.byedpi.core

import io.github.romanvht.byedpi.data.UISettings
import io.github.romanvht.byedpi.utility.checkIpAndPortInArgs
import org.junit.Assert.assertEquals
import org.junit.Test

class UiArgsTest {

    private fun args(settings: UISettings) = ByeDpiProxyUIPreferences(settings).uiargs.toList()

    @Test
    fun defaultSettings() {
        assertEquals(
            listOf(
                "ciadpi", "-i127.0.0.1", "-p1080", "-c512", "-b16384", "-Kt,h",
                "-o1", "-e97", "-An", "-Ku", "-a1", "-An",
            ),
            args(UISettings()),
        )
    }

    @Test
    fun fakeWithHostBlacklist() {
        val settings = UISettings(
            desyncMethod = UISettings.DesyncMethod.Fake,
            splitPosition = 2,
            splitAtHost = true,
            fakeTtl = 5,
            hostsMode = UISettings.HostsMode.Blacklist,
            hosts = "a.com\nb.com",
            desyncUdp = false,
        )
        assertEquals(
            listOf(
                "ciadpi", "-i127.0.0.1", "-p1080", "-c512", "-b16384",
                "-H:a.com b.com", "-An", "-Kt,h", "-f2+h", "-t5", "-nwww.iana.org", "-An",
            ),
            args(settings),
        )
    }

    @Test
    fun addressIsFoundInGeneratedArguments() {
        val generated = args(UISettings(ip = "0.0.0.0", port = 2080)).drop(1)
        assertEquals("0.0.0.0" to "2080", generated.checkIpAndPortInArgs())
    }
}
