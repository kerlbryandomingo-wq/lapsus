import { _electron as electron, type ElectronApplication, type Page } from '@playwright/test';
import { mkdtempSync } from 'node:fs';
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
export async function launch(): Promise<Launched> {
  const fixture = pathToFileURL(resolve(__dirname, '../fixtures/portal-server.mjs')).href;
  const { startPortalServer } = (await import(fixture)) as {
    startPortalServer: () => Promise<{ server: Server; url: string }>;
  };
  const { server, url } = await startPortalServer();
  const userData = mkdtempSync(join(tmpdir(), 'lapsus-e2e-'));
  const executablePath = process.env.LAPSUS_E2E_EXECUTABLE;
  const app = await electron.launch({
    ...(executablePath ? { executablePath, args: [] } : { args: [resolve(__dirname, '../..')] }),
    env: { ...process.env, LAPSUS_E2E_PORTAL_URL: url, LAPSUS_E2E_USER_DATA: userData },
  });
  const ui = await waitForWindow(app, (u) => u.startsWith('app://lapsus/'));
  await ui.waitForSelector('.titlebar');
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

export async function waitForWindow(app: ElectronApplication, match: (url: string) => boolean, timeout = 15_000): Promise<Page> {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const found = app.windows().find((w) => match(w.url()));
    if (found) return found;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error(`No window matched. Open: ${app.windows().map((w) => w.url()).join(', ')}`);
}

export async function portalPage(l: Launched): Promise<Page> {
  const page = await waitForWindow(l.app, (u) => u.startsWith(l.portalUrl) && !u.includes('/popup'));
  await page.waitForLoadState('domcontentloaded');
  return page;
}

/** URL currently loaded in the browsing view, read from the main process. */
