<div align="center">
  <p>
    <img src=".github/images/app.svg" alt="Sidestep logo" width="160" />
  </p>
  <h1>Sidestep</h1>
  <p>
    English |
    <a href="README-ru.md">Русский</a> |
    <a href="README-tr.md">Türkçe</a>
  </p>
  <p>
    <a href="https://github.com/metetv2021-cloud/ByeByeDPI/actions/workflows/build.yml"><img src="https://github.com/metetv2021-cloud/ByeByeDPI/actions/workflows/build.yml/badge.svg" alt="Build" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="License: GPL-3.0" /></a>
  </p>
</div>

Sidestep opens websites and apps that your internet provider blocks with deep packet inspection (DPI). It runs [ByeDPI](https://github.com/hufrea/byedpi) on your phone or TV and sends your traffic through it.

Sidestep is not a VPN service. It uses Android's VPN feature to redirect traffic on the device, but nothing is sent to a remote server. It does not encrypt your traffic or hide your IP address.

Sidestep is a redesigned fork of [ByeByeDPI](https://github.com/romanvht/ByeByeDPI) by romanvht.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot1.png" alt="Main screen" width="200" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot2.png" alt="Strategy finder" width="200" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot3.png" alt="Welcome screen" width="200" />
</p>

---

### Features

* **One button.** Tap to connect. The screen tells you what is happening and what to do next.
* **Finds what works.** The strategy finder tests ready-made strategies against the sites you choose, scores each one and lets you apply the best one in one tap.
* **Saved strategies.** Switch strategies from the main screen. Pin, rename and edit them in the strategy editor.
* **Blocked apps.** Sidestep works out which country you are in and lists the apps that are blocked there. It installs them directly with one tap: from their developers' official sites or F-Droid, and apps that are only on Google Play from the APKPure mirror. A file from the mirror is installed only if it is signed with the developer's own key. Nothing is downloaded until you tap a button.
* **For beginners and experts.** Use the visual editor, or write ByeDPI command-line arguments yourself.
* **VPN or proxy.** VPN mode works for all apps, all apps except the ones you choose, or only the ones you choose. Proxy mode gives you a local SOCKS5 proxy for single apps.
* **Starts by itself.** Connect when the device boots or when you open the app. Quick Settings tile and home screen shortcuts for pinned strategies.
* **Android TV.** Works on TVs and TV boxes with a remote control.
* **18 languages.** English, Arabic, Azerbaijani, Belarusian, Chinese (Simplified), French, German, Hindi, Indonesian, Kazakh, Persian, Portuguese (Brazil), Russian, Spanish, Turkish, Ukrainian, Uzbek and Vietnamese.
* **Looks at home.** Light and dark themes, and colors from your wallpaper on Android 12 and newer.
* **Backup.** Export and import settings, strategies, app lists and domain lists. Backups made with ByeByeDPI can be imported too.

### Getting started

1. Install the app and open it. The welcome screen lets you pick a language.
2. Tap **Connect** and allow the VPN request. Android shows it only the first time.
3. If some sites still don't open, go to **Find a working strategy** and tap **Start test**. When it finishes, tap **Apply** on the strategy at the top.

To start automatically after a reboot, turn on **Settings → Start when the device turns on**. Connect once by hand first, so Android remembers the VPN permission.

On some devices Android stops background apps to save battery. If the connection drops, allow the app to run without battery restrictions (the app offers this after the first connection).

How to set up Sber TV boxes (in Russian): [sbox.md](sbox.md).

### Moving from ByeByeDPI

Sidestep has its own app ID, so it installs next to ByeByeDPI instead of replacing it. To keep your settings, export them in ByeByeDPI (Settings → ⋮ menu → Export settings) and import the file in Sidestep (Settings → Import settings). Android runs only one VPN at a time, so keep only one of them connected.

### Building

You need JDK 17 or newer and the Android SDK with NDK 27.0.12077973 and CMake 3.22.1. The native code does not build on Windows; use Linux, macOS or WSL.

1. Clone the repository with its submodules:

```bash
git clone --recurse-submodules https://github.com/metetv2021-cloud/ByeByeDPI.git
```

2. Build from the repository root:

```bash
./gradlew assembleDebug
```

3. The APKs are in `app/build/outputs/apk/debug/`. There is one APK per processor type and a universal APK that works everywhere.

`./gradlew assembleRelease` builds smaller, optimized APKs. They are unsigned; sign them with your own key using `apksigner`.

Every push to this repository is also built on GitHub Actions. You can download the debug APKs from the run's **Artifacts** section.

### Updating the blocked apps list

The list is in `app/src/main/assets/blocked_apps.json`. Each app has its package names, its official site, an optional download (`apk`, `json`, `fdroid` or `page`) and the countries where it is `blocked` or `restricted`. Apps that are only on Google Play use the `apkpure` download, which needs `signatures`: the SHA-256 certificate fingerprints the developer publishes in `https://<their site>/.well-known/assetlinks.json`. A mirror file signed with any other key is rejected. After editing, change `updated` to today's date and push to the `master` branch: the app picks up the new list when someone taps **Update the list**. For safety, downloads may only come from the sites in `trustedHosts` in `AppCatalogUtils.kt`, so a new download site needs an app update.

### Translations

The English texts are in `app/src/main/res/values/strings.xml`, and each language has its own `values-<code>/strings.xml`. The translations were made with the help of machine translation, so corrections from native speakers are very welcome.

### Credits

Sidestep is built on the work of these open-source projects:

* [ByeByeDPI](https://github.com/romanvht/ByeByeDPI) by [romanvht](https://github.com/romanvht), the app this fork is based on
* [ByeDPIAndroid](https://github.com/dovecoteescapee/ByeDPIAndroid) by [dovecoteescapee](https://github.com/dovecoteescapee), the original Android app
* [ByeDPI](https://github.com/hufrea/byedpi) by [hufrea](https://github.com/hufrea), the engine that bypasses DPI
* [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) by [heiher](https://github.com/heiher), which connects the VPN to the proxy

### License

[GNU General Public License v3.0](LICENSE), the same as the projects above.
