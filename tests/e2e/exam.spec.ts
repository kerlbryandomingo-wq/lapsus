/// <reference lib="dom" />
// Exam compatibility tests (IDs match the audit's test matrix). They run against the local
// exam probe page, which records the fullscreen, size, visibility and focus signals a web
// exam can observe. Lapsus must report these truthfully and behave like Chrome.
import { expect, test } from '@playwright/test';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
  clearProbe,
  launch,
  openExam,
  pressKey,
  probe,
  waitForWindow,
  windowState,
  type Launched,
} from './helpers';

let l: Launched;

test.beforeEach(async () => {
  l = await launch();
});

test.afterEach(async () => {
  await l.close();
});

async function enterFullscreen(page: import('@playwright/test').Page) {
  await page.click('#fs-btn');
  await expect
    .poll(async () => (await probe(page)).some((e) => e.type === 'fullscreenchange' && e.fs))
    .toBe(true);
  await expect.poll(async () => (await windowState(l)).fullscreen).toBe(true);
}

async function hintUrl(): Promise<string> {
  return l.app.evaluate(({ webContents }) => {
    const wc = webContents
      .getAllWebContents()
      .find((w) => w.getURL().includes('fullscreen-hint.html'));
    return wc ? wc.getURL() : '';
  });
}

test('FS-01 page fullscreen is genuine and the page gets the full screen with no late resize', async () => {
  const page = await openExam(l);
  await enterFullscreen(page);
  const state = await windowState(l);
  expect(state).toMatchObject({ fullscreen: true, pageFullscreen: true });
  await expect.poll(hintUrl).toContain('#exit');
  // Let the 3 s exit hint come and go; the page must not be resized when it disappears.
  const settledAt = (await probe(page)).at(-1)!.t;
  await page.waitForTimeout(3600);
  const after = (await probe(page)).filter((e) => e.t > settledAt + 500);
  expect(after.filter((e) => e.type === 'resize')).toEqual([]);
  const size = await page.evaluate(() => ({
    fs: !!document.fullscreenElement,
    w: innerWidth,
    h: innerHeight,
    sw: screen.width,
    sh: screen.height,
  }));
  expect(size.fs).toBe(true);
  expect(size.w).toBe(size.sw);
  expect(size.h).toBe(size.sh);
});

test('FS-02 fullscreen without a click is refused and Lapsus stays as it was', async () => {
  const page = await openExam(l, '?autofs=1');
  await expect
    .poll(async () => (await probe(page)).map((e) => e.type))
    .toContain('request-rejected');
  expect((await probe(page)).map((e) => e.type)).toContain('fullscreenerror');
  expect(await windowState(l)).toMatchObject({ fullscreen: false, pageFullscreen: false });
  await expect(l.ui.locator('.titlebar')).toBeVisible();
  // Recovery: a real click still works afterwards.
  await enterFullscreen(page);
});

test('FS-03 Esc leaves page fullscreen and window fullscreen, with one change event each way', async () => {
  const page = await openExam(l);
  await enterFullscreen(page);
  await pressKey(l, 'page', 'Escape');
  await expect.poll(async () => (await windowState(l)).fullscreen).toBe(false);
  expect((await windowState(l)).pageFullscreen).toBe(false);
  const changes = (await probe(page)).filter((e) => e.type === 'fullscreenchange');
  expect(changes.map((e) => e.fs)).toEqual([true, false]);
  await expect(l.ui.locator('.titlebar')).toBeVisible();
});

test('FS-04 leaving page fullscreen returns to F11 fullscreen when the student was already in it', async () => {
  const page = await openExam(l);
  await pressKey(l, 'page', 'F11');
  await expect.poll(async () => (await windowState(l)).fullscreen).toBe(true);
  await enterFullscreen(page);
  await pressKey(l, 'page', 'Escape');
  await expect.poll(async () => (await windowState(l)).pageFullscreen).toBe(false);
  await page.waitForTimeout(400);
  expect((await windowState(l)).fullscreen).toBe(true);
  await pressKey(l, 'page', 'F11');
  await expect.poll(async () => (await windowState(l)).fullscreen).toBe(false);
});

test('FS-05 F11 hides Lapsus chrome so the page fills the screen, and restores it after', async () => {
  const page = await openExam(l);
  await pressKey(l, 'page', 'F11');
  await expect.poll(async () => (await windowState(l)).fullscreen).toBe(true);
  await expect(l.ui.locator('.titlebar')).toHaveCount(0);
  await expect(l.ui.locator('.sidebar')).toHaveCount(0);
  await page.waitForTimeout(500);
  const size = await page.evaluate(() => ({
    w: innerWidth,
    h: innerHeight,
    sw: screen.width,
    sh: screen.height,
  }));
  expect(size.w).toBe(size.sw);
  expect(size.sh - size.h).toBeLessThanOrEqual(8);
  // Esc goes to the page in F11 mode (as in Chrome) and does not leave fullscreen.
  await pressKey(l, 'page', 'Escape');
  await page.waitForTimeout(300);
  expect((await windowState(l)).fullscreen).toBe(true);
  expect((await probe(page)).some((e) => e.type === 'keydown' && e.key === 'Escape')).toBe(true);
  await pressKey(l, 'page', 'F11');
  await expect.poll(async () => (await windowState(l)).fullscreen).toBe(false);
  await expect(l.ui.locator('.titlebar')).toBeVisible();
  await expect(l.ui.locator('.sidebar')).toBeVisible();
});

test('FS-06 navigation or reload ends page fullscreen cleanly, and the page can enter again', async () => {
  let page = await openExam(l);
  await enterFullscreen(page);
  await page.click('#reload-link');
  await expect.poll(async () => (await windowState(l)).fullscreen).toBe(false);
  expect((await windowState(l)).pageFullscreen).toBe(false);
  page = await openExam(l);
  await enterFullscreen(page);
  await pressKey(l, 'page', 'F5');
  await expect.poll(async () => (await windowState(l)).fullscreen).toBe(false);
  await page.waitForFunction(() =>
    Array.isArray((window as unknown as { __probe?: unknown[] }).__probe),
  );
  await enterFullscreen(page);
});

test('FS-07 Lapsus UI shortcuts do not steal focus from a fullscreen page', async () => {
  const page = await openExam(l);
  await enterFullscreen(page);
  await page.waitForTimeout(300);
  await clearProbe(page);
  for (const key of ['K', 'L', 'F', 'J', 'H']) await pressKey(l, 'page', key, ['control']);
  await page.waitForTimeout(400);
  const events = await probe(page);
  expect(events.filter((e) => e.type === 'blur')).toEqual([]);
  expect(
    events.filter((e) => e.type === 'keydown').map((e) => (e.key ?? '').toLowerCase()),
  ).toEqual(['k', 'l', 'f', 'j', 'h']);
  expect(await windowState(l)).toMatchObject({ fullscreen: true, pageFullscreen: true });
  await expect(l.ui.locator('.assistant')).toHaveCount(0);
  // Out of fullscreen, Ctrl+K opens the assistant as before.
  await pressKey(l, 'page', 'Escape');
  await expect.poll(async () => (await windowState(l)).fullscreen).toBe(false);
  await pressKey(l, 'page', 'K', ['control']);
  await expect(l.ui.locator('.assistant')).toBeVisible();
});

test('FS-08 a download does not end page fullscreen', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'lapsus-dl-'));
  await l.app.evaluate(({ session }, target) => {
    session
      .fromPartition('persist:lapsus-web')
      .on('will-download', (_e, item) => item.setSavePath(`${target}/${item.getFilename()}`));
  }, dir);
  const page = await openExam(l);
  await enterFullscreen(page);
  await page.evaluate(() => {
    const a = document.createElement('a');
    a.href = '/notes.txt';
    a.download = '';
    document.body.append(a);
    a.click();
  });
  await expect
    .poll(() =>
      l.ui.evaluate(async () =>
        (
          (await (
            window as unknown as { lapsus: { invoke: (c: string) => Promise<{ state: string }[]> } }
          ).lapsus.invoke('downloads:list')) as { state: string }[]
        ).map((d) => d.state),
      ),
    )
    .toEqual(['completed']);
  expect(await windowState(l)).toMatchObject({ fullscreen: true, pageFullscreen: true });
});

test('FS-09 blocked pop-ups and blocked links do not end page fullscreen', async () => {
  const page = await openExam(l);
  await enterFullscreen(page);
  await page.waitForTimeout(3300); // let the exit hint go
  await page.evaluate(() => window.open('/popup', 'x'));
  await expect.poll(hintUrl).toContain('#popup-blocked');
  await page.evaluate(() => document.getElementById('app-link')!.click());
  await page.waitForTimeout(400);
  expect(await windowState(l)).toMatchObject({ fullscreen: true, pageFullscreen: true, popups: 0 });
  expect(page.url()).toContain('/exam');
});

test('FS-10 a crash during fullscreen leaves fullscreen and offers recovery', async () => {
  const page = await openExam(l);
  await enterFullscreen(page);
  await l.app.evaluate(({ webContents }) => {
    webContents
      .getAllWebContents()
      .find((wc) => wc.getURL().includes('/exam'))
      ?.forcefullyCrashRenderer();
  });
  await expect.poll(async () => (await windowState(l)).fullscreen).toBe(false);
  await expect(l.ui.locator('.page-error h2')).toHaveText('This page crashed');
});

test('WIN-01 the page reports focus truthfully when a pop-up takes focus and when Lapsus gets it back', async () => {
  const page = await openExam(l);
  // Playwright normally makes every page think it has focus; switch that off to see real events.
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setFocusEmulationEnabled', { enabled: false });
  await page.click('#answer');
  await expect.poll(() => page.evaluate(() => document.hasFocus())).toBe(true);
  await clearProbe(page);
  await page.click('#open-btn');
  const popup = await waitForWindow(l.app, (u) => u.endsWith('/popup'));
  // Windows may refuse to bring a window to the front for a process that is not in the
  // foreground (as on a CI runner), so ask for focus explicitly and check what the OS did.
  const focus = () =>
    l.app.evaluate(({ BaseWindow, BrowserWindow }) => {
      const main = BaseWindow.getAllWindows().find((x) => !(x instanceof BrowserWindow))!;
      const pop = BrowserWindow.getAllWindows()[0];
      return { main: main.isFocused(), popup: pop?.isFocused() ?? false };
    });
  await l.app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0]?.focus());
  await expect
    .poll(async () => (await focus()).popup, { timeout: 5000 })
    .toBe(true)
    .catch(() => {});
  const os = await focus();
  console.log(`WIN-01 OS focus after pop-up: ${JSON.stringify(os)}`);
  // The rule under test: the page says it has focus exactly when its window does.
  await expect.poll(() => page.evaluate(() => document.hasFocus())).toBe(os.main);
  // The blur event itself is checked without Playwright attached (tests/e2e/raw, WIN-01c):
  // with Playwright's focus emulation switched off mid-test, Chromium sometimes skips it.

  await popup.close();
  await l.app.evaluate(({ BaseWindow, BrowserWindow }) =>
    BaseWindow.getAllWindows()
      .find((x) => !(x instanceof BrowserWindow))
      ?.focus(),
  );
  const back = await focus();
  console.log(`WIN-01 OS focus after closing pop-up: ${JSON.stringify(back)}`);
  await expect.poll(() => page.evaluate(() => document.hasFocus())).toBe(back.main);
});

// WIN-02 (minimize and restore) runs in tests/e2e/raw/visibility.mjs without Playwright attached,
// because an attached DevTools client keeps the page reporting "visible" while minimized.

test('WIN-03 maximize, unmaximize and resize keep the page filling its area', async () => {
  const page = await openExam(l);
  const pageMatchesView = async () => {
    const view = await l.app.evaluate(({ webContents }) => {
      const wc = webContents.getAllWebContents().find((w) => w.getURL().includes('/exam'))!;
      // The page view's size is what the page should report as its viewport.
      return wc.executeJavaScript('({ w: innerWidth, h: innerHeight })') as Promise<{
        w: number;
        h: number;
      }>;
    });
    const ui = await l.ui.evaluate(() => {
      const r = document
        .querySelector('.portal-viewport, [data-viewport]')
        ?.getBoundingClientRect();
      return r ? { w: Math.round(r.width), h: Math.round(r.height) } : null;
    });
    return ui ? Math.abs(view.w - ui.w) <= 1 && Math.abs(view.h - ui.h) <= 1 : view.w > 0;
  };
  await l.app.evaluate(({ BaseWindow }) => BaseWindow.getAllWindows()[0]?.unmaximize());
  await expect.poll(pageMatchesView).toBe(true);
  await l.app.evaluate(({ BaseWindow }) => BaseWindow.getAllWindows()[0]?.setSize(1000, 700));
  await expect.poll(pageMatchesView).toBe(true);
  await l.app.evaluate(({ BaseWindow }) => BaseWindow.getAllWindows()[0]?.maximize());
  await expect.poll(pageMatchesView).toBe(true);
  expect(page.url()).toContain('/exam');
});

test('POP-01 window.open after a click opens an owned, sandboxed pop-up', async () => {
  const page = await openExam(l);
  await page.click('#open-btn');
  const popup = await waitForWindow(l.app, (u) => u.endsWith('/popup'));
  expect((await probe(page)).find((e) => e.type === 'open-result')?.opened).toBe(true);
  expect(
    await popup.evaluate(() => typeof (window as unknown as { require?: unknown }).require),
  ).toBe('undefined');
  const owned = await l.app.evaluate(({ BrowserWindow }) =>
    BrowserWindow.getAllWindows().every((b) => b.getParentWindow() !== null),
  );
  expect(owned).toBe(true);
});

test('POP-02 window.open without a click is blocked with a notice, and the site can be allowed', async () => {
  let page = await openExam(l, '?autopopup=1');
  await expect
    .poll(async () => (await probe(page)).find((e) => e.type === 'open-result')?.opened)
    .toBe(false);
  await expect(l.ui.locator('.band')).toContainText('blocked a pop-up');
  expect((await windowState(l)).popups).toBe(0);
  await l.ui.click('.band__action');
  page = await openExam(l, '?autopopup=2');
  await expect
    .poll(async () => (await probe(page)).find((e) => e.type === 'open-result')?.opened)
    .toBe(true);
  await waitForWindow(l.app, (u) => u.endsWith('/popup'));
});

test('POP-03 a target=_blank link opens an owned pop-up', async () => {
  const page = await openExam(l);
  await page.click('#blank-link');
  await waitForWindow(l.app, (u) => u.endsWith('/popup'));
  expect((await windowState(l)).popups).toBe(1);
});

test('POP-04 a pop-up opened from a fullscreen page stays in front of it', async () => {
  const page = await openExam(l);
  await enterFullscreen(page);
  await page.click('#open-btn');
  await waitForWindow(l.app, (u) => u.endsWith('/popup'));
  const info = await l.app.evaluate(({ BrowserWindow, BaseWindow }) => {
    const main = BaseWindow.getAllWindows().find((x) => !(x instanceof BrowserWindow))!;
    const pop = BrowserWindow.getAllWindows()[0]!;
    return { parentIsMain: pop.getParentWindow()?.id === main.id, visible: pop.isVisible() };
  });
  expect(info).toEqual({ parentIsMain: true, visible: true });
});

test('POP-05 file: and data: pop-ups are blocked even after a click', async () => {
  const page = await openExam(l);
  for (const target of ['file:///etc/passwd', 'data:text/html,<h1>x</h1>']) {
    await page.click('#answer');
    await page.evaluate((t) => void window.open(t), target);
  }
  await page.waitForTimeout(500);
  expect((await windowState(l)).popups).toBe(0);
});

test('POP-06 Ctrl+N and Ctrl+T do not open windows or tabs', async () => {
  await openExam(l);
  await pressKey(l, 'page', 'N', ['control']);
  await pressKey(l, 'page', 'T', ['control']);
  await pressKey(l, 'page', 'N', ['control', 'shift']);
  await new Promise((r) => setTimeout(r, 500));
  expect((await windowState(l)).popups).toBe(0);
  expect(l.app.windows().filter((w) => w.url().startsWith('http')).length).toBe(1);
});

test('POP-07 links to other apps are blocked and nothing is launched', async () => {
  const page = await openExam(l);
  const launched = await l.app.evaluate(({ shell }) => {
    const calls: string[] = [];
    (globalThis as unknown as { __ext: string[] }).__ext = calls;
    const original = shell.openExternal.bind(shell);
    shell.openExternal = async (u: string, o?: Electron.OpenExternalOptions) => {
      calls.push(u);
      return original(u, o);
    };
    return calls.length;
  });
  expect(launched).toBe(0);
  await page.evaluate(() => document.getElementById('app-link')!.click());
  await expect(l.ui.locator('.band')).toContainText('blocked a link');
  expect(await l.app.evaluate(() => (globalThis as unknown as { __ext: string[] }).__ext)).toEqual(
    [],
  );
  expect(page.url()).toContain('/exam');
  await expect(l.ui.locator('.page-error')).toHaveCount(0);
});

test('POP-08 at most five pop-ups are open at once', async () => {
  const page = await openExam(l);
  for (let i = 0; i < 6; i++) {
    await page.click('#blank-link');
    await page.waitForTimeout(250);
  }
  await expect.poll(async () => (await windowState(l)).popups).toBe(5);
  await expect(l.ui.locator('.band')).toContainText('Too many pop-up windows');
});
