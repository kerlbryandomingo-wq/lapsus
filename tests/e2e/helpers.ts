/// <reference lib="dom" />
import { _electron as electron, type ElectronApplication, type Page } from '@playwright/test';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import type { Server } from 'node:http';
import { pathToFileURL } from 'node:url';

export interface Launched {
  app: ElectronApplication;
  ui: Page;
  portalUrl: string;
  userData: string;
  close: () => Promise<void>;
}

/**
 * Launches Lapsus against a local stand-in portal with an isolated profile.
 * Set LAPSUS_E2E_EXECUTABLE to test a packaged build instead of the compiled sources.
 */
export async function launch(
  // Most tests start in a normal maximized window; window-mode tests pass {} to use the defaults.
  settings: Record<string, unknown> = { windowMode: 'maximized' },
): Promise<Launched> {
  const fixture = pathToFileURL(resolve(__dirname, '../fixtures/portal-server.mjs')).href;
  const { startPortalServer } = (await import(fixture)) as {
    startPortalServer: () => Promise<{ server: Server; url: string }>;
  };
  const { server, url } = await startPortalServer();
  const userData = mkdtempSync(join(tmpdir(), 'lapsus-e2e-'));
  if (Object.keys(settings).length)
    writeFileSync(join(userData, 'settings.json'), JSON.stringify(settings));
  const executablePath = process.env.LAPSUS_E2E_EXECUTABLE;
  const app = await electron.launch({
    ...(executablePath ? { executablePath, args: [] } : { args: [resolve(__dirname, '../..')] }),
    env: { ...process.env, LAPSUS_E2E_PORTAL_URL: url, LAPSUS_E2E_USER_DATA: userData },
  });
  const ui = await waitForWindow(app, (u) => u.startsWith('app://lapsus/'));
  await ui.waitForSelector('.app');
  return {
    app,
    ui,
    portalUrl: url,
    userData,
    close: async () => {
      await app.close().catch(() => {});
      server.close();
    },
  };
}

export async function waitForWindow(
  app: ElectronApplication,
  match: (url: string) => boolean,
  timeout = 15_000,
): Promise<Page> {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const found = app.windows().find((w) => match(w.url()));
    if (found) return found;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error(
    `No window matched. Open: ${app
      .windows()
      .map((w) => w.url())
      .join(', ')}`,
  );
}

export async function portalPage(l: Launched): Promise<Page> {
  const page = await waitForWindow(
    l.app,
    (u) => u.startsWith(l.portalUrl) && !u.includes('/popup'),
  );
  await page.waitForLoadState('domcontentloaded');
  return page;
}

/** URL currently loaded in the browsing view, read from the main process. */

type Modifier = 'control' | 'shift' | 'alt';

/**
 * Presses a key the way a real keyboard does, through Electron's input pipeline, so Lapsus's
 * shortcut handling sees it. (Playwright's keyboard goes through DevTools and skips that step.)
 */
export async function pressKey(
  l: Launched,
  target: 'page' | 'ui',
  keyCode: string,
  modifiers: Modifier[] = [],
): Promise<void> {
  await l.app.evaluate(
    ({ webContents }, { target, keyCode, modifiers, portalUrl }) => {
      const all = webContents.getAllWebContents();
      const wc =
        target === 'ui'
          ? all.find((w) => w.getURL().startsWith('app://lapsus/index'))
          : all.find((w) => w.getURL().startsWith(portalUrl) && !w.getURL().includes('/popup'));
      if (!wc) throw new Error(`no ${target} contents`);
      wc.sendInputEvent({ type: 'keyDown', keyCode, modifiers });
      wc.sendInputEvent({ type: 'keyUp', keyCode, modifiers });
    },
    { target, keyCode, modifiers, portalUrl: l.portalUrl },
  );
}

export interface ProbeEntry {
  type: string;
  t: number;
  fs: boolean;
  w: number;
  h: number;
  sw: number;
  sh: number;
  vis: string;
  focus: boolean;
  key?: string;
  opened?: boolean;
}

export async function probe(page: Page): Promise<ProbeEntry[]> {
  return page.evaluate(() => (window as unknown as { __probe: ProbeEntry[] }).__probe.slice());
}

export async function clearProbe(page: Page): Promise<void> {
  await page.evaluate(() => {
    (window as unknown as { __probe: unknown[] }).__probe.length = 0;
  });
}

/** Window and page state as the main process sees it. */
export async function windowState(l: Launched): Promise<{
  fullscreen: boolean;
  pageFullscreen: boolean;
  minimized: boolean;
  focused: boolean;
  popups: number;
}> {
  const [base, snap] = await Promise.all([
    l.app.evaluate(({ BaseWindow, BrowserWindow }) => {
      const w = BaseWindow.getAllWindows().find((x) => !(x instanceof BrowserWindow))!;
      return {
        fullscreen: w.isFullScreen(),
        minimized: w.isMinimized(),
        focused: w.isFocused(),
        popups: BrowserWindow.getAllWindows().filter((b) => !b.isDestroyed()).length,
      };
    }),
    l.ui.evaluate(() =>
      (
        window as unknown as {
          lapsus: { invoke: (c: string) => Promise<{ window: { pageFullscreen: boolean } }> };
        }
      ).lapsus.invoke('state:get'),
    ),
  ]);
  return { ...base, pageFullscreen: snap.window.pageFullscreen };
}

export async function openExam(l: Launched, query = ''): Promise<Page> {
  await l.ui.evaluate(
    (u) =>
      (
        window as unknown as { lapsus: { invoke: (c: string, a: unknown) => Promise<unknown> } }
      ).lapsus.invoke('nav:go', { input: u }),
    `${l.portalUrl}exam${query}`,
  );
  const page = await waitForWindow(l.app, (u) => u.startsWith(`${l.portalUrl}exam`));
  await page.waitForFunction(() =>
    Array.isArray((window as unknown as { __probe?: unknown[] }).__probe),
  );
  return page;
}
