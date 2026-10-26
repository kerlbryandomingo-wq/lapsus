import { useEffect, useState } from 'react';
import { useStore } from './lib/store';
import { useViewport } from './lib/useViewport';
import { api } from './lib/api';
import { TitleBar } from './components/TitleBar';
import { Sidebar } from './components/Sidebar';
import { FindBar, NoticeBand, SiteInfoBand } from './components/Bands';
import { HomePage } from './pages/HomePage';
import { PortalPage } from './pages/PortalPage';
import { BookmarksPage } from './pages/BookmarksPage';
import { HistoryPage } from './pages/HistoryPage';
import { DownloadsPage } from './pages/DownloadsPage';
import { SettingsPage } from './pages/SettingsPage';
import { AboutPage } from './pages/AboutPage';
import { AssistantPanel } from './features/ai/AssistantPanel';
import { LapsusMark } from './components/Brand';

const NARROW = 1100;

function useNarrow() {
  const [narrow, setNarrow] = useState(window.innerWidth < NARROW);
  useEffect(() => {
    const onResize = () => setNarrow(window.innerWidth < NARROW);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return narrow;
}

function useTheme(theme: 'system' | 'light' | 'dark' | undefined) {
  useEffect(() => {
    if (!theme || theme === 'system') document.documentElement.removeAttribute('data-theme');
    else document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);
}

export function App() {
  const { state } = useStore();
  const narrow = useNarrow();
  const focusMode = state.window?.focusMode ?? false;
  const pageFullscreen = state.window?.pageFullscreen ?? false;
  const [revealed, setRevealed] = useState(false);
  const collapsed = (state.settings?.sidebarCollapsed ?? false) || narrow;
  const showChrome = !focusMode || revealed;
  const pageVisible = state.ready && state.page === 'portal' && !state.nav?.error;
  const viewport = useViewport(pageVisible);

  useTheme(state.settings?.theme);

  useEffect(() => {
    if (!focusMode) setRevealed(false);
  }, [focusMode]);

  if (!state.ready) {
    return (
      <div className="splash">
        <LapsusMark size={56} />
      </div>
    );
  }

  const toggleSidebar = () => void api.invoke('settings:update', { sidebarCollapsed: !state.settings?.sidebarCollapsed });

  let content;
  switch (state.page) {
    case 'home':
      content = <HomePage />;
      break;
    case 'portal':
      content = <PortalPage ref={viewport} />;
      break;
    case 'bookmarks':
      content = <BookmarksPage />;
      break;
    case 'history':
      content = <HistoryPage />;
      break;
    case 'downloads':
      content = <DownloadsPage />;
      break;
    case 'settings':
      content = <SettingsPage />;
      break;
    case 'about':
      content = <AboutPage />;
      break;
  }

  return (
    <div className={`app ${focusMode ? 'app--focus' : ''} ${state.assistantOpen ? 'app--assistant' : ''}`}>
      {pageFullscreen && (
        <div className="fullscreen-hint" role="status">
          The page is in full screen. Press <kbd>Esc</kbd> to exit.
        </div>
      )}
      {focusMode && !revealed && (
        <div
          className="reveal-strip"
          onMouseEnter={() => setRevealed(true)}
          role="button"
          tabIndex={0}
          aria-label="Show Lapsus controls"
          onFocus={() => setRevealed(true)}
        />
      )}
      {showChrome && (
        <div
          className="chrome-top"
          onMouseLeave={() => {
            if (focusMode) setTimeout(() => setRevealed(false), 900);
          }}
        >
          <TitleBar onToggleSidebar={toggleSidebar} />
        </div>
      )}
      <div className="bands">
        <NoticeBand />
        <FindBar />
        <SiteInfoBand />
      </div>
      <div className="workspace">
        {!focusMode && <Sidebar collapsed={collapsed} />}
        <main className="content" id="content">
          {content}
        </main>
        {state.assistantOpen && <AssistantPanel />}
      </div>
    </div>
  );
}
