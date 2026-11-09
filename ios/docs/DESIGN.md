# Sophia for iOS: design notes

The shared specification for how Sophia looks and behaves on iPhone and iPad, matching Sophia for
Android (`android/app/.../ui/browser/GesturePanels.kt`) and the reference screenshots.

## Identity

- White stylised S on a solid maroon circle (`#7A1B1B`), no AMA logo, no tagline in the icon.
  The S path is the same as Android's `ic_sophia_mark.xml`; `scripts/make_icons.py` renders the PNGs
  and `SophiaMark` draws it as a vector in the app.
- The home-screen icon is a full maroon square with the white S: iOS requires opaque square icons
  and applies its own rounded mask. The circular mark is used on the launch screen, in Settings and in About.
- Palette: maroon, white, neutral grey. Sophia's own screens follow light or dark mode; web pages keep
  their own colours.

## Three browser states

| State | What is drawn over the page |
|---|---|
| Normal | Nothing. (A 3 pt maroon bar shows only while a page loads.) |
| Left panel | Grey panel (white 50% grey at 95% opacity) on the left; the rest of the page uncovered and undimmed |
| Right panel | Grey panel on the right; the rest uncovered and undimmed |

A transparent layer beside an open panel catches the tap that closes it. It has no colour.

## Gestures

| Gesture | Result |
|---|---|
| Touch starts within 24 pt (iPad 32 pt, never over 8% of the width) of the left edge and moves right, at least 1.8x more sideways than vertically | Left panel follows the finger |
| Same from the right edge, moving left | Right panel follows the finger |
| Release past 40% of the panel width, or flick faster than 550 pt/s | Panel opens; otherwise it slides back |
| Drag an open panel toward its edge | Closes, following the finger |
| Tap beside the panel | Closes |

The recogniser lives on a host view around the WKWebView, and the web view's own scrolling waits
only for touches that start in the edge strip. Everything else reaches the page untouched, so
scrolling, pinch zoom, text selection, long-press menus and forms behave as in Safari. WKWebView's
own back/forward edge swipe is off because the edges belong to the panels; Back and Forward are in
the right panel. iPhone and iPad have no system gestures on the left and right edges; the top and
bottom edges (Notification Centre, Home) are never used.

Alternatives: VoiceOver custom actions on the page, keyboard shortcuts, two-finger trackpad scroll
with the pointer at an edge, and optional edge buttons in Settings > Accessibility.

## Sizes

All numbers live in `SophiaKit/Sources/SophiaKit/EdgeGesture.swift` and are unit-tested.

| | Compact width (iPhone, narrow iPad windows) | Regular width (iPad) |
|---|---|---|
| Address panel | min(300, 82% of width) | min(380, 50% of width) |
| Controls panel | 104 pt, tiles 76 pt | 128 pt, tiles 92 pt |
| Extra content | | Recent pages in the address panel |

Size classes come from the window, not the device, so an iPad window in Split View or a small
Stage Manager window gets the compact layout, and changes live as the window is resized.
