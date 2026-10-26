/// <reference lib="dom" />
// Startup presentation (WM) and new-window handling (NW) tests.
import { expect, test } from '@playwright/test';
import {
  launch,
  openExam,
  pressKey,
  probe,
  waitForWindow,
  windowState,
  type Launched,
} from './helpers';

let l: Launched | null = null;

test.afterEach(async () => {
  await l?.close();
  l = null;
});

const mainBounds = (x: Launched) =>
  x.app.evaluate(({ BaseWindow, BrowserWindow }) => {
    const w = BaseWindow.getAllWindows().find((b) => !(b instanceof BrowserWindow))!;
    return { ...w.getBounds(), maximized: w.isMaximized(), fullscreen: w.isFullScreen() };
  });

test('WM-01 by default Lapsus opens in genuine OS fullscreen with its controls hidden', async () => {
  l = await launch({});
  await expect.poll(async () => (await windowState(l!)).fullscreen).toBe(true);
  await expect(l.ui.locator('.titlebar')).toHaveCount(0);
  await expect(l.ui.locator('.reveal-strip')).toBeVisible();
  const page = await openExam(l);
  await page.waitForTimeout(500);
  const s = await page.evaluate(() => ({
    w: innerWidth,
    h: innerHeight,
    sw: screen.width,
    sh: screen.height,
    apiFullscreen: !!document.fullscreenElement,
  }));
  expect(s.w).toBe(s.sw);
  expect(s.sh - s.h).toBeLessThanOrEqual(8);
  // Honest reporting: the window is fullscreen, but the page has not entered Fullscreen API mode.
  expect(s.apiFullscreen).toBe(false);
  // The hidden controls come back on hover, including the window buttons.
  await l.ui.hover('.reveal-strip');
  await expect(l.ui.locator('.titlebar')).toBeVisible();
});

test('WM-02 from the fullscreen start the window can be restored, resized and moved', async () => {
  l = await launch({});
  await expect.poll(async () => (await windowState(l!)).fullscreen).toBe(true);
  await openExam(l);
  await pressKey(l, 'page', 'F11');
  await expect.poll(async () => (await mainBounds(l!)).fullscreen).toBe(false);
  await expect(l.ui.locator('.titlebar')).toBeVisible();
  await l.app.evaluate(({ BaseWindow }) => {
    const w = BaseWindow.getAllWindows()[0]!;
    w.unmaximize();
    w.setBounds({ x: 40, y: 30, width: 1100, height: 720 });
  });
  await expect
    .poll(async () => {
      const b = await mainBounds(l!);
      return [b.width, b.height, b.maximized];
    })
    .toEqual([1100, 720, false]);
  const b = await mainBounds(l);
  expect(Math.abs(b.x - 40)).toBeLessThanOrEqual(12); // the window manager may nudge the position
  await l.app.evaluate(({ BaseWindow }) => BaseWindow.getAllWindows()[0]?.maximize());
  await expect.poll(async () => (await mainBounds(l!)).maximized).toBe(true);
});

test('WM-03 "Maximized window" mode starts maximized, not fullscreen', async () => {
  l = await launch({ windowMode: 'maximized' });
  await expect.poll(async () => (await mainBounds(l!)).maximized).toBe(true);
  expect((await mainBounds(l)).fullscreen).toBe(false);
  await expect(l.ui.locator('.titlebar')).toBeVisible();
});

test('WM-04 a page entering and leaving Fullscreen API mode returns to the fullscreen start', async () => {
  l = await launch({});
  await expect.poll(async () => (await windowState(l!)).fullscreen).toBe(true);
  const page = await openExam(l);
  await page.click('#fs-btn');
  await expect
    .poll(async () => (await probe(page)).some((e) => e.type === 'fullscreenchange' && e.fs))
    .toBe(true);
  expect((await windowState(l)).pageFullscreen).toBe(true);
  await pressKey(l, 'page', 'Escape');
  await expect.poll(async () => (await windowState(l!)).pageFullscreen).toBe(false);
  await page.waitForTimeout(400);
  expect((await windowState(l)).fullscreen).toBe(true);
  expect(await page.evaluate(() => !!document.fullscreenElement)).toBe(false);
});

test('NW-01 in "open in this window" mode a target=_blank link loads in the main window', async () => {
  l = await launch({ windowMode: 'maximized', newWindows: 'same-window' });
  const page = await openExam(l);
  await page.click('#blank-link');
  await expect.poll(() => page.url()).toContain('/popup');
  expect((await windowState(l)).popups).toBe(0);
});

test('NW-02 in "open in this window" mode window.open loads in the main window and returns null', async () => {
  l = await launch({ windowMode: 'maximized', newWindows: 'same-window' });
  const page = await openExam(l);
  const opened = page.evaluate(
    () =>
      new Promise<boolean>((resolve) => {
        document
          .getElementById('open-btn')!
          .addEventListener('click', () => resolve(false), { once: true });
        const original = window.open;
        window.open = (...args) => {
          const w = original.apply(window, args);
          resolve(!!w);
          return w;
        };
      }),
  );
  await page.click('#open-btn');
  expect(await opened).toBe(false);
  await waitForWindow(l.app, (u) => u.endsWith('/popup'));
  expect((await windowState(l)).popups).toBe(0);
});

test('NW-03 in "open in this window" mode an unrequested pop-up is still blocked', async () => {
  l = await launch({ windowMode: 'maximized', newWindows: 'same-window' });
  const page = await openExam(l, '?autopopup=1');
  await expect
    .poll(async () => (await probe(page)).find((e) => e.type === 'open-result')?.opened)
    .toBe(false);
  await expect(l.ui.locator('.band')).toContainText('blocked a pop-up');
  expect(page.url()).toContain('/exam');
});
