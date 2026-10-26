import { app, dialog, session } from 'electron';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { APP_NAME, APP_ORIGIN, DEFAULT_PORTAL_URL, PUBLISHER } from '@shared/constants';
import type { AppInfo, AppPage, Snapshot } from '@shared/types';
import { registerAppScheme, serveAppProtocol } from './security/app-protocol';
import { WebSession } from './browser/web-session';
import { DownloadManager } from './browser/downloads';
import { BookmarkStore, HistoryStore, SessionStore, SettingsStore } from './store/stores';
import { SecretStore } from './store/secrets';
import { UsageStore } from './store/usage';
import { AIService } from './ai/service';
import { MainWindow } from './window/main-window';
import { isTrustedUiUrl, registerIpc } from './ipc-router';
import { createHandlers } from './handlers';

app.setName(APP_NAME);
app.setAppUserModelId('com.krlbryn.lapsus');

// Tests can point Lapsus at a local stand-in portal and an isolated profile directory.
const e2ePortal = process.env.LAPSUS_E2E_PORTAL_URL;
const e2eProfile = process.env.LAPSUS_E2E_USER_DATA;
if (e2eProfile) app.setPath('userData', e2eProfile);

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  registerAppScheme();
  app.enableSandbox();
  void start();
}

let mainWindow: MainWindow | null = null;

app.on('second-instance', () => {
  const win = mainWindow?.window;
  if (!win || win.isDestroyed()) return;
  if (win.isMinimized()) win.restore();
  win.focus();
});

// Block every attempt to create webviews or open new windows from any contents we did not set up.
app.on('web-contents-created', (_event, contents) => {
  contents.on('will-attach-webview', (e) => e.preventDefault());
});

app.on('window-all-closed', () => app.quit());

async function start(): Promise<void> {
  await app.whenReady();

  const dataDir = app.getPath('userData');
  const settings = new SettingsStore(dataDir);
  const bookmarks = new BookmarkStore(dataDir);
  const history = new HistoryStore(dataDir);
  const sessionStore = new SessionStore(dataDir);
  const secrets = new SecretStore(dataDir);
  await secrets.init();
  const usage = new UsageStore(dataDir);
  const ai = new AIService(secrets, usage, () => settings.get());

  const rendererDir = join(__dirname, '../renderer');
  const devUrl = !app.isPackaged ? process.env.ELECTRON_RENDERER_URL : undefined;
  const uiUrl = devUrl ?? `${APP_ORIGIN}/index.html`;
  if (!devUrl) serveAppProtocol(session.defaultSession, rendererDir);

  // The app UI session needs no permissions at all.
  session.defaultSession.setPermissionRequestHandler((_wc, _p, cb) => cb(false));
  session.defaultSession.setPermissionCheckHandler(() => false);

  const iconCandidates = [join(process.resourcesPath, 'icon.png'), join(__dirname, '../../build/icon.png')];
  const iconPath = iconCandidates.find((p) => existsSync(p));
  const portalUrl = e2ePortal || DEFAULT_PORTAL_URL;

  let win: MainWindow | null = null;
  const webSession = new WebSession(() => win?.window ?? null);
  const downloads = new DownloadManager(
    () => win?.window ?? null,
    (list) => win?.emit({ type: 'downloads', downloads: list }),
  );
  downloads.attach(webSession.session);

  win = new MainWindow({
    uiUrl,
    preloadPath: join(__dirname, '../preload/index.cjs'),
    iconPath,
    devTools: !app.isPackaged,
    webSession,
    getSettings: () => settings.get(),
    updateSettings: (patch) => {
      const next = settings.update(patch);
      win?.emit({ type: 'settings', settings: next });
    },
    portalUrl,
    isBookmarked: (url) => !!bookmarks.findByUrl(url),
    onPageVisited: (url, title) => {
      if (settings.get().historyEnabled) history.record(url, title);
      sessionStore.setLastUrl(url);
    },
    onTitleUpdated: (url, title) => {
      if (settings.get().historyEnabled) history.updateTitle(url, title);
    },
  });
  mainWindow = win;

  const info: AppInfo = {
    name: APP_NAME,
    version: app.getVersion(),
    publisher: PUBLISHER,
    electron: process.versions.electron,
    chromium: process.versions.chrome,
    node: process.versions.node,
    platform: process.platform,
    arch: process.arch,
    userDataPath: dataDir,
    packaged: app.isPackaged,
  };

  // Decide what the first screen is and start loading the page behind it.
  const startup = settings.get().startup;
  let initialPage: AppPage = 'portal';
  if (startup === 'home') {
    initialPage = 'home';
  } else if (startup === 'restore' && sessionStore.lastUrl && !e2ePortal) {
    win.navigate(sessionStore.lastUrl);
  } else {
    win.goPortal();
  }

  const snapshot = (): Snapshot => ({
    nav: win!.navState(),
    window: win!.windowState,
    settings: settings.get(),
    ai: ai.status(),
    info,
    initialPage,
  });

  registerIpc(
    createHandlers({ win, settings, bookmarks, history, sessionStore, secrets, ai, downloads, webSession, info, snapshot }),
    (sender, frameUrl) => sender === win!.ui.webContents && isTrustedUiUrl(frameUrl, uiUrl),
  );

  win.showMaximized();

  win.window.on('closed', () => {
    mainWindow = null;
  });

  app.on('before-quit', () => {
    settings.flush();
    bookmarks.flush();
    history.flush();
    sessionStore.flush();
  });

}

process.on('uncaughtException', (err) => {
  // Report without leaking stack traces that may contain local paths or data.
  console.error('Unexpected error:', err.message);
  if (app.isReady()) dialog.showErrorBox(APP_NAME, `Something went wrong: ${err.message}`);
});
