import { useEffect, useState } from 'react';
import {
  ArrowRight,
  Bookmark as BookmarkIcon,
  Clock,
  Download,
  Maximize2,
  Sparkles,
  Globe,
  History as HistoryIcon,
} from 'lucide-react';
import type { Bookmark, HistoryEntry } from '@shared/types';
import { DEFAULT_PORTAL_URL } from '@shared/constants';
import { useStore } from '../lib/store';
import { api } from '../lib/api';
import { greeting, hostOf, relativeTime } from '../lib/format';
import { Button, EmptyState, Pill, ProgressRing, SectionHeader, Kbd } from '../components/ui';
import amaShield from '../assets/ama-shield.png';

function SiteAvatar({ url, tone = 'navy' }: { url: string; tone?: 'navy' | 'maroon' | 'gold' }) {
  const letter = hostOf(url).charAt(0).toUpperCase() || '?';
  return (
    <span className={`avatar avatar--${tone}`} aria-hidden="true">
      {letter}
    </span>
  );
}

export function HomePage() {
  const { state, dispatch } = useStore();
  const [recent, setRecent] = useState<HistoryEntry[]>([]);
  const [bookmarks, setBookmarks] = useState<Bookmark[]>([]);
  const historyEnabled = state.settings?.historyEnabled ?? true;

  useEffect(() => {
    if (historyEnabled) void api.invoke('history:list', { query: '', limit: 6 }).then(setRecent);
    else setRecent([]);
    void api.invoke('bookmarks:list').then((b) => setBookmarks(b.slice(0, 6)));
  }, [historyEnabled, state.nav?.bookmarked]);

  const name = state.settings?.displayName?.trim();
  const ai = state.ai;
  const usage = ai?.usage;
  const portalVisited = recent.find((r) => hostOf(r.url).endsWith('amasystem.net'));
  const openPortal = (focus = false) => {
    dispatch({ type: 'page', page: 'portal' });
    if (!state.nav?.url || !hostOf(state.nav.url).endsWith('amasystem.net')) void api.invoke('nav:portal');
    if (focus) void api.invoke('window:set-focus-mode', { enabled: true });
  };

  return (
    <div className="home">
      <div className="home__inner">
        <header className="home__header">
          <div>
            <p className="home__eyebrow">{new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}</p>
            <h1 className="home__title">
              {greeting()}
              {name ? `, ${name}` : ''}
            </h1>
          </div>
        </header>

        <section className="hero" aria-labelledby="hero-title">
          <div className="hero__body">
            <div className="hero__pills">
              <Pill tone="gold">Student Portal</Pill>
              <Pill tone="maroon">Default home</Pill>
            </div>
            <h2 id="hero-title" className="hero__title">
              AMA Discipulus
            </h2>
            <p className="hero__url">{hostOf(DEFAULT_PORTAL_URL)}</p>
            <p className="hero__text">Courses, learning materials, assessments and your account, in a focused window.</p>
            <div className="hero__actions">
              <Button variant="accent" size="lg" onClick={() => openPortal(false)} icon={<ArrowRight size={18} />}>
                Continue to Discipulus
              </Button>
              <Button variant="on-dark" size="lg" onClick={() => openPortal(true)} icon={<Maximize2 size={17} />}>
                Open in focus mode
              </Button>
            </div>
            <p className="hero__meta">
              <Clock size={13} aria-hidden="true" />
              {portalVisited ? `Last opened ${relativeTime(portalVisited.visitedAt)}` : 'Not opened yet on this computer'}
            </p>
          </div>
          <div className="hero__badge" aria-hidden="true">
            <div className="hero__shield">
              <img src={amaShield} alt="" draggable={false} />
            </div>
          </div>
        </section>

        <div className="home__grid">
          <section className="home__col home__col--wide">
            <SectionHeader
              title="Recent pages"
              action={
                historyEnabled && recent.length > 0 ? (
                  <Button variant="ghost" size="sm" onClick={() => dispatch({ type: 'page', page: 'history' })}>
                    View history
                  </Button>
                ) : undefined
              }
            />
            {recent.length === 0 ? (
              <EmptyState icon={<HistoryIcon size={22} />} title={historyEnabled ? 'No recent pages yet' : 'History is turned off'}>
                {historyEnabled ? 'Pages you open will appear here.' : 'Turn on history in Settings to see recent pages.'}
              </EmptyState>
            ) : (
              <ul className="list-cards">
                {recent.map((entry) => (
                  <li key={entry.id}>
                    <button
                      type="button"
                      className="list-card"
                      onClick={() => {
                        dispatch({ type: 'page', page: 'portal' });
                        void api.invoke('history:open', { id: entry.id });
                      }}
                    >
                      <SiteAvatar url={entry.url} />
                      <span className="list-card__main">
                        <span className="list-card__title">{entry.title || hostOf(entry.url)}</span>
                        <span className="list-card__meta">
                          <Globe size={12} aria-hidden="true" /> {hostOf(entry.url)}
                          <span className="dot" aria-hidden="true" />
                          <Clock size={12} aria-hidden="true" /> {relativeTime(entry.visitedAt)}
                        </span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <SectionHeader
              title="Bookmarks"
              action={
                bookmarks.length > 0 ? (
                  <Button variant="ghost" size="sm" onClick={() => dispatch({ type: 'page', page: 'bookmarks' })}>
                    Manage
                  </Button>
                ) : undefined
              }
            />
            {bookmarks.length === 0 ? (
              <EmptyState icon={<BookmarkIcon size={22} />} title="No bookmarks yet">
                Use the star in the address bar to save pages you use often.
              </EmptyState>
            ) : (
              <ul className="tile-grid">
                {bookmarks.map((b) => (
                  <li key={b.id}>
                    <button
                      type="button"
                      className="tile"
                      onClick={() => {
                        dispatch({ type: 'page', page: 'portal' });
                        void api.invoke('bookmarks:open', { id: b.id });
                      }}
                    >
                      <SiteAvatar url={b.url} tone="maroon" />
                      <span className="tile__title">{b.title}</span>
                      <span className="tile__meta">{hostOf(b.url)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <aside className="home__col">
            <section className="ai-card" aria-labelledby="ai-card-title">
              <div className="ai-card__top">
                <div>
                  <Pill tone="ai">
                    <Sparkles size={12} aria-hidden="true" /> AI
                  </Pill>
                  <h2 id="ai-card-title" className="ai-card__title">
                    Study Assistant
                  </h2>
                </div>
                <ProgressRing
                  value={usage && usage.limit > 0 ? usage.requests / usage.limit : 0}
                  label={`AI requests this month: ${usage?.requests ?? 0} of ${usage?.limit ?? 0}`}
                  size={64}
                  stroke={7}
                >
                  <span className="ring__num">{usage?.requests ?? 0}</span>
                </ProgressRing>
              </div>
              <p className="ai-card__text">
                Paste or select a practice question to get an explanation of the concept and the reasoning behind each option.
              </p>
              <p className="ai-card__status">
                <span className={`status-dot ${ai?.ready ? 'is-ok' : 'is-off'}`} aria-hidden="true" />
                {ai?.message ?? 'Checking…'}
              </p>
              <div className="ai-card__actions">
                <Button variant="primary" onClick={() => dispatch({ type: 'assistant', open: true })} icon={<Sparkles size={16} />}>
                  Open assistant
                </Button>
                <Kbd>{state.settings?.aiShortcut ?? 'Ctrl+K'}</Kbd>
              </div>
              <p className="ai-card__foot">
                {usage ? `${usage.requests} of ${usage.limit} requests used this month` : ''}
              </p>
            </section>

            <section>
              <SectionHeader title="Quick actions" />
              <div className="quick-grid">
                <button type="button" className="quick" onClick={() => dispatch({ type: 'page', page: 'downloads' })}>
                  <Download size={18} aria-hidden="true" />
                  <span>Downloads</span>
                </button>
                <button type="button" className="quick" onClick={() => dispatch({ type: 'page', page: 'bookmarks' })}>
                  <BookmarkIcon size={18} aria-hidden="true" />
                  <span>Bookmarks</span>
                </button>
                <button type="button" className="quick" onClick={() => dispatch({ type: 'page', page: 'history' })}>
                  <HistoryIcon size={18} aria-hidden="true" />
                  <span>History</span>
                </button>
                <button type="button" className="quick" onClick={() => openPortal(true)}>
                  <Maximize2 size={18} aria-hidden="true" />
                  <span>Focus mode</span>
                </button>
              </div>
            </section>
          </aside>
        </div>

        <footer className="statusbar">
          <span>
            Lapsus {state.info?.version} by {state.info?.publisher}
          </span>
          <span className="dot" aria-hidden="true" />
          <span>{navigator.onLine ? 'Online' : 'Offline'}</span>
          <span className="dot" aria-hidden="true" />
          <span>AI: {ai?.provider === 'none' ? 'not configured' : ai?.ready ? 'ready' : 'needs setup'}</span>
          <span className="statusbar__spacer" />
          <span>Independent student tool. Not affiliated with AMA Education System.</span>
        </footer>
      </div>
    </div>
  );
}
