import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  RotateCw,
  X,
  Star,
  Sparkles,
  Settings as SettingsIcon,
  Minus,
  Square,
  Copy,
  Lock,
  LockOpen,
  ShieldAlert,
  Monitor,
  Search,
  Maximize2,
  Minimize2,
  PanelLeft,
} from 'lucide-react';
import { useStore } from '../lib/store';
import { api } from '../lib/api';
import { displayUrl, type SecurityLevel } from '@shared/url';
import { Wordmark } from './Brand';
import { IconButton } from './ui';

const SECURITY: Record<SecurityLevel, { label: string; className: string }> = {
  secure: { label: 'Secure connection', className: 'sec--secure' },
  local: { label: 'Local page on this computer', className: 'sec--local' },
  insecure: { label: 'Not secure', className: 'sec--insecure' },
  'cert-error': { label: 'Certificate error', className: 'sec--danger' },
  internal: { label: 'Lapsus page', className: 'sec--local' },
  none: { label: 'No page loaded', className: 'sec--none' },
};

function SecurityIcon({ level }: { level: SecurityLevel }) {
  if (level === 'secure') return <Lock size={15} aria-hidden="true" />;
  if (level === 'insecure') return <LockOpen size={15} aria-hidden="true" />;
  if (level === 'cert-error') return <ShieldAlert size={15} aria-hidden="true" />;
  if (level === 'local' || level === 'internal') return <Monitor size={15} aria-hidden="true" />;
  return <Search size={15} aria-hidden="true" />;
}

export function AddressBar() {
  const { state, dispatch } = useStore();
  const nav = state.nav;
  const [value, setValue] = useState('');
  const [editing, setEditing] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const onPortalPage = state.page === 'portal';
  const shownUrl = onPortalPage && nav?.url ? displayUrl(nav.url) : '';

  useEffect(() => {
    if (!editing) setValue(shownUrl);
  }, [shownUrl, editing]);

  useEffect(() => {
    if (state.addressFocusToken > 0) {
      input.current?.focus();
      input.current?.select();
    }
  }, [state.addressFocusToken]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const text = value.trim();
    if (!text) return;
    dispatch({ type: 'page', page: 'portal' });
    void api.invoke('nav:go', { input: text });
    setEditing(false);
    input.current?.blur();
  };

  const level: SecurityLevel = onPortalPage && nav ? nav.security : 'none';
  const sec = SECURITY[level];

  return (
    <form className="address" onSubmit={submit} role="search">
      <button
        type="button"
        className={`address__security ${sec.className}`}
        onClick={() => onPortalPage && nav?.url && dispatch({ type: 'site-info' })}
        aria-label={`${sec.label}. Show site information`}
        title={sec.label}
      >
        <SecurityIcon level={level} />
        {level === 'insecure' && <span className="address__security-text">Not secure</span>}
      </button>
      <input
        ref={input}
        className="address__input"
        value={value}
        placeholder="Search or enter address"
        spellCheck={false}
        aria-label="Address and search bar"
        onFocus={(e) => {
          setEditing(true);
          e.currentTarget.select();
        }}
        onBlur={() => setEditing(false)}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            setValue(shownUrl);
            e.currentTarget.blur();
          }
        }}
      />
      {onPortalPage && nav?.url && (
        <button
          type="button"
          className={`address__star ${nav.bookmarked ? 'is-on' : ''}`}
          aria-label={nav.bookmarked ? 'Remove bookmark' : 'Bookmark this page'}
          title={nav.bookmarked ? 'Remove bookmark' : 'Bookmark this page (Ctrl+D shows bookmarks)'}
          onClick={() => void api.invoke('bookmarks:toggle-current')}
        >
          <Star size={16} fill={nav.bookmarked ? 'currentColor' : 'none'} aria-hidden="true" />
        </button>
      )}
    </form>
  );
}

export function WindowControls() {
  const { state } = useStore();
  const maximized = state.window?.maximized || state.window?.fullscreen;
  return (
    <div className="winctl" role="group" aria-label="Window controls">
      <button
        type="button"
        className="winctl__btn"
        aria-label="Minimize"
        title="Minimize"
        onClick={() => void api.invoke('window:minimize')}
      >
        <Minus size={16} strokeWidth={1.5} aria-hidden="true" />
      </button>
      <button
        type="button"
        className="winctl__btn"
        aria-label={maximized ? 'Restore down' : 'Maximize'}
        title={maximized ? 'Restore down' : 'Maximize'}
        onClick={() => void api.invoke('window:toggle-maximize')}
      >
        {maximized ? (
          <Copy size={14} strokeWidth={1.5} aria-hidden="true" />
        ) : (
          <Square size={13} strokeWidth={1.5} aria-hidden="true" />
        )}
      </button>
      <button
        type="button"
        className="winctl__btn winctl__btn--close"
        aria-label="Close"
        title="Close"
        onClick={() => void api.invoke('window:close')}
      >
        <X size={17} strokeWidth={1.5} aria-hidden="true" />
      </button>
    </div>
  );
}

export function TitleBar({ onToggleSidebar }: { onToggleSidebar: () => void }) {
  const { state, dispatch } = useStore();
  const nav = state.nav;
  const focus = state.window?.focusMode ?? false;
  return (
    <header className="titlebar">
      <div className="titlebar__left">
        <IconButton label="Toggle sidebar" className="icon-btn--on-dark" onClick={onToggleSidebar}>
          <PanelLeft size={18} aria-hidden="true" />
        </IconButton>
        <button
          type="button"
          className="titlebar__brand"
          onClick={() => dispatch({ type: 'page', page: 'home' })}
          aria-label="Lapsus home"
        >
          <Wordmark onDark />
        </button>
        <div className="titlebar__nav">
          <IconButton
            label="Back (Alt+Left)"
            className="icon-btn--on-dark"
            disabled={!nav?.canGoBack}
            onClick={() => {
              dispatch({ type: 'page', page: 'portal' });
              void api.invoke('nav:back');
            }}
          >
            <ArrowLeft size={18} aria-hidden="true" />
          </IconButton>
          <IconButton
            label="Forward (Alt+Right)"
            className="icon-btn--on-dark"
            disabled={!nav?.canGoForward}
            onClick={() => {
              dispatch({ type: 'page', page: 'portal' });
              void api.invoke('nav:forward');
            }}
          >
            <ArrowRight size={18} aria-hidden="true" />
          </IconButton>
          {nav?.loading ? (
            <IconButton
              label="Stop loading"
              className="icon-btn--on-dark"
              onClick={() => void api.invoke('nav:stop')}
            >
              <X size={18} aria-hidden="true" />
            </IconButton>
          ) : (
            <IconButton
              label="Reload (Ctrl+R)"
              className="icon-btn--on-dark"
              onClick={() => {
                dispatch({ type: 'page', page: 'portal' });
                void api.invoke('nav:reload', { ignoreCache: false });
              }}
            >
              <RotateCw size={17} aria-hidden="true" />
            </IconButton>
          )}
        </div>
      </div>

      <div className="titlebar__center">
        <AddressBar />
        {nav?.loading && (
          <div className="titlebar__progress" role="progressbar" aria-label="Page loading" />
        )}
      </div>

      <div className="titlebar__right">
        <IconButton
          label={focus ? 'Exit focus mode (Ctrl+Shift+F)' : 'Focus mode (Ctrl+Shift+F)'}
          className="icon-btn--on-dark"
          active={focus}
          onClick={() => {
            dispatch({ type: 'page', page: 'portal' });
            void api.invoke('window:set-focus-mode', { enabled: !focus });
          }}
        >
          {focus ? (
            <Minimize2 size={17} aria-hidden="true" />
          ) : (
            <Maximize2 size={17} aria-hidden="true" />
          )}
        </IconButton>
        <button
          type="button"
          className={`ai-trigger ${state.assistantOpen ? 'is-active' : ''}`}
          onClick={() => dispatch({ type: 'assistant' })}
          aria-pressed={state.assistantOpen}
          title={`Study Assistant (${state.settings?.aiShortcut ?? 'Ctrl+K'})`}
        >
          <Sparkles size={16} aria-hidden="true" />
          <span>Assistant</span>
          <kbd>{state.settings?.aiShortcut ?? 'Ctrl+K'}</kbd>
        </button>
        <IconButton
          label="Settings"
          className="icon-btn--on-dark"
          active={state.page === 'settings'}
          onClick={() => dispatch({ type: 'page', page: 'settings' })}
        >
          <SettingsIcon size={18} aria-hidden="true" />
        </IconButton>
        <WindowControls />
      </div>
    </header>
  );
}
