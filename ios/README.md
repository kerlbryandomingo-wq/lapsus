# Sophia for iOS and iPadOS

**Browse. Learn. Understand.** Sophia is an educational browser and AI study companion by KRLBRYN
for AMA Computer College students. This is the native iPhone and iPad edition, built with Swift,
SwiftUI and WKWebView. It is the same product as Sophia for Android (`android/`), adapted to iOS.

| | |
|---|---|
| App name | Sophia |
| Developer | KRLBRYN |
| Bundle identifier | `com.krlbryn.sophia` |
| Version | 0.1.0 (build 1), set in `Config/Sophia.xcconfig` |
| Minimum system | iOS 17 and iPadOS 17 (iPhone XS / XR and newer, iPad 6th generation and newer) |
| Portal | https://discipulusv2.amasystem.net/ |

## What it does

- **Launches straight into Discipulus**, full screen. No dashboard, no toolbar, nothing drawn over the page.
- **Swipe from the left edge** for the address bar (web address or search, HTTPS only) and the
  bookmarks Discipulus, LMS and Aurora Login. LMS and Aurora show "Not set up yet" until you enter
  their addresses in Settings > Bookmarks, because those addresses aren't published anywhere yet.
- **Swipe from the right edge** for Back, Forward, Reload and Settings. Back and Forward are disabled
  when there is nowhere to go.
- The grey belongs only to the open panel. Tap the page beside it, or swipe it back toward its edge,
  to close it. The page is never tinted or dimmed.
- **Downloads** with name, type, size, progress, cancel, preview, share and Files app access.
- **History** stored on the device with search, single delete and clear by time range.
- **Settings** for appearance, homepage, search engine, JavaScript, pop-ups, downloads, privacy
  (clear history, cookies, cache, website permissions), the Study Assistant, accessibility and About.
- **Study Assistant** for explanations, summaries, study notes, practice questions and flashcards,
  with OpenAI, Anthropic, any OpenAI-compatible service or an offline demo. API keys live in the
  Keychain. You see exactly what will be sent (with passwords, keys, emails and ID numbers removed)
  before anything leaves the device. Select text on a page and choose **Ask Sophia** to start.

### iPhone and iPad

The same interaction everywhere, tuned for each device:

| | iPhone | iPad |
|---|---|---|
| Address panel | 300 pt (82% of narrow screens) | 380 pt, plus recent pages |
| Controls panel | 104 pt, 76 pt tiles | 128 pt, 92 pt tiles |
| Edge strip that starts a swipe | 24 pt | 32 pt |
| Settings | List that pushes each page | Sidebar with the page beside it |
| Study Assistant | One screen at a time | Material and answer side by side |
| Windows | One | Several (Split View, Slide Over, Stage Manager), each with its own page |
| Input | Touch | Touch, hardware keyboard shortcuts, trackpad (pointer at the edge plus two-finger sideways scroll) |

People who can't or prefer not to swipe can open the panels with VoiceOver actions on the page,
keyboard shortcuts (Command-L, Command-Shift-B) or optional edge buttons (Settings > Accessibility).

## Project layout

```
ios/
  project.yml              XcodeGen spec (generates Sophia.xcodeproj)
  Config/                  Shared build settings; your signing team goes in Signing.local.xcconfig
  SophiaKit/               Swift package: URL rules, swipe maths, Study Assistant, AI providers
  Sophia/                  The app (SwiftUI, MVVM)
    App/                   Entry point, shared services, network monitor, UI-test page
    Browser/               BrowserModel, WKWebView delegates, edge swipes, panels, browser screen
    Downloads/ History/ Settings/ Assistant/ Data/ Design/
    Resources/             Asset catalog (icon, launch mark, colours), privacy manifest
  SophiaTests/             Unit tests (settings, history, panels, errors, assistant flow, Keychain)
  SophiaUITests/           UI tests on iPhone and iPad, portrait and landscape, with screenshots
  scripts/                 Icon renderer, CI simulator picker
  docs/                    Releasing, TestFlight, App Store, privacy policy, design notes
```

SophiaKit uses Foundation only, so its 30 tests also run on Linux (`swift test`).

## Build and run

You need a Mac with the latest Xcode (16 or newer) and [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```sh
brew install xcodegen
cd ios
xcodegen               # creates Sophia.xcodeproj from project.yml
open Sophia.xcodeproj
```

Choose an iPhone or iPad simulator and press Run. Re-run `xcodegen` after adding or removing files.

To run on your own iPhone or iPad, see [docs/RELEASING.md](docs/RELEASING.md#1-development-build-on-your-own-device).

### Tests

```sh
cd ios/SophiaKit && swift test                                     # shared logic
cd ios && xcodebuild test -project Sophia.xcodeproj -scheme Sophia \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro'         # app unit + UI tests
```

UI tests launch with `-ui-testing`, which uses an in-memory store, a separate preferences and
Keychain domain, and a built-in test page, so they never touch your data or need the network.
`testLivePortalLoads` opens the real Discipulus portal and is skipped when it can't be reached.

### Continuous integration

`.github/workflows/ios.yml` runs on a GitHub macOS runner for every pull request that touches `ios/`:
SophiaKit tests, app unit tests, UI tests on an iPhone and an iPad simulator side by side, an
unsigned Release build for devices, and a zipped simulator app. Screenshots of each browser state are
pushed to the `sophia-ios-screens` branch. Run it by hand with **all_devices** to add a small iPhone
and an iPad mini.

Signing, TestFlight and App Store steps need your Apple Developer account. They are written up in
[docs/RELEASING.md](docs/RELEASING.md) and [docs/APP_STORE.md](docs/APP_STORE.md).
