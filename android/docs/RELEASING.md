# Releasing Sophia for Android

Release builds are minified with R8 and signed with an upload key you keep private. **Never commit a keystore, a `keystore.properties` file, or any password.** `.gitignore` already excludes `*.jks`, `*.keystore` and `keystore.properties`.

## 1. Create an upload key (once)

```bash
keytool -genkeypair -v -keystore sophia-upload.jks -storetype PKCS12 \
  -alias sophia -keyalg RSA -keysize 4096 -validity 10000
```

Keep the file and its passwords somewhere safe, such as a password manager. If you publish on Google Play, enrol in Play App Signing. Google then holds the app signing key, and this key is only for uploads.

## 2a. Sign locally

Create `android/keystore.properties` (it is git-ignored):

```properties
storeFile=/absolute/path/to/sophia-upload.jks
storePassword=…
keyAlias=sophia
keyPassword=…
```

Then run `./gradlew :app:assembleRelease :app:bundleRelease`.

## 2b. Sign in GitHub Actions

Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `SOPHIA_KEYSTORE_BASE64` | `base64 -w0 sophia-upload.jks` |
| `SOPHIA_KEYSTORE_PASSWORD` | keystore password |
| `SOPHIA_KEY_ALIAS` | `sophia` |
| `SOPHIA_KEY_PASSWORD` | key password |

With the secrets set, CI names its outputs `Sophia-<version>-release-release-signed.apk` and `.aab`.

Without them, CI signs with a throwaway key made for that run only, and names the files `…-release-test-signed…`. Those builds are for testing only. Don't publish them, because a later build can't update them.

## 3. Version

Set `sophia.versionName` and `sophia.versionCode` in `android/gradle.properties`, or pass `-Psophia.versionName=… -Psophia.versionCode=…`. Increase `versionCode` for every release.

## 4. Keep the R8 mapping

CI uploads `Sophia-<version>-mapping.txt` next to the APK. Keep it with each release, so crash stack traces can be decoded. Upload it to Play Console with the AAB.
