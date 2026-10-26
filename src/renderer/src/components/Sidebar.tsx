import {
  Home,
  GraduationCap,
  Bookmark,
  History,
  Download,
  Sparkles,
  Settings,
  Info,
} from 'lucide-react';
import type { ReactNode } from 'react';
import type { AppPage } from '@shared/types';
import { useStore } from '../lib/store';

interface Item {
  page: AppPage | 'assistant';
  label: string;
  icon: ReactNode;
}

const MAIN: Item[] = [
  { page: 'home', label: 'Home', icon: <Home size={19} aria-hidden="true" /> },
  { page: 'portal', label: 'Student Portal', icon: <GraduationCap size={19} aria-hidden="true" /> },
  { page: 'bookmarks', label: 'Bookmarks', icon: <Bookmark size={19} aria-hidden="true" /> },
  { page: 'history', label: 'History', icon: <History size={19} aria-hidden="true" /> },
  { page: 'downloads', label: 'Downloads', icon: <Download size={19} aria-hidden="true" /> },
  { page: 'assistant', label: 'Study Assistant', icon: <Sparkles size={19} aria-hidden="true" /> },
];

const FOOTER: Item[] = [
  { page: 'settings', label: 'Settings', icon: <Settings size={19} aria-hidden="true" /> },
  { page: 'about', label: 'About Lapsus', icon: <Info size={19} aria-hidden="true" /> },
];

export function Sidebar({ collapsed }: { collapsed: boolean }) {
  const { state, dispatch } = useStore();
  const active = (item: Item) =>
    item.page === 'assistant' ? state.assistantOpen : state.page === item.page;
  const activeDownloads = state.downloads.filter((d) => d.state === 'progressing').length;

  const render = (item: Item) => (
    <li key={item.page}>
      <button
        type="button"
        className={`nav-item ${active(item) ? 'is-active' : ''}`}
        aria-current={item.page !== 'assistant' && active(item) ? 'page' : undefined}
        title={collapsed ? item.label : undefined}
        onClick={() =>
          item.page === 'assistant'
            ? dispatch({ type: 'assistant' })
            : dispatch({ type: 'page', page: item.page })
        }
      >
        <span className="nav-item__icon">{item.icon}</span>
        <span className="nav-item__label">{item.label}</span>
        {item.page === 'downloads' && activeDownloads > 0 && (
          <span className="nav-item__badge">{activeDownloads}</span>
        )}
      </button>
    </li>
  );

  return (
    <nav className={`sidebar ${collapsed ? 'is-collapsed' : ''}`} aria-label="Lapsus navigation">
      <ul className="sidebar__list">{MAIN.map(render)}</ul>
      <ul className="sidebar__list sidebar__list--footer">{FOOTER.map(render)}</ul>
    </nav>
  );
}
