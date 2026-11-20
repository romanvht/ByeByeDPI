package io.github.romanvht.byedpi.utility

import android.content.Intent
import io.github.romanvht.byedpi.data.BlockedApp
import io.github.romanvht.byedpi.data.CatalogDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class AppActionsTest {

    private fun app(download: Boolean = false, play: Boolean = true, website: Boolean = true) = BlockedApp(
        id = "app",
        name = "App",
        category = "messenger",
        packages = listOf("com.example.app"),
        playPackage = if (play) "com.example.app" else null,
        website = if (website) "https://example.com" else null,
        tool = false,
        download = if (download) CatalogDownload(type = "apkpure") else null,
        signatures = setOf("a".repeat(64)),
        regions = emptyMap(),
    )

    @Test
    fun directDownloadComesFirst() {
        assertEquals(AppAction.Install, AppActions.primary(app(download = true), installed = false, state = null, noPlay = false))
        assertEquals(AppAction.Install, AppActions.primary(app(download = true), installed = false, state = null, noPlay = true))
    }

    @Test
    fun installedAppsOpen() {
        assertEquals(AppAction.Open, AppActions.primary(app(download = true), installed = true, state = null, noPlay = false))
    }

    @Test
    fun downloadStates() {
        val app = app(download = true)
        assertEquals(AppAction.Cancel, AppActions.primary(app, false, AppDownloads.State.Downloading(10, 100), false))
        assertEquals(AppAction.Cancel, AppActions.primary(app, false, AppDownloads.State.Preparing, false))
        assertEquals(AppAction.InstallDownloaded, AppActions.primary(app, false, AppDownloads.State.Ready(listOf(File("a.apk"))), false))
        assertEquals(AppAction.Install, AppActions.primary(app, false, AppDownloads.State.Failed(AppDownloads.Reason.Network), false))
    }

    @Test
    fun noButtonWhileAndroidInstalls() {
        val app = app(download = true)
        assertNull(AppActions.primary(app, false, AppDownloads.State.Installing, false))
        assertNull(AppActions.primary(app, false, AppDownloads.State.Confirm(Intent()), false))
    }

    @Test
    fun withoutDownloadGooglePlayOrTheWebsite() {
        assertEquals(AppAction.Play, AppActions.primary(app(), installed = false, state = null, noPlay = false))
        assertEquals(AppAction.Website, AppActions.primary(app(), installed = false, state = null, noPlay = true))
        assertEquals(AppAction.Website, AppActions.primary(app(play = false), installed = false, state = null, noPlay = false))
    }

    @Test
    fun otherActions() {
        assertEquals(listOf(AppAction.Play, AppAction.Website), AppActions.others(app(download = true), AppAction.Install, noPlay = false))
        // Google Play is not offered where it does not work
        assertEquals(listOf(AppAction.Website), AppActions.others(app(download = true), AppAction.Install, noPlay = true))
        assertEquals(emptyList<AppAction>(), AppActions.others(app(play = false), AppAction.Website, noPlay = false))
    }
}
