import { REPO_URL } from '@shared/constants';
import type { AppInfo, Snapshot } from '@shared/types';
import type { Handlers } from './ipc-router';
import type { MainWindow } from './window/main-window';
import type { BookmarkStore, HistoryStore, SessionStore, SettingsStore } from './store/stores';
import type { SecretStore } from './store/secrets';
import type { AIService } from './ai/service';
import type { DownloadManager } from './browser/downloads';
import type { WebSession } from './browser/web-session';

interface Deps {
  win: MainWindow;
  settings: SettingsStore;
  bookmarks: BookmarkStore;
  history: HistoryStore;
  sessionStore: SessionStore;
  secrets: SecretStore;
  ai: AIService;
  downloads: DownloadManager;
  webSession: WebSession;
  info: AppInfo;
  snapshot: () => Snapshot;
}

export function createHandlers(d: Deps): Handlers {
  const { win } = d;
  const publishAi = () => win.emit({ type: 'ai', ai: d.ai.status() });

  return {
    'state:get': () => d.snapshot(),
    'nav:go': ({ input }) => win.navigate(input),
    'nav:portal': () => win.goPortal(),
    'nav:back': () => win.back(),
    'nav:forward': () => win.forward(),
    'nav:reload': ({ ignoreCache }) => win.reload(ignoreCache),
    'nav:stop': () => win.stop(),
    'nav:allow-insecure': ({ url }) => win.allowInsecure(url),
    'popups:allow-site': ({ origin }) => win.allowPopupsFrom(origin),
    'view:set-bounds': (rect) => {
      win.setPageBounds(rect);
      if (rect.visible) win.ensurePortalLoaded();
    },
    'find:start': ({ text, forward, findNext }) => win.find(text, forward, findNext),
    'find:stop': () => win.stopFind(),
    'zoom:step': ({ step }) => win.zoom(step),
    'window:minimize': () => win.window.minimize(),
    'window:toggle-maximize': () => win.toggleMaximize(),
    'window:close': () => win.window.close(),
    'window:set-focus-mode': ({ enabled }) => win.setFocusMode(enabled),
    'window:toggle-fullscreen': () => win.toggleFullscreen(),
    'settings:update': (patch) => {
      const next = d.settings.update(patch);
      win.emit({ type: 'settings', settings: next });
      publishAi();
      win.publishNav();
      return next;
    },
    'bookmarks:list': () => d.bookmarks.list(),
    'bookmarks:toggle-current': () => {
      const { url, title } = win.navState();
      const existing = d.bookmarks.findByUrl(url);
      if (existing) d.bookmarks.remove(existing.id);
      else d.bookmarks.add(url, title, '');
      win.publishNav();
      return !existing;
    },
    'bookmarks:add': ({ url, title, folder }) => {
      const b = d.bookmarks.add(url, title, folder);
      win.publishNav();
      return b;
    },
    'bookmarks:update': ({ id, title, folder }) => d.bookmarks.update(id, { title, folder }),
    'bookmarks:remove': ({ id }) => {
      d.bookmarks.remove(id);
      win.publishNav();
    },
    'bookmarks:open': ({ id }) => {
      const b = d.bookmarks.get(id);
      if (b) win.navigate(b.url);
    },
    'history:list': ({ query, limit }) => d.history.list(query, limit),
    'history:remove': ({ id }) => d.history.remove(id),
    'history:clear': () => {
      d.history.clear();
      d.sessionStore.clear();
    },
    'history:open': ({ id }) => {
      const e = d.history.get(id);
      if (e) win.navigate(e.url);
    },
    'downloads:list': () => d.downloads.list(),
    'downloads:show': ({ id }) => d.downloads.show(id),
    'downloads:open': ({ id }) => d.downloads.open(id),
    'downloads:cancel': ({ id }) => d.downloads.cancel(id),
    'downloads:clear': () => d.downloads.clear(),
    'data:clear': async (opts) => {
      await d.webSession.clear({ cookies: opts.cookies, cache: opts.cache });
      if (opts.cookies) win.clearHttpExceptions();
      if (opts.history) {
        d.history.clear();
        d.sessionStore.clear();
      }
      if (opts.downloads) d.downloads.clear();
      if (opts.aiKeys) d.secrets.clear();
      publishAi();
    },
    'ai:status': () => d.ai.status(),
    'ai:set-key': async ({ provider, key }) => {
      await d.secrets.set(provider, key.trim());
      const status = d.ai.status();
      win.emit({ type: 'ai', ai: status });
      return status;
    },
    'ai:clear-key': ({ provider }) => {
      d.secrets.clear(provider);
      const status = d.ai.status();
      win.emit({ type: 'ai', ai: status });
      return status;
    },
    'ai:test': () => d.ai.test(),
    'ai:prepare': ({ material }) => d.ai.prepare(material),
    'ai:ask': async ({ material, note }) => {
      const result = await d.ai.ask(material, note);
      publishAi();
      return result;
    },
    'ai:cancel': () => d.ai.cancel(),
    'ai:get-selection': async () => {
      const pending = win.takePendingSelection();
      if (pending) return { text: pending };
      return win.getSelection();
    },
    'external:open': async ({ url }) => {
      // The app UI may only open the project page, or links the student explicitly chose.
      if (url !== REPO_URL && !url.startsWith(`${REPO_URL}/`)) return win.openExternal(url);
      return win.openExternal(url);
    },
    'app:info': () => d.info,
  };
}
