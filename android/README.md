# Sophia for Android

**Browse. Learn. Understand.**

Sophia (from the Greek *sophía*, wisdom) is a focused, secure browser for the AMA Discipulus student portal, with a built-in Study Assistant. It is the Android edition of the browser that ships on Windows as **Lapsus**, and was called "Lapsus for Android" before it was renamed. The Windows app is unchanged.

Made by **KRLBRYN**. Sophia is an independent student project and is not made or endorsed by AMA Education System.

## Features

- **Home dashboard** with Open Discipulus (https://discipulusv2.amasystem.net/), your own portal shortcuts, recent pages, bookmarks and downloads.
- **Secure browser** built on Android System WebView.
  - Plain `http://` pages ask first, and certificate errors are never bypassed.
  - Safe Browsing is on by default.
  - No JavaScript bridges, and nothing is injected into pages.
  - Pop-ups need a tap. Camera, microphone and location are never granted to websites.
  - Tabs (up to 8), fullscreen video, file uploads, and an optional portal-only mode.
- **Gesture navigation**. Pages get the whole screen on phones, and these work everywhere:
  - Swipe **left** to reveal Back, Forward and Reload. Back and Forward are greyed out when there is nowhere to go.
  - Swipe **right** to reveal the built-in **AMA Discipulus** bookmark and a link to all bookmarks.
  - Swipe back toward the centre, tap the page, or press Back to close a panel.
  - Swipes are ignored during vertical scrolling, text selection (long press), pinch, and inside horizontally scrolling page areas.
  - Accessible alternatives: the ⋮ menu (*Navigation controls*, *Bookmarks panel*), TalkBack custom actions on the page, and keyboard shortcuts.
- **Bookmarks** with folders and search.
  - AMA Discipulus is always pinned. It can't be edited, duplicated or removed, and clearing history doesn't touch it.
- **History** with search, per-item delete and clear all.
- **Downloads** through Android's DownloadManager, saved to `Download/Sophia` or private app storage. Progress, cancel, retry and open are available.
- **Study Assistant**: explain, summarise, notes, practice questions and flashcards.
  - Works with your own OpenAI, Anthropic or OpenAI-compatible API key, or an offline demo.
  - Keys are encrypted with the Android Keystore and never hard-coded.
  - You review exactly what will be sent, after likely passwords, keys, emails and ID numbers are removed.
  - Saved notes stay on the device.
  - The assistant is for learning, not for graded or monitored assessments.
- **Adaptive layout**. It follows the actual window size, so it handles rotation, split-screen, foldables and freeform windows.
  - Phones get a bottom bar.
  - Large phones in landscape and small tablets get a navigation rail.
  - Tablets get a permanent drawer, a persistent toolbar, a tab strip, a Study/Bookmarks/History side panel, and multi-column lists.
- **Keyboard shortcuts**: Ctrl+L (address), Ctrl+T (new tab), Ctrl+W (close tab), Ctrl+R or F5 (reload), Ctrl+D (bookmark), Ctrl+B (portal panel), Alt+← and Alt+→ (back and forward).

What Sophia deliberately does **not** do:
- It does not change its browser identity.
- It does not hide app switching, or alter the focus or visibility events pages see.
- It does not misreport window or device state.
- It does not get around exam monitoring.

## Project layout

```
android/
├── core/   Pure Kotlin: URL policy, swipe classifier, study prompts and parsing, AI providers (unit-tested on the JVM)
└── app/    Android app: Compose UI, WebView browser, Room, DataStore, Keystore, DownloadManager, Hilt
```

The stack is Kotlin, Jetpack Compose, Material 3, Navigation, Room, DataStore, Hilt, Coroutines/Flow, Android Keystore, DownloadManager, OkHttp and the official Anthropic Java SDK. The minimum Android version is 8.0 (API 26), and the target is API 36.

## Building

You need JDK 17 and the Android SDK (or Android Studio). From `android/`:

```bash
./gradlew :core:test :app:testDebugUnitTest   # unit and Robolectric tests
./gradlew :app:lintDebug                      # lint
./gradlew :app:assembleDebug                  # debug APK  -> app/build/outputs/apk/debug/
./gradlew :app:assembleRelease                # release APK (R8) -> app/build/outputs/apk/release/
./gradlew :app:bundleRelease                  # release AAB -> app/build/outputs/bundle/release/
./gradlew :app:connectedDebugAndroidTest      # UI tests on a connected device or emulator
```

Without signing details, the release build is left unsigned. See [docs/RELEASING.md](docs/RELEASING.md) to sign it. Signing keys and passwords are never committed.

GitHub Actions (`.github/workflows/android.yml`) runs the tests, lint and all three builds on every change under `android/`. It uploads the APKs and the AAB as the `sophia-android` artifact, and runs the UI tests on phone and tablet emulators.

## Application ID

The app ID is **`com.krlbryn.sophia`**, and debug builds use `com.krlbryn.sophia.debug` so both can be installed side by side.

The app ID is permanent once an app is published. Sophia has not been released under any other ID, so nobody needs to migrate. Any test build of the old "Lapsus Android" is a separate app; uninstall it by hand, because its data does not carry over.

## Privacy

Sophia has no accounts, analytics or servers. History, bookmarks, downloads, notes and settings stay on the device and are excluded from cloud backup and device transfer. Websites receive what you browse. Your chosen AI provider receives only the text you review and send.
