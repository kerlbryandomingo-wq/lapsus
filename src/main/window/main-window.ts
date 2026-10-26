import {
  BaseWindow,
  BrowserWindow,
  Menu,
  WebContentsView,
  clipboard,
  dialog,
  shell,
  type ContextMenuParams,
  type Input,
  type WebContents,
} from 'electron';
import { APP_NAME, MAX_POPUP_WINDOWS } from '@shared/constants';
import type { Settings } from '@shared/settings';
import type { AppCommand, MainEvent, NavState, PageError, WindowState } from '@shared/types';
import {
  evaluateNavigation,
  isSafeExternalUrl,
  normalizeAddressInput,
  securityLevelFor,
  type NavigationContext,
} from '@shared/url';
import { IPC_EVENT } from '@shared/ipc';
import { matchShortcut, type ShortcutAction } from './shortcuts';
import type { WebSession } from '../browser/web-session';

export interface MainWindowDeps {
  uiUrl: string;
  preloadPath: string;
  iconPath: string | undefined;
  devTools: boolean;
  webSession: WebSession;
  getSettings: () => Settings;
  updateSettings: (patch: Partial<Settings>) => void;
  portalUrl: string;
  isBookmarked: (url: string) => boolean;
  onPageVisited: (url: string, title: string) => void;
  onTitleUpdated: (url: string, title: string) => void;
}

interface Insets {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

const FULLSCREEN_HINT_MS = 3000;
const FULLSCREEN_HINT_HEIGHT = 36;
const ISOLATED_WORLD_ID = 1001;

/**
 * The Lapsus window: one trusted view for the app UI and one sandboxed view for web content.
 * The two views never share a session, a preload, or IPC access.
 */
export class MainWindow {
  readonly window: BaseWindow;
  readonly ui: WebContentsView;
  readonly page: WebContentsView;

  private insets: Insets | null = null;
  private pageVisible = false;
  private fullscreenHintUntil = 0;
  private hintTimer: NodeJS.Timeout | null = null;
  private popups = new Set<BrowserWindow>();
  private allowedHttpOrigins = new Set<string>();
  private certErrorUrl: string | null = null;
  private pageError: PageError | null = null;
  private findActive = false;
  private state: WindowState = { maximized: false, fullscreen: false, pageFullscreen: false, focusMode: false };
  private loadedOnce = false;
  private uiReady = false;

  constructor(private readonly deps: MainWindowDeps) {
    const settings = deps.getSettings();
    this.window = new BaseWindow({
      title: APP_NAME,
      width: 1280,
      height: 800,
      minWidth: 760,
      minHeight: 520,
      show: false,
      frame: false,
      backgroundColor: '#F7F5F2',
      icon: deps.iconPath,
    });

    this.ui = new WebContentsView({
      webPreferences: {
        preload: deps.preloadPath,
        sandbox: true,
        contextIsolation: true,
        nodeIntegration: false,
        webSecurity: true,
        spellcheck: false,
        devTools: deps.devTools,
        backgroundThrottling: false,
      },
    });
    this.ui.setBackgroundColor('#F7F5F2');

    this.page = new WebContentsView({
      webPreferences: {
        session: deps.webSession.session,
        sandbox: true,
        contextIsolation: true,
        nodeIntegration: false,
        nodeIntegrationInSubFrames: false,
        webSecurity: true,
        allowRunningInsecureContent: false,
        safeDialogs: true,
        navigateOnDragDrop: false,
        spellcheck: true,
        devTools: deps.devTools,
      },
    });
    this.page.setBackgroundColor('#FFFFFF');
    this.page.setVisible(false);

    this.window.contentView.addChildView(this.ui);
    this.window.contentView.addChildView(this.page);

    this.state.focusMode = settings.rememberFocusMode && settings.focusMode;

    this.wireWindow();
    this.wireUi();
    this.wirePage(this.page.webContents);
    this.layout();

    void this.ui.webContents.loadURL(deps.uiUrl);
  }

  // ---------------------------------------------------------------- window

  private wireWindow(): void {
    const w = this.window;
    w.on('resize', () => this.layout());
    w.on('maximize', () => this.updateWindowState());
    w.on('unmaximize', () => this.updateWindowState());
    w.on('enter-full-screen', () => this.updateWindowState());
    w.on('leave-full-screen', () => {
      // Leaving OS fullscreen (F11, Esc, or the OS) always ends page fullscreen too.
      if (this.state.pageFullscreen) void this.exitPageFullscreen();
      this.updateWindowState();
    });
    w.on('closed', () => {
      for (const p of this.popups) if (!p.isDestroyed()) p.close();
      this.popups.clear();
      if (this.hintTimer) clearTimeout(this.hintTimer);
    });
  }

  showMaximized(): void {
    this.window.maximize();
    this.window.show();
    this.updateWindowState();
  }

  private updateWindowState(): void {
    this.state = {
      ...this.state,
      maximized: this.window.isMaximized(),
      fullscreen: this.window.isFullScreen(),
    };
    this.layout();
    this.emit({ type: 'window', window: { ...this.state } });
  }

  get windowState(): WindowState {
    return { ...this.state };
  }

  setFocusMode(enabled: boolean): void {
    if (this.state.focusMode === enabled) return;
    this.state.focusMode = enabled;
    if (this.deps.getSettings().rememberFocusMode) this.deps.updateSettings({ focusMode: enabled });
    this.updateWindowState();
  }

  toggleFullscreen(): void {
    if (this.state.pageFullscreen) {
      void this.exitPageFullscreen();
      return;
    }
    this.window.setFullScreen(!this.window.isFullScreen());
  }

  toggleMaximize(): void {
    if (this.window.isFullScreen()) this.window.setFullScreen(false);
    else if (this.window.isMaximized()) this.window.unmaximize();
    else this.window.maximize();
  }

  // ---------------------------------------------------------------- layout

  /** Called by the UI with the rectangle (in DIPs) where web content should appear. */
  setPageBounds(rect: { x: number; y: number; width: number; height: number; visible: boolean }): void {
    const [w = 0, h = 0] = this.window.getContentSize();
    this.insets = {
      left: Math.max(0, Math.round(rect.x)),
      top: Math.max(0, Math.round(rect.y)),
      right: Math.max(0, Math.round(w - rect.x - rect.width)),
      bottom: Math.max(0, Math.round(h - rect.y - rect.height)),
    };
    this.pageVisible = rect.visible && rect.width > 0 && rect.height > 0;
    this.layout();
  }

  private layout(): void {
    if (this.window.isDestroyed()) return;
    const [w = 0, h = 0] = this.window.getContentSize();
    this.ui.setBounds({ x: 0, y: 0, width: w, height: h });

    if (this.state.pageFullscreen) {
      // A page in fullscreen gets the whole window, apart from a brief exit hint at the top.
      const hint = Date.now() < this.fullscreenHintUntil ? FULLSCREEN_HINT_HEIGHT : 0;
      this.page.setBounds({ x: 0, y: hint, width: w, height: Math.max(0, h - hint) });
      this.page.setVisible(true);
      return;
    }
    if (!this.insets) {
      this.page.setVisible(false);
      return;
    }
    const { left, top, right, bottom } = this.insets;
    this.page.setBounds({
      x: left,
      y: top,
      width: Math.max(0, w - left - right),
      height: Math.max(0, h - top - bottom),
    });
    this.page.setVisible(this.pageVisible);
  }

  private async exitPageFullscreen(): Promise<void> {
    try {
      // Ask the page to leave fullscreen through the standard API, in an isolated world.
      await this.page.webContents.executeJavaScriptInIsolatedWorld(ISOLATED_WORLD_ID, [
        { code: 'document.fullscreenElement ? document.exitFullscreen().catch(() => {}) : undefined' },
      ]);
    } catch {
      /* page may be gone */
    }
  }

  // ---------------------------------------------------------------- UI view

  private wireUi(): void {
    const wc = this.ui.webContents;
    // The UI view only ever shows the bundled app. Any navigation away is refused.
    wc.on('will-navigate', (event, url) => {
      if (url !== this.deps.uiUrl) event.preventDefault();
    });
    wc.setWindowOpenHandler(() => ({ action: 'deny' }));
    wc.on('will-attach-webview', (event) => event.preventDefault());
    wc.on('before-input-event', (event, input) => this.onInput(event, input, 'ui'));
    wc.on('render-process-gone', (_e, details) => {
      if (details.reason !== 'clean-exit') setTimeout(() => void wc.loadURL(this.deps.uiUrl), 500);
    });
    wc.on('did-finish-load', () => {
      this.uiReady = true;
    });
  }

  get isUiReady(): boolean {
    return this.uiReady;
  }

  emit(event: MainEvent): void {
    const wc = this.ui.webContents;
    if (!wc.isDestroyed()) wc.send(IPC_EVENT, event);
  }

  command(command: AppCommand): void {
    this.emit({ type: 'command', command });
  }

  // ---------------------------------------------------------------- page view

  private navContext(): NavigationContext {
    const s = this.deps.getSettings();
    return { portalOnly: s.portalOnly, extraPortalHosts: s.extraPortalHosts, allowedHttpOrigins: this.allowedHttpOrigins };
  }

  private wirePage(wc: WebContents): void {
    wc.on('will-navigate', (event) => this.guardNavigation(event, event.url, event.isMainFrame));
    wc.on('will-redirect', (event) => this.guardNavigation(event, event.url, event.isMainFrame));
    wc.on('will-attach-webview', (event) => event.preventDefault());
    wc.setWindowOpenHandler((details) => this.handleWindowOpen(details.url));
    wc.on('did-create-window', (child) => this.adoptPopup(child));
    wc.on('before-input-event', (event, input) => this.onInput(event, input, 'page'));
    wc.on('context-menu', (_event, params) => this.showContextMenu(wc, params));

    wc.on('did-start-loading', () => this.publishNav());
    wc.on('did-stop-loading', () => this.publishNav());
    wc.on('did-start-navigation', (details) => {
      if (details.isMainFrame && !details.isSameDocument) {
        this.pageError = null;
        this.certErrorUrl = null;
      }
    });
    wc.on('did-navigate', (_e, url) => {
      this.loadedOnce = true;
      this.deps.onPageVisited(url, wc.getTitle());
      this.publishNav();
    });
    wc.on('did-navigate-in-page', (_e, url, isMainFrame) => {
      if (isMainFrame) {
        this.deps.onPageVisited(url, wc.getTitle());
        this.publishNav();
      }
    });
    wc.on('page-title-updated', (_e, title) => {
      this.deps.onTitleUpdated(wc.getURL(), title);
      this.publishNav();
    });
    wc.on('did-fail-load', (_e, code, description, url, isMainFrame) => {
      if (!isMainFrame || code === -3) return; // -3: aborted by a newer navigation
      const isCert = code <= -200 && code > -300;
      if (isCert) this.certErrorUrl = url;
      const offline = code === -106 || code === -105 || code === -21;
      this.pageError = {
        kind: isCert ? 'cert' : offline ? 'offline' : 'load-failed',
        url,
        code,
        description: friendlyError(code, description),
      };
      this.publishNav();
    });
    wc.on('render-process-gone', (_e, details) => {
      if (details.reason === 'clean-exit') return;
      this.pageError = {
        kind: 'crashed',
        url: wc.getURL(),
        description: 'The page stopped working. Reload to try again.',
      };
      if (this.state.pageFullscreen) this.leftPageFullscreen();
      this.publishNav();
    });
    wc.on('unresponsive', () =>
      this.command({ type: 'notice', level: 'warning', message: 'The page is not responding. You can wait or reload it.' }),
    );

    wc.on('found-in-page', (_e, result) => {
      this.emit({ type: 'find', find: { active: this.findActive, matches: result.matches, activeMatch: result.activeMatchOrdinal } });
    });
    wc.on('enter-html-full-screen', () => {
      this.state.pageFullscreen = true;
      this.fullscreenHintUntil = Date.now() + FULLSCREEN_HINT_MS;
      if (this.hintTimer) clearTimeout(this.hintTimer);
      this.hintTimer = setTimeout(() => this.layout(), FULLSCREEN_HINT_MS + 20);
      if (!this.window.isFullScreen()) this.window.setFullScreen(true);
      this.updateWindowState();
    });
    wc.on('leave-html-full-screen', () => this.leftPageFullscreen());
  }

  private leftPageFullscreen(): void {
    this.state.pageFullscreen = false;
    this.fullscreenHintUntil = 0;
    if (this.window.isFullScreen()) this.window.setFullScreen(false);
    this.updateWindowState();
  }

  private guardNavigation(event: { preventDefault: () => void }, url: string, isMainFrame: boolean): void {
    if (!isMainFrame) {
      // Subframes may load web pages but never local files or script URLs.
      if (!/^(https?:|about:blank|about:srcdoc|data:|blob:)/i.test(url)) event.preventDefault();
      return;
    }
    const decision = evaluateNavigation(url, this.navContext());
    if (decision.action === 'allow') return;
    event.preventDefault();
    if (decision.action === 'external') {
      void this.openExternal(decision.url);
      return;
    }
    this.showBlocked(url, decision.reason);
  }

  private showBlocked(url: string, reason: 'invalid' | 'scheme' | 'insecure' | 'credentials'): void {
    const description =
      reason === 'insecure'
        ? 'This page uses an unencrypted connection (HTTP). Information you send could be read or changed by others on the network.'
        : reason === 'credentials'
          ? 'This address contains embedded login details, which is a common phishing technique. Lapsus blocked it.'
          : reason === 'scheme'
            ? 'Lapsus does not open this kind of address.'
            : 'This address is not valid.';
    this.pageError = { kind: reason === 'insecure' ? 'insecure' : 'blocked', url, description };
    this.publishNav();
    this.command({ type: 'show-page', page: 'portal' });
  }

  private handleWindowOpen(url: string): ReturnType<Parameters<WebContents['setWindowOpenHandler']>[0]> {
    const decision = evaluateNavigation(url, this.navContext());
    if (decision.action === 'external') {
      void this.openExternal(decision.url);
      return { action: 'deny' };
    }
    if (decision.action === 'block') {
      this.command({ type: 'notice', level: 'warning', message: 'Lapsus blocked a pop-up to an address it does not open.' });
      return { action: 'deny' };
    }
    const open = [...this.popups].filter((p) => !p.isDestroyed()).length;
    if (open >= MAX_POPUP_WINDOWS) {
      this.command({ type: 'notice', level: 'warning', message: 'Too many pop-up windows are open. Close one and try again.' });
      return { action: 'deny' };
    }
    return {
      action: 'allow',
      outlivesOpener: false,
      overrideBrowserWindowOptions: {
        width: 1000,
        height: 760,
        autoHideMenuBar: true,
        backgroundColor: '#FFFFFF',
        icon: this.deps.iconPath,
        webPreferences: {
          session: this.deps.webSession.session,
          sandbox: true,
          contextIsolation: true,
          nodeIntegration: false,
          webSecurity: true,
          allowRunningInsecureContent: false,
          safeDialogs: true,
          navigateOnDragDrop: false,
          devTools: this.deps.devTools,
        },
      },
    };
  }

  private adoptPopup(child: BrowserWindow): void {
    this.popups.add(child);
    child.setMenu(null);
    const wc = child.webContents;
    wc.on('will-navigate', (event) => this.guardPopupNavigation(event, event.url, event.isMainFrame));
    wc.on('will-redirect', (event) => this.guardPopupNavigation(event, event.url, event.isMainFrame));
    wc.on('will-attach-webview', (event) => event.preventDefault());
    wc.setWindowOpenHandler((details) => this.handleWindowOpen(details.url));
    wc.on('did-create-window', (grandchild) => this.adoptPopup(grandchild));
    wc.on('context-menu', (_event, params) => this.showContextMenu(wc, params));
    child.on('closed', () => this.popups.delete(child));
  }

  private guardPopupNavigation(event: { preventDefault: () => void }, url: string, isMainFrame: boolean): void {
    if (!isMainFrame) {
      if (!/^(https?:|about:blank|about:srcdoc|data:|blob:)/i.test(url)) event.preventDefault();
      return;
    }
    const decision = evaluateNavigation(url, this.navContext());
    if (decision.action === 'allow') return;
    event.preventDefault();
    if (decision.action === 'external') void this.openExternal(decision.url);
  }

  /** Hands a link to the default system browser or mail app after the student confirms. */
  async openExternal(url: string): Promise<boolean> {
    if (!isSafeExternalUrl(url)) return false;
    const { response } = await dialog.showMessageBox(this.window, {
      type: 'question',
      title: 'Open outside Lapsus?',
      message: 'This link opens in another app.',
      detail: url.length > 300 ? `${url.slice(0, 300)}…` : url,
      buttons: ['Cancel', 'Open'],
      defaultId: 1,
      cancelId: 0,
      noLink: true,
    });
    if (response !== 1) return false;
    await shell.openExternal(url);
    return true;
  }

  // ---------------------------------------------------------------- navigation API

  navigate(input: string): void {
    const s = this.deps.getSettings();
    const url = normalizeAddressInput(input, s.searchEngine);
    if (!url) return;
    const decision = evaluateNavigation(url, this.navContext());
    if (decision.action === 'external') {
      void this.openExternal(decision.url);
      return;
    }
    if (decision.action === 'block') {
      this.showBlocked(url, decision.reason);
      return;
    }
    this.pageError = null;
    this.loadPage(url);
  }

  loadPage(url: string): void {
    this.pageError = null;
    this.certErrorUrl = null;
    this.page.webContents.loadURL(url).catch(() => {
      /* failures surface through did-fail-load */
    });
    this.publishNav();
  }

  ensurePortalLoaded(): void {
    if (!this.loadedOnce && !this.page.webContents.isLoading()) this.loadPage(this.deps.portalUrl);
  }

  goPortal(): void {
    this.loadPage(this.deps.portalUrl);
  }

  /** The student chose to continue to an HTTP page for this session. */
  allowInsecure(url: string): void {
    try {
      const parsed = new URL(url);
      if (parsed.protocol !== 'http:') return;
      this.allowedHttpOrigins.add(parsed.origin);
      this.loadPage(parsed.toString());
    } catch {
      /* ignore invalid */
    }
  }

  back(): void {
    const nav = this.page.webContents.navigationHistory;
    if (nav.canGoBack()) nav.goBack();
  }

  forward(): void {
    const nav = this.page.webContents.navigationHistory;
    if (nav.canGoForward()) nav.goForward();
  }

  reload(ignoreCache: boolean): void {
    const wc = this.page.webContents;
    this.pageError = null;
    if (!wc.getURL()) {
      this.loadPage(this.deps.portalUrl);
      return;
    }
    if (ignoreCache) wc.reloadIgnoringCache();
    else wc.reload();
  }

  stop(): void {
    this.page.webContents.stop();
  }

  zoom(step: -1 | 0 | 1): void {
    const wc = this.page.webContents;
    const level = step === 0 ? 0 : Math.max(-5, Math.min(5, wc.getZoomLevel() + step * 0.5));
    wc.setZoomLevel(level);
    this.publishNav();
  }

  find(text: string, forward: boolean, findNext: boolean): void {
    this.findActive = true;
    this.page.webContents.findInPage(text, { forward, findNext });
  }

  stopFind(): void {
    this.findActive = false;
    this.page.webContents.stopFindInPage('keepSelection');
  }

  async getSelection(): Promise<{ text: string; reason?: string }> {
    try {
      const result = (await this.page.webContents.executeJavaScriptInIsolatedWorld(ISOLATED_WORLD_ID, [
        {
          code: `(() => {
            const a = document.activeElement;
            if (a && a.tagName === 'INPUT' && String(a.type).toLowerCase() === 'password') return { text: '', reason: 'password' };
            return { text: String(window.getSelection() || '').slice(0, 50000) };
          })()`,
        },
      ])) as { text?: unknown; reason?: unknown };
      const text = typeof result?.text === 'string' ? result.text : '';
      return result?.reason === 'password' ? { text: '', reason: 'password' } : { text };
    } catch {
      return { text: '', reason: 'unavailable' };
    }
  }

  navState(): NavState {
    const wc = this.page.webContents;
    const url = wc.getURL();
    const certError = !!this.certErrorUrl || this.pageError?.kind === 'cert';
    let certificate = null;
    try {
      const parsed = new URL(url);
      if (parsed.protocol === 'https:') certificate = this.deps.webSession.certificateFor(parsed.hostname);
    } catch {
      /* no url yet */
    }
    const shownUrl = this.pageError?.url ?? url;
    return {
      url: shownUrl,
      title: wc.getTitle() || '',
      canGoBack: wc.navigationHistory.canGoBack(),
      canGoForward: wc.navigationHistory.canGoForward(),
      loading: wc.isLoading(),
      security: securityLevelFor(shownUrl, certError),
      certificate: certError ? null : certificate,
      bookmarked: url ? this.deps.isBookmarked(url) : false,
      zoomPercent: Math.round(100 * Math.pow(1.2, wc.getZoomLevel())),
      error: this.pageError,
    };
  }

  publishNav(): void {
    if (this.page.webContents.isDestroyed()) return;
    this.emit({ type: 'nav', nav: this.navState() });
  }

  // ---------------------------------------------------------------- keyboard

  private onInput(event: { preventDefault: () => void }, input: Input, source: 'ui' | 'page'): void {
    const action = matchShortcut(input, this.deps.getSettings());
    if (!action) return;
    if (this.handleShortcut(action, source)) event.preventDefault();
  }

  /** Returns true when Lapsus consumed the key. */
  private handleShortcut(action: ShortcutAction, source: 'ui' | 'page'): boolean {
    switch (action) {
      case 'toggle-ai':
        this.command({ type: 'toggle-ai' });
        this.ui.webContents.focus();
        return true;
      case 'focus-address':
        if (this.state.focusMode) this.setFocusMode(false);
        this.command({ type: 'focus-address' });
        this.ui.webContents.focus();
        return true;
      case 'find':
        this.command({ type: 'open-find' });
        this.ui.webContents.focus();
        return true;
      case 'reload':
        this.reload(false);
        return true;
      case 'hard-reload':
        this.reload(true);
        return true;
      case 'back':
        this.back();
        return true;
      case 'forward':
        this.forward();
        return true;
      case 'bookmark':
        this.command({ type: 'show-page', page: 'bookmarks' });
        return true;
      case 'history':
        this.command({ type: 'show-page', page: 'history' });
        return true;
      case 'downloads':
        this.command({ type: 'show-page', page: 'downloads' });
        return true;
      case 'home':
        this.command({ type: 'show-page', page: 'home' });
        return true;
      case 'toggle-focus-mode':
        this.setFocusMode(!this.state.focusMode);
        return true;
      case 'toggle-fullscreen':
        this.toggleFullscreen();
        return true;
      case 'zoom-in':
        this.zoom(1);
        return true;
      case 'zoom-out':
        this.zoom(-1);
        return true;
      case 'zoom-reset':
        this.zoom(0);
        return true;
      case 'escape':
        // Escape belongs to the page unless Lapsus has something of its own to close.
        if (this.state.pageFullscreen) {
          void this.exitPageFullscreen();
          return true;
        }
        if (this.window.isFullScreen()) {
          this.window.setFullScreen(false);
          return true;
        }
        if (this.findActive) {
          this.stopFind();
          this.command({ type: 'close-overlays' });
          return true;
        }
        if (source === 'ui') {
          this.command({ type: 'close-overlays' });
          return false;
        }
        return false;
      case 'reveal-chrome':
        return false;
    }
  }

  // ---------------------------------------------------------------- context menu

  private showContextMenu(wc: WebContents, params: ContextMenuParams): void {
    const template: Electron.MenuItemConstructorOptions[] = [];
    const isMainPage = wc === this.page.webContents;
    const selection = params.selectionText.trim();

    if (params.linkURL && /^https?:/i.test(params.linkURL)) {
      template.push(
        { label: 'Open link in new window', click: () => this.openLinkInWindow(params.linkURL) },
        { label: 'Open link in default browser', click: () => void this.openExternal(params.linkURL) },
        { label: 'Copy link address', click: () => clipboard.writeText(params.linkURL) },
        { type: 'separator' },
      );
    }
    if (params.isEditable) {
      template.push(
        { role: 'undo', enabled: params.editFlags.canUndo },
        { role: 'redo', enabled: params.editFlags.canRedo },
        { type: 'separator' },
        { role: 'cut', enabled: params.editFlags.canCut },
        { role: 'copy', enabled: params.editFlags.canCopy },
        { role: 'paste', enabled: params.editFlags.canPaste },
        { role: 'selectAll' },
      );
      if (params.misspelledWord && params.dictionarySuggestions.length) {
        template.unshift(
          ...params.dictionarySuggestions.slice(0, 5).map((s) => ({ label: s, click: () => wc.replaceMisspelling(s) })),
          { type: 'separator' as const },
        );
      }
    } else if (selection) {
      template.push({ role: 'copy' });
    }
    if (selection && isMainPage && params.formControlType !== 'input-password') {
      template.push(
        { type: 'separator' },
        {
          label: 'Ask Study Assistant about selection',
          click: () => {
            this.pendingSelection = selection.slice(0, 50000);
            this.command({ type: 'ask-selection' });
          },
        },
      );
    }
    if (!params.isEditable && !selection && !params.linkURL && isMainPage) {
      template.push(
        { label: 'Back', enabled: wc.navigationHistory.canGoBack(), click: () => this.back() },
        { label: 'Forward', enabled: wc.navigationHistory.canGoForward(), click: () => this.forward() },
        { label: 'Reload', click: () => this.reload(false) },
      );
    }
    if (template.length === 0) return;
    Menu.buildFromTemplate(template).popup();
  }

  /** Selection captured from the context menu, handed to the assistant once on request. */
  pendingSelection: string | null = null;

  takePendingSelection(): string | null {
    const s = this.pendingSelection;
    this.pendingSelection = null;
    return s;
  }

  private openLinkInWindow(url: string): void {
    const result = this.handleWindowOpen(url);
    if (result.action !== 'allow') return;
    const child = new BrowserWindow(result.overrideBrowserWindowOptions);
    this.adoptPopup(child);
    void child.loadURL(url);
  }

  clearHttpExceptions(): void {
    this.allowedHttpOrigins.clear();
  }
}

function friendlyError(code: number, description: string): string {
  switch (code) {
    case -105:
      return 'The site address could not be found. Check the address or your internet connection.';
    case -106:
      return 'You appear to be offline. Check your internet connection and try again.';
    case -21:
      return 'Your network connection changed. Try again.';
    case -102:
      return 'The site refused the connection.';
    case -118:
      return 'The site took too long to respond.';
    case -137:
      return 'The site could not be reached (name resolution failed).';
    default:
      if (code <= -200 && code > -300) {
        return `The site's security certificate could not be verified (${description}). Lapsus stopped loading it to protect you.`;
      }
      return `The page could not be loaded (${description || `error ${code}`}).`;
  }
}
