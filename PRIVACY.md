# Privacy

Lapsus has no accounts, analytics, telemetry, crash reporting or advertising. It does not check
for updates in version 0.1.0.

## Stored on your computer only

Everything below is kept in `%APPDATA%\Lapsus` and never sent anywhere by Lapsus:

- Settings, bookmarks, browsing history (can be turned off), recent downloads list.
- Website data (cookies, cache, site storage) in the `lapsus-web` profile, like any browser.
- Your assistant API key, encrypted with Windows DPAPI (tied to your Windows account) through
  Electron `safeStorage`. Lapsus refuses to store a key if encryption is unavailable.
- A count of assistant requests this month, used for your monthly limit.

Settings > Clear browsing data can remove cookies, cache, history, downloads and saved keys.
Uninstalling keeps `%APPDATA%\Lapsus` unless you delete it.

## What leaves your computer

- **Web pages you visit** are loaded directly from those sites, as in any browser. Lapsus sends a
  standard Chrome-like user agent without Electron or Lapsus identifiers.
- **Study Assistant**, only when you press Send after reviewing the text. Lapsus sends the text
  you chose and your optional question to the provider you configured (OpenAI or Anthropic), using
  your key. Before sending, it removes things that look like passwords, API keys, tokens and email
  addresses, strips hidden control characters, and cuts the text at 6,000 characters. It never
  sends whole pages, cookies, your history, your name or your portal login. Each question is sent
  on its own, without earlier questions. The provider's own privacy terms apply to what you send.
  Demo mode sends nothing.
- **Spell-check dictionaries**: Chromium's spell checker in Electron may download a dictionary
  file for your language from Google's servers the first time it is needed.

## Children and school accounts

Lapsus does not collect anything, but the portal and AI providers you use have their own terms.
Never paste your password or other personal details into the assistant.
