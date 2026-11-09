# Sophia for Android

**Browse. Learn. Understand.**

Sophia (from the Greek *sophía*, wisdom) is a focused, secure browser for the AMA Discipulus student portal, with a built-in Study Assistant. It is the Android edition of the browser that ships on Windows as **Lapsus**, and was called "Lapsus for Android" before it was renamed. The Windows app is unchanged.

Made by **KRLBRYN**. Sophia is an independent student project and is not made or endorsed by AMA Education System.

## Features

- **Opens straight into the portal.** Sophia launches into a fullscreen AMA Discipulus page (or your chosen homepage). Android's status and navigation bars are hidden in real immersive mode, and a swipe from the screen edge brings them back. There is no toolbar, tab bar or button over the page, and nothing dims or tints it. A thin maroon bar shows loading progress.
- **Side panels** open with an edge swipe and close when you swipe them back toward their edge, tap the page beside them, or press Back:
  - Swipe in from the **left edge** for the **address bar** (URL, search or address, Go) and **bookmarks**: Discipulus, LMS and Aurora Login, plus *Manage* and your open tabs. LMS and Aurora Login stay "Not set up" until you enter their addresses; Sophia never guesses them.
  - Swipe in from the **right edge** for **Back, Forward, Reload and Settings**. Back and Forward are greyed out when there's nowhere to go.
  - The panels are translucent grey, and the page behind them dims only while a panel is open.
  - Swipes must start near an edge. Vertical scrolling, long-press text selection, pinch zoom, horizontally scrolling page areas, buttons and forms are left alone. Android's back gesture is held off a 200dp strip in the middle of each edge.
  - Alternatives: TalkBack custom actions on the page, and keyboard shortcuts (Ctrl+L for the address bar, Ctrl+B for bookmarks, Ctrl+J for the controls).
- **Secure browser** built on Android System WebView.
  - Plain `http://` pages ask first, and certificate errors are never bypassed.
  - Safe Browsing is on by default.
  - No JavaScript bridges, and nothing is injected into pages.
  - Pop-ups need a tap. Camera, microphone and location are never granted to websites.
  - Tabs (up to 8), fullscreen video, file uploads, CAPTCHA and sign-in pages work as normal, and an optional portal-only mode is available.
- **Home dashboard** (from Settings → app navigation) with portal shortcuts, recent pages, bookmarks and downloads.
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
- **Adaptive layout**. It follows the actual window size, so it handles rotation, split-screen, foldables and freeform windows. The browser is fullscreen everywhere, and panels are wider on tablets. Outside the browser:
  - Phones get a bottom bar.
  - Large phones in landscape and small tablets get a navigation rail.
  - Tablets get a permanent drawer and multi-column lists.
- **Keyboard shortcuts**: Ctrl+L (address), Ctrl+T (new tab), Ctrl+W (close tab), Ctrl+R or F5 (reload), Ctrl+D (bookmark), Ctrl+B (address and bookmarks panel), Ctrl+J (controls panel), Alt+← and Alt+→ (back and forward).

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
