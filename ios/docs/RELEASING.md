# Releasing Sophia for iOS

Everything that doesn't need your Apple account is done: the project, the bundle identifier
`com.krlbryn.sophia`, version 0.1.0 (1), icon, launch screen, privacy manifest, permission texts,
export settings and a CI job that archives and uploads. The steps below need **your** Apple
Developer account, because only you can create certificates and app records. Nothing in this repo
contains a certificate, private key, provisioning profile or API key, and none should ever be committed.

> Steps marked **(you)** need your account. Everything else is already set up.

## 0. One-time account setup (you)

1. Join the [Apple Developer Program](https://developer.apple.com/programs/) (USD 99 per year).
   A free Apple ID can run Sophia on your own device for 7 days at a time (step 1), but can't use TestFlight.
2. Note your **Team ID**: developer.apple.com > Account > Membership details.
3. Register the bundle ID: Certificates, Identifiers & Profiles > Identifiers > + > App IDs > App >
   Description "Sophia", Bundle ID **Explicit** `com.krlbryn.sophia`. No extra capabilities are needed.
4. Create the app record: [App Store Connect](https://appstoreconnect.apple.com) > Apps > + > New App >
   iOS, name **Sophia**, primary language English, bundle ID `com.krlbryn.sophia`, SKU `sophia-ios`.
   If the name Sophia is taken on the App Store, use a variant such as "Sophia Study Browser" for the
   store name only; the home-screen name stays Sophia.

## 1. Development build on your own device

1. On a Mac: `brew install xcodegen`, then in `ios/`:
   ```sh
   cp Config/Signing.local.xcconfig.example Config/Signing.local.xcconfig
   # edit it: DEVELOPMENT_TEAM = <your Team ID>
   xcodegen && open Sophia.xcodeproj
   ```
   `Signing.local.xcconfig` is git-ignored.
2. Xcode > Settings > Accounts: add your Apple ID **(you)**.
3. Connect the iPhone or iPad, turn on Developer Mode on it (Settings > Privacy & Security >
   Developer Mode), pick it as the run destination and press Run. Xcode creates the development
   certificate and profile automatically (Signing & Capabilities shows "Automatically manage signing").

## 2. Signed archive (manual, from Xcode)

1. Choose **Any iOS Device (arm64)** as the destination.
2. Product > Archive. When it finishes, the Organizer opens.
3. **Distribute App > App Store Connect > Upload** sends it to TestFlight; **Export** keeps a signed `.ipa`.
   Raise `CURRENT_PROJECT_VERSION` in `Config/Sophia.xcconfig` before each upload (every build
   needs a new number); raise `MARKETING_VERSION` for each public release.

## 3. Signed archive and TestFlight upload from GitHub Actions

The **release** job in `.github/workflows/ios.yml` archives with automatic (cloud) signing, exports an
App Store `.ipa` and can upload it to TestFlight. It uses an App Store Connect API key, so no
certificate or profile is stored anywhere.

1. App Store Connect > Users and Access > Integrations > App Store Connect API > Team Keys > +.
   Name "Sophia CI", access **App Manager** (or Admin). Download the `.p8` file **(you)**. It can be
   downloaded only once; keep it safe and never commit it.
2. Repository > Settings > Secrets and variables > Actions > New repository secret, add **(you)**:

   | Secret | Value |
   |---|---|
   | `APPLE_TEAM_ID` | your Team ID |
   | `ASC_KEY_ID` | the key's Key ID |
   | `ASC_ISSUER_ID` | the Issuer ID shown above the keys list |
   | `ASC_KEY_P8_BASE64` | output of `base64 -i AuthKey_XXXX.p8` |

3. Actions > Sophia for iOS > Run workflow, tick **release** (and **upload_testflight** to send it
   to TestFlight). The build number is the workflow run number, so it always increases.

The release job checks for the four secrets first and stops with a clear message if any is missing.
It has not been run yet, because the secrets don't exist; expect to confirm the first run together.

## 4. TestFlight installation guide

**Internal testers** (up to 100 people on your App Store Connect team, no review):

1. After the upload, App Store Connect > Sophia > TestFlight shows the build as "Processing"
   (usually 5 to 30 minutes).
2. Answer the export compliance question if asked: Sophia uses only standard HTTPS, so choose
   "None of the algorithms mentioned above" (the app also declares `ITSAppUsesNonExemptEncryption = NO`).
3. TestFlight > Internal Testing > + create a group, add testers by Apple ID email, add the build.

**External testers** (up to 10,000, such as classmates):

1. TestFlight > External Testing > + create a group, add the build and fill in "What to Test".
2. The first build of each version goes through a short Beta App Review (usually under a day).
3. Share the group's **public link** or invite by email.

**On each tester's iPhone or iPad:**

1. Install **TestFlight** from the App Store.
2. Open the invitation email or public link and tap **Accept**, or in TestFlight tap **Redeem** and
   enter the code.
3. Tap **Install**. Sophia appears on the home screen; TestFlight offers updates as new builds arrive.
4. To send feedback, take a screenshot in Sophia and tap **Share Beta Feedback**, or use TestFlight > Sophia > Send Beta Feedback.
5. Builds expire after 90 days; install the newest one from TestFlight.

## 5. Real-device test checklist

Simulator tests can't cover everything. On at least one iPhone and one iPad:

- [ ] Sign in to Discipulus with a real student account, open a course, play a video, download a file.
- [ ] Swipe from each edge in portrait and landscape; confirm page scrolling, text selection and forms still work.
- [ ] Rotate while a panel is open; use Split View and Stage Manager on iPad, with windows of different widths.
- [ ] Hardware keyboard shortcuts and trackpad edge scroll on iPad.
- [ ] Airplane mode during a page load; turn it off and watch the page retry.
- [ ] A site asking for the camera (an online class), then revoke it in Settings > Privacy and Security.
- [ ] Study Assistant with your own OpenAI or Anthropic key: review screen, answer, copy, save.
- [ ] VoiceOver: open both panels with the rotor actions.
- [ ] Background the app for an hour and return; the session should still be signed in.
