# Security

Lapsus aims to be as safe as a mainstream browser for its narrow purpose. No software is
perfectly secure; this page explains the design, what was tested, and the risks that remain.

## Reporting a problem

Open a private security advisory on the GitHub repository, or contact the maintainer (KRLBRYN)
directly. Please do not post details publicly before a fix is available.

## Threat model

| Threat                                                          | Mitigation                                                                                                                                                                             |
| --------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A web page tries to reach Node.js, Electron or Lapsus internals | Page view and pop-ups: sandbox, context isolation, no Node, no preload, separate session. Verified by e2e test "remote pages get no Node.js, Electron or Lapsus APIs"                  |
| A compromised UI renderer calls powerful IPC                    | One channel; every request matched to an allowlisted channel and a strict zod schema; sender must be the UI view's top frame on `app://lapsus`. No generic file, shell or eval channel |
| Script injection in the UI                                      | Strict CSP (`default-src 'none'; script-src 'self'`); assistant output rendered as React text nodes only, never HTML; lint bans `dangerouslySetInnerHTML`                              |
| Navigation to local files or script URLs                        | `file:`, `javascript:`, `data:` and unknown schemes blocked for pages and pop-ups; `mailto:`/`tel:` open externally only after confirmation; links to other apps are blocked           |
| Plain HTTP and certificate problems                             | HTTP blocked except localhost, with a per-session "continue anyway"; Chromium's certificate checks are kept and errors shown with no bypass                                            |
| Phishing with embedded credentials (`https://user:pass@...`)    | Blocked                                                                                                                                                                                |
| Unrequested pop-ups or window spam                              | Pop-ups need a real click (Chromium's rule), at most 5 open, owned by the main window                                                                                                  |
| Site permissions                                                | Camera, microphone, notifications, location and clipboard reading ask first and last for the session; USB, serial, HID and other device access denied                                  |
| Dangerous downloads                                             | Filenames sanitized; executable types need extra confirmation; Lapsus never opens a downloaded program, only shows it in its folder                                                    |
| API key theft                                                   | Key stays in the main process, encrypted with DPAPI; never in the renderer bundle or logs; artifact scan in CI checks for keys                                                         |
| Prompt injection through study material                         | Material wrapped and neutralized as data; answers must match a strict JSON schema; answers are clearly labelled AI explanations and cannot act on the page                             |
| Tampering with the installed app                                | Electron fuses: RunAsNode off, NODE_OPTIONS off, inspect flags off, ASAR integrity validation on, load only from ASAR, cookie encryption on                                            |

## Exam integrity

Lapsus does not bypass or interfere with exam rules. It never fakes fullscreen, focus, visibility
or window state, never hides pop-up attempts from a page, never auto-answers or submits, and its
assistant never reads the page by itself. Whatever an exam can detect in Chrome, it can detect in
Lapsus.

## Checks and results

Results from 2026-10-26 on Linux (Xvfb, openbox) for commit 47d3aee and later:

| Check                                                      | Result                                                                                      |
| ---------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| ESLint                                                     | Pass                                                                                        |
| TypeScript (main, preload, renderer, tests)                | Pass                                                                                        |
| Unit tests (Vitest)                                        | 83 passed                                                                                   |
| End-to-end tests (Playwright + Electron)                   | 39 passed                                                                                   |
| Focus, visibility and crash harness (no DevTools attached) | 8 passed, 1 not run (minimizing a fullscreen window is refused by the Linux window manager) |
| Packaged app fuses (Linux build)                           | As configured above                                                                         |
| Artifact secret scan (`scripts/check-artifacts.mjs`)       | Pass; also confirmed it fails on a planted fake key                                         |
| Packaged app smoke test (Linux build)                      | Pass                                                                                        |

The Windows results come from the CI workflow on `windows-latest` (same tests plus installer
install, metadata check and uninstall). See the Actions tab for each run.

## Residual risks

- The app is **not code-signed** yet. Windows SmartScreen may warn, and users must verify
  checksums. See [RELEASE.md](RELEASE.md).
- Lapsus relies on Electron's Chromium for web security. Security fixes reach users only when a
  new Lapsus version with a newer Electron is released, and v0.1.0 has no automatic updates.
- No Safe Browsing service: Lapsus does not warn about known phishing or malware sites the way
  Chrome and Edge do.
- AI providers receive the text you choose to send. Redaction is pattern-based and can miss
  personal details written in unusual ways.
- The real Discipulus portal could not be tested from the build environment.
