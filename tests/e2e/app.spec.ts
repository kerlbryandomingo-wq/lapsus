/// <reference lib="dom" />
import { expect, test } from '@playwright/test';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { launch, portalPage, waitForWindow, type Launched } from './helpers';

let l: Launched;

test.beforeEach(async () => {
  l = await launch();
});

test.afterEach(async () => {
  await l.close();
});

test('starts, loads the portal, and shows Lapsus chrome', async () => {
  const page = await portalPage(l);
  await expect(page.locator('h2', { hasText: 'My Courses' })).toBeVisible();
  await expect(l.ui.locator('.titlebar')).toBeVisible();
  await expect(l.ui.locator('.address__input')).toHaveValue(/127\.0\.0\.1/);
  const title = await l.app.evaluate(({ BaseWindow }) => BaseWindow.getAllWindows()[0]?.getTitle());
  expect(title).toBe('Lapsus');
});

test('remote pages get no Node.js, Electron or Lapsus APIs', async () => {
  const page = await portalPage(l);
  const exposed = await page.evaluate(() => ({
    lapsus: typeof (window as unknown as { lapsus?: unknown }).lapsus,
    require: typeof (window as unknown as { require?: unknown }).require,
    process: typeof (window as unknown as { process?: unknown }).process,
    electron: typeof (window as unknown as { electron?: unknown }).electron,
  }));
  expect(exposed).toEqual({ lapsus: 'undefined', require: 'undefined', process: 'undefined', electron: 'undefined' });
});

test('app UI is served with a restrictive CSP and the bridge rejects bad requests', async () => {
  const csp = await l.ui.evaluate(async () => (await fetch(location.href)).headers.get('content-security-policy'));
  expect(csp).toContain("default-src 'none'");
  expect(csp).toContain("script-src 'self'");
  expect(csp).not.toContain('unsafe-eval');

  const results = await l.ui.evaluate(async () => {
    const api = (window as unknown as { lapsus: { invoke: (c: string, a?: unknown) => Promise<unknown> } }).lapsus;
    const attempt = (c: string, a?: unknown) =>
      api.invoke(c, a).then(
        () => 'accepted',
        () => 'rejected',
      );
    return {
      unknown: await attempt('fs:read', { path: 'C:/Windows/win.ini' }),
      badArgs: await attempt('nav:go', { input: 1 }),
      extraKey: await attempt('settings:update', { nodeIntegration: true }),
      good: await attempt('app:info'),
    };
  });
  expect(results).toEqual({ unknown: 'rejected', badArgs: 'rejected', extraKey: 'rejected', good: 'accepted' });
});

test('blocks file:, javascript: and plain http navigation', async () => {
  const page = await portalPage(l);
  await page.click('#evil-js');
  await page.waitForTimeout(300);
  expect(page.url()).toBe(l.portalUrl);

  await l.ui.fill('.address__input', 'file:///C:/Windows/win.ini');
  await l.ui.press('.address__input', 'Enter');
  await expect(l.ui.locator('.page-error h2')).toHaveText('Lapsus blocked this address');
  expect(page.url()).toBe(l.portalUrl);

  await l.ui.click('text=Go to Student Portal');
  await expect(l.ui.locator('.page-error')).toHaveCount(0);
  await page.click('#http-link');
  await expect(l.ui.locator('.page-error h2')).toHaveText('This page is not secure');
});

test('Ctrl+K toggles the assistant without disturbing the page', async () => {
  const page = await portalPage(l);
  await page.fill('#user', 'student-123');
  await page.click('#user');
  await page.keyboard.press('Control+k');
  await expect(l.ui.locator('.assistant')).toBeVisible();
  await expect(l.ui.locator('.assistant')).toContainText('Set up the assistant');
  await l.ui.keyboard.press('Control+k');
  await expect(l.ui.locator('.assistant')).toHaveCount(0);
  expect(await page.inputValue('#user')).toBe('student-123');
  expect(page.url()).toBe(l.portalUrl);
});

test('assistant review and answer flow works with the offline demo provider', async () => {
  await l.ui.evaluate(() =>
    (window as unknown as { lapsus: { invoke: (c: string, a?: unknown) => Promise<unknown> } }).lapsus.invoke('settings:update', {
      aiProvider: 'demo',
    }),
  );
  await l.ui.click('.ai-trigger');
  await l.ui.fill('.composer__text', 'Which is FIFO? password: hunter2\nA. Stack\nB. Queue');
  await l.ui.click('button:has-text("Review")');
  await expect(l.ui.locator('.review__text')).toContainText('[password removed]');
  await expect(l.ui.locator('.review__text')).not.toContainText('hunter2');
  await l.ui.click('button:has-text("Send to Demo mode")');
  await expect(l.ui.locator('.answer')).toBeVisible();
  await expect(l.ui.locator('.answer')).toContainText('AI explanation');
  await expect(l.ui.locator('.answer')).toContainText('Low confidence');
  await l.ui.click('button[aria-label="Clear conversation"]');
  await expect(l.ui.locator('.answer')).toHaveCount(0);
});

test('"Use selected text" reads the page selection but never password fields', async () => {
  await l.ui.evaluate(() =>
    (window as unknown as { lapsus: { invoke: (c: string, a?: unknown) => Promise<unknown> } }).lapsus.invoke('settings:update', {
      aiProvider: 'demo',
    }),
  );
  const page = await portalPage(l);
  await page.evaluate(() => {
    const r = document.createRange();
    r.selectNodeContents(document.getElementById('q')!);
    getSelection()!.removeAllRanges();
    getSelection()!.addRange(r);
  });
  await l.ui.click('.ai-trigger');
  await l.ui.click('button:has-text("Use selected text")');
  await expect(l.ui.locator('.composer__text')).toHaveValue(/First-In, First-Out/);

  await page.fill('#pw', 'not-a-real-password');
  await page.focus('#pw');
  await page.evaluate(() => (document.getElementById('pw') as HTMLInputElement).select());
  await l.ui.fill('.composer__text', '');
  await l.ui.click('button:has-text("Use selected text")');
  await expect(l.ui.locator('.composer__text')).toHaveValue('');
});

test('pop-ups open in a sandboxed window; script pop-ups to blocked schemes are denied', async () => {
  const page = await portalPage(l);
  await page.click('#popup');
  const popup = await waitForWindow(l.app, (u) => u.endsWith('/popup'));
  await expect(popup.locator('h2')).toHaveText('Pop-up window');
  expect(await popup.evaluate(() => typeof (window as unknown as { require?: unknown }).require)).toBe('undefined');

  const before = l.app.windows().length;
  await page.evaluate(() => window.open('file:///C:/Windows/win.ini'));
  await page.waitForTimeout(500);
  expect(l.app.windows().length).toBe(before);
});

test('focus mode hides and restores Lapsus chrome', async () => {
  await portalPage(l);
  await l.ui.keyboard.press('Control+Shift+F');
  await expect(l.ui.locator('.titlebar')).toHaveCount(0);
  await expect(l.ui.locator('.reveal-strip')).toBeVisible();
  const state = await l.ui.evaluate(() =>
    (window as unknown as { lapsus: { invoke: (c: string) => Promise<{ window: { focusMode: boolean } }> } }).lapsus.invoke('state:get'),
  );
  expect(state.window.focusMode).toBe(true);
  await l.ui.hover('.reveal-strip');
  await expect(l.ui.locator('.titlebar')).toBeVisible();
  await l.ui.click('button[aria-label^="Exit focus mode"]');
  await expect(l.ui.locator('.reveal-strip')).toHaveCount(0);
  await expect(l.ui.locator('.sidebar')).toBeVisible();
});

test('downloads are tracked and saved', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'lapsus-dl-'));
  // Skip the native save dialog in the test by choosing the path up front.
  await l.app.evaluate(({ session }, target) => {
    session.fromPartition('persist:lapsus-web').on('will-download', (_e, item) => item.setSavePath(`${target}/${item.getFilename()}`));
  }, dir);
  const page = await portalPage(l);
  await page.click('#materials');
  await page.click('text=Download notes');
  await l.ui.click('.nav-item:has-text("Downloads")');
  await expect(l.ui.locator('.row--download')).toContainText('week3-notes.txt');
  await expect(l.ui.locator('.row--download .pill')).toHaveText('Done');
  expect(readFileSync(join(dir, 'week3-notes.txt'), 'utf8')).toContain('FIFO');
});

test('recovers from a crashed page', async () => {
  await portalPage(l);
  await l.app.evaluate(({ webContents }) => {
    const page = webContents.getAllWebContents().find((wc) => wc.getURL().startsWith('http://127.0.0.1'));
    page?.forcefullyCrashRenderer();
  });
  await expect(l.ui.locator('.page-error h2')).toHaveText('This page crashed');
  await l.ui.click('text=Try again');
  const page = await portalPage(l);
  await expect(page.locator('h2', { hasText: 'My Courses' })).toBeVisible();
  await expect(l.ui.locator('.page-error')).toHaveCount(0);
});

test('closing the window quits the app process', async () => {
  const proc = l.app.process();
  const exited = new Promise<number | null>((resolve) => proc.once('exit', (code) => resolve(code)));
  await l.ui.click('button[aria-label="Close"]');
  expect(await exited).toBe(0);
});
