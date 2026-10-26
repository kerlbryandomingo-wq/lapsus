import { createContext, useContext, useEffect, useMemo, useReducer, type ReactNode } from 'react';
import type {
  AIStatus,
  AppInfo,
  AppPage,
  DownloadInfo,
  FindState,
  MainEvent,
  NavState,
  Snapshot,
  WindowState,
} from '@shared/types';
import type { Settings } from '@shared/settings';
import { api } from './api';

export interface Notice {
  id: number;
  level: 'info' | 'warning' | 'error' | 'success';
  message: string;
  popupOrigin?: string;
}

export interface UIState {
  ready: boolean;
  nav: NavState | null;
  window: WindowState | null;
  settings: Settings | null;
  ai: AIStatus | null;
  info: AppInfo | null;
  downloads: DownloadInfo[];
  find: FindState;
  page: AppPage;
  assistantOpen: boolean;
  findOpen: boolean;
  siteInfoOpen: boolean;
  notice: Notice | null;
  /** Bumped to ask the address bar to take focus. */
  addressFocusToken: number;
  /** Bumped when the pointer reaches the top edge of the page in an immersive layout. */
  revealToken: number;
  /** Bumped when the student asked the assistant about a page selection. */
  askSelectionToken: number;
}

type Action =
  | { type: 'snapshot'; snapshot: Snapshot }
  | { type: 'event'; event: MainEvent }
  | { type: 'page'; page: AppPage }
  | { type: 'assistant'; open?: boolean }
  | { type: 'find'; open: boolean }
  | { type: 'site-info'; open?: boolean }
  | { type: 'notice'; notice: Omit<Notice, 'id'> | null };

const initial: UIState = {
  ready: false,
  nav: null,
  window: null,
  settings: null,
  ai: null,
  info: null,
  downloads: [],
  find: { active: false, matches: 0, activeMatch: 0 },
  page: 'portal',
  assistantOpen: false,
  findOpen: false,
  siteInfoOpen: false,
  notice: null,
  addressFocusToken: 0,
  revealToken: 0,
  askSelectionToken: 0,
};

let noticeId = 0;

function reducer(state: UIState, action: Action): UIState {
  switch (action.type) {
    case 'snapshot': {
      const s = action.snapshot;
      return {
        ...state,
        ready: true,
        nav: s.nav,
        window: s.window,
        settings: s.settings,
        ai: s.ai,
        info: s.info,
        page: s.initialPage,
      };
    }
    case 'page':
      return {
        ...state,
        page: action.page,
        siteInfoOpen: false,
        findOpen: action.page === 'portal' && state.findOpen,
      };
    case 'assistant':
      return { ...state, assistantOpen: action.open ?? !state.assistantOpen };
    case 'find':
      return { ...state, findOpen: action.open, page: action.open ? 'portal' : state.page };
    case 'site-info':
      return { ...state, siteInfoOpen: action.open ?? !state.siteInfoOpen };
    case 'notice':
      return { ...state, notice: action.notice ? { ...action.notice, id: ++noticeId } : null };
    case 'event': {
      const e = action.event;
      switch (e.type) {
        case 'nav':
          return { ...state, nav: e.nav };
        case 'window':
          return { ...state, window: e.window };
        case 'settings':
          return { ...state, settings: e.settings };
        case 'downloads':
          return { ...state, downloads: e.downloads };
        case 'find':
          return { ...state, find: e.find };
        case 'ai':
          return { ...state, ai: e.ai };
        case 'command': {
          const c = e.command;
          switch (c.type) {
            case 'toggle-ai':
              return { ...state, assistantOpen: !state.assistantOpen };
            case 'ask-selection':
              return {
                ...state,
                assistantOpen: true,
                askSelectionToken: state.askSelectionToken + 1,
              };
            case 'focus-address':
              return { ...state, addressFocusToken: state.addressFocusToken + 1 };
            case 'reveal-chrome':
              return { ...state, revealToken: state.revealToken + 1 };
            case 'open-find':
              return { ...state, findOpen: true, page: 'portal' };
            case 'close-overlays':
              return { ...state, findOpen: false, siteInfoOpen: false };
            case 'show-page':
              return { ...state, page: c.page };
            case 'notice':
              return {
                ...state,
                notice: {
                  id: ++noticeId,
                  level: c.level,
                  message: c.message,
                  popupOrigin: c.popupOrigin,
                },
              };
          }
        }
      }
    }
  }
  return state;
}

const Ctx = createContext<{ state: UIState; dispatch: (a: Action) => void } | null>(null);

export function StoreProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initial);

  useEffect(() => {
    const off = api.onEvent((event) => dispatch({ type: 'event', event }));
    void api.invoke('state:get').then((snapshot) => dispatch({ type: 'snapshot', snapshot }));
    void api
      .invoke('downloads:list')
      .then((downloads) => dispatch({ type: 'event', event: { type: 'downloads', downloads } }));
    return off;
  }, []);

  const value = useMemo(() => ({ state, dispatch }), [state]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useStore() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('StoreProvider missing');
  return ctx;
}
