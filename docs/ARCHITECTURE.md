# Architecture

Lapsus is an Electron 45 app written in TypeScript, with a React 19 interface built by
electron-vite and packaged by electron-builder.

## Processes and views

One `BaseWindow` holds two `WebContentsView`s and, when needed, a third tiny overlay:

| View         | Loads                                           | Session              | Preload                 | Can call Lapsus?                       |
| ------------ | ----------------------------------------------- | -------------------- | ----------------------- | -------------------------------------- |
| UI view      | `app://lapsus/index.html` (bundled React app)   | default              | `out/preload/index.cjs` | Yes, through one validated IPC channel |
| Page view    | The portal and other web pages                  | `persist:lapsus-web` | none                    | No                                     |
| Hint overlay | `app://lapsus/fullscreen-hint.html` (no script) | default              | none                    | No                                     |

All three are sandboxed with context isolation on and Node.js off. The page view sits above the
UI view inside the rectangle the UI reports (`view:set-bounds`), so Lapsus notices are drawn as
"bands" that push the page down rather than covering it. Pop-ups are separate `BrowserWindow`s
owned by the main window, in the same web session and with the same restrictions as the page view.

## Main process modules (`src/main`)

- `index.ts`: app lifecycle, single instance, protocol registration, store creation, startup.
- `window/main-window.ts`: the window, layout, navigation guard, fullscreen handling, pop-up
  policy, keyboard shortcuts (`before-input-event`), context menu.
- `window/shortcuts.ts`: pure key-to-action mapping.
- `ipc-router.ts`, `handlers.ts`: the single `lapsus:invoke` channel. Every request is checked
  against a zod schema in `src/shared/ipc.ts`, and the sender must be the UI view's top frame on
  the `app://lapsus` origin.
- `security/`: `app://` protocol with a strict CSP and path checks; permission policy.
- `browser/`: web session (user agent, permissions, certificate errors), downloads.
- `store/`: JSON stores with atomic writes; API keys encrypted with Windows DPAPI through
  Electron `safeStorage`; local monthly assistant usage counter.
- `ai/`: assistant service and providers (Anthropic SDK, OpenAI over `fetch`, offline demo).
  Keys never leave the main process.

## Shared code (`src/shared`)

Pure, unit-tested modules: URL policy (`url.ts`), settings schema, IPC schema, assistant input
preparation and answer schema, constants and types.

## Fullscreen and windows

- "Window" setting: `fullscreen` (default) puts the Lapsus window into OS fullscreen at start and
  hides Lapsus chrome; `maximized` opens a normal maximized window. Neither changes what a page
  sees through the Fullscreen API.
- When a page calls `requestFullscreen()`, the window goes OS-fullscreen and the page fills it from
  the first frame. A 3-second "Press Esc to exit" hint is drawn by the overlay view, so the page is
  never resized when it disappears. Leaving returns to the earlier window state.
- While a page is fullscreen, Lapsus shortcuts that need its own UI are passed to the page.
- Pop-ups need a real click or key press within 5 seconds (used once), like Chromium's transient
  activation. Others are blocked with a notice that can allow the site for the session.
- Nothing fakes or hides focus, visibility, fullscreen or window state from pages.

## Tests

- `tests/unit`: Vitest, pure modules and stores.
- `tests/e2e`: Playwright driving Electron against `tests/fixtures/portal-server.mjs`, a labelled
  local stand-in portal with an exam probe page (`/exam`) that records fullscreen, size,
  visibility and focus signals.
- `tests/e2e/raw`: a harness that runs Lapsus without DevTools attached, because an attached
  Playwright makes pages always report focus and visibility.
- CI on `windows-latest` also packages the app, checks fuses, scans for secrets, and installs,
  runs and uninstalls the real installer.
