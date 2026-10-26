import type { SecurityLevel } from './url';
import type { Settings, AIProviderId } from './settings';
import type { AIAnswer } from './ai';

export type AppPage =
  'home' | 'portal' | 'bookmarks' | 'history' | 'downloads' | 'settings' | 'about';

export interface PageError {
  kind: 'load-failed' | 'blocked' | 'insecure' | 'cert' | 'crashed' | 'offline';
  url: string;
  code?: number;
  description: string;
}

export interface CertificateInfo {
  host: string;
  issuer: string;
  subject: string;
  validExpiry: number;
}

export interface NavState {
  url: string;
  title: string;
  canGoBack: boolean;
  canGoForward: boolean;
  loading: boolean;
  security: SecurityLevel;
  certificate: CertificateInfo | null;
  bookmarked: boolean;
  zoomPercent: number;
  error: PageError | null;
}

export interface WindowState {
  maximized: boolean;
  /** Real OS fullscreen of the Lapsus window (F11 or a page Fullscreen API request). */
  fullscreen: boolean;
  /** The page itself requested fullscreen through the standard Fullscreen API. */
  pageFullscreen: boolean;
  /** Distraction-reduced mode: Lapsus chrome hidden, window stays a normal window. */
  focusMode: boolean;
}

export interface Bookmark {
  id: string;
  title: string;
  url: string;
  folder: string;
  createdAt: number;
}

export interface HistoryEntry {
  id: string;
  url: string;
  title: string;
  visitedAt: number;
}

export interface DownloadInfo {
  id: string;
  filename: string;
  url: string;
  state: 'progressing' | 'completed' | 'cancelled' | 'interrupted';
  receivedBytes: number;
  totalBytes: number;
  savePath: string | null;
  startedAt: number;
  dangerous: boolean;
}

export interface FindState {
  active: boolean;
  matches: number;
  activeMatch: number;
}

export interface AIStatus {
  provider: AIProviderId;
  model: string | null;
  keyStored: boolean;
  encryptionAvailable: boolean;
  ready: boolean;
  usage: { month: string; requests: number; limit: number };
  message: string;
}

export type AIAskResult =
  | { ok: true; answer: AIAnswer; provider: AIProviderId; model: string; sentText: string }
  | {
      ok: false;
      error: string;
      code:
        | 'not-configured'
        | 'limit'
        | 'network'
        | 'auth'
        | 'refused'
        | 'invalid-response'
        | 'cancelled'
        | 'input'
        | 'unknown';
    };

export interface AppInfo {
  name: string;
  version: string;
  publisher: string;
  electron: string;
  chromium: string;
  node: string;
  platform: string;
  arch: string;
  userDataPath: string;
  packaged: boolean;
}

export interface Snapshot {
  nav: NavState;
  window: WindowState;
  settings: Settings;
  ai: AIStatus;
  info: AppInfo;
  initialPage: AppPage;
}

export type AppCommand =
  | { type: 'toggle-ai' }
  /** The pointer reached the top edge of a full-height page: show the hidden Lapsus controls. */
  | { type: 'reveal-chrome' }
  | { type: 'ask-selection' }
  | { type: 'focus-address' }
  | { type: 'open-find' }
  | { type: 'close-overlays' }
  | { type: 'show-page'; page: AppPage }
  | {
      type: 'notice';
      level: 'info' | 'warning' | 'error';
      message: string;
      /** Set when the notice is about a blocked pop-up; the band then offers to allow that site. */
      popupOrigin?: string;
    };

export type MainEvent =
  | { type: 'nav'; nav: NavState }
  | { type: 'window'; window: WindowState }
  | { type: 'settings'; settings: Settings }
  | { type: 'downloads'; downloads: DownloadInfo[] }
  | { type: 'find'; find: FindState }
  | { type: 'ai'; ai: AIStatus }
  | { type: 'command'; command: AppCommand };
