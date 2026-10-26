# Lapsus

A focused Windows browser for the AMA Discipulus student portal
(<https://discipulusv2.amasystem.net/>), made by KRLBRYN.

Lapsus is an independent student project. It is not made, endorsed or supported by AMA
Education System or the makers of Discipulus.

## What it does

- Opens the Student Portal at start, in genuine fullscreen with the Lapsus controls hidden
  (hover the top edge to show them, F11 to leave). A maximized window is one setting away.
- Browses other secure (HTTPS) sites too, or only the portal when "Portal-only mode" is on.
- Bookmarks, history, downloads, find in page, zoom, light and dark themes, and focus mode.
- Pop-ups open only after a click and always stay in front of Lapsus. Optionally, new-window
  links can open in the main window instead.
- A Study Assistant (Ctrl+K) explains course material you choose to send. It is for studying:
  it never reads the page by itself, never fills in or submits answers, and you review the
  exact text before anything leaves your computer. It works with your own OpenAI or Anthropic
  API key, or an offline demo mode.

## Install

Download `Lapsus-Setup-x64.exe` (installer, no admin rights needed) or
`Lapsus-Portable-x64.exe` (runs without installing) from the
[Releases page](https://github.com/kerlbryandomingo-wq/lapsus/releases). Check the file against
`SHA256SUMS.txt`:

```powershell
Get-FileHash .\Lapsus-Setup-x64.exe -Algorithm SHA256
```

Requires Windows 10 22H2 or Windows 11, 64-bit. The app is not code-signed yet, so Windows
SmartScreen may show "Windows protected your PC". Choose **More info** and **Run anyway** only if
the checksum matches. See [RELEASE.md](RELEASE.md).

## Develop

Requires Node.js 22.12 or later.

```bash
npm ci
npm run dev          # run with hot reload
npm run lint
npm run typecheck
npm test             # unit tests (Vitest)
npm run build
npm run test:e2e     # end-to-end tests (Playwright + Electron) against a local stand-in portal
npm run package:win  # Windows installer and portable app (run on Windows)
```

On Linux, run the end-to-end tests as a non-root user with a display (for example Xvfb and a
window manager); Electron's sandbox refuses to run as root.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Security](SECURITY.md)
- [Privacy](PRIVACY.md)
- [Releasing and code signing](RELEASE.md)
