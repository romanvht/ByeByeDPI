package io.github.romanvht.byedpi.utility

import io.github.romanvht.byedpi.data.BlockedApp

/** What can be done with an app in the blocked apps list. */
enum class AppAction { Open, Install, InstallDownloaded, Cancel, Play, Website }

object AppActions {

    /**
     * The main button. A direct download comes first, because it also works when the app is missing
     * from Google Play in the country. Without a button while Android's installer is busy.
     */
    fun primary(app: BlockedApp, installed: Boolean, state: AppDownloads.State?, noPlay: Boolean): AppAction? = when {
        state is AppDownloads.State.Ready -> AppAction.InstallDownloaded
        state is AppDownloads.State.Installing || state is AppDownloads.State.Confirm -> null
        state != null && state !is AppDownloads.State.Failed -> AppAction.Cancel
        installed -> AppAction.Open
        app.download != null -> AppAction.Install
        app.playPackage != null && !noPlay -> AppAction.Play
        app.website != null -> AppAction.Website
        else -> null
    }

    /** The other ways to get the app, for the menu next to the main button. */
    fun others(app: BlockedApp, primary: AppAction?, noPlay: Boolean): List<AppAction> = buildList {
        if (app.playPackage != null && !noPlay && primary != AppAction.Play) add(AppAction.Play)
        if (app.website != null && primary != AppAction.Website) add(AppAction.Website)
    }
}
