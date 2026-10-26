import { useEffect, useRef, useState } from 'react';
import { ChevronUp, ChevronDown, X, Info, AlertTriangle, CircleAlert, CircleCheck, Lock } from 'lucide-react';
import { useStore } from '../lib/store';
import { api } from '../lib/api';
import { hostOf } from '../lib/format';
import { IconButton } from './ui';

export function NoticeBand() {
  const { state, dispatch } = useStore();
  const notice = state.notice;
  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => dispatch({ type: 'notice', notice: null }), notice.level === 'error' ? 9000 : 5000);
    return () => clearTimeout(t);
  }, [notice, dispatch]);
  if (!notice) return null;
  const Icon = notice.level === 'error' ? CircleAlert : notice.level === 'warning' ? AlertTriangle : notice.level === 'success' ? CircleCheck : Info;
  return (
    <div className={`band band--${notice.level}`} role={notice.level === 'error' ? 'alert' : 'status'}>
      <Icon size={16} aria-hidden="true" />
      <span className="band__text">{notice.message}</span>
      <IconButton label="Dismiss" onClick={() => dispatch({ type: 'notice', notice: null })}>
        <X size={15} aria-hidden="true" />
      </IconButton>
    </div>
  );
}

export function FindBar() {
  const { state, dispatch } = useStore();
  const [text, setText] = useState('');
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (state.findOpen) {
      input.current?.focus();
      input.current?.select();
    }
  }, [state.findOpen]);
  if (!state.findOpen) return null;

  const run = (forward: boolean, findNext: boolean) => {
    if (text) void api.invoke('find:start', { text, forward, findNext });
  };
  const close = () => {
    void api.invoke('find:stop');
    dispatch({ type: 'find', open: false });
  };
  const { matches, activeMatch } = state.find;

  return (
    <div className="band band--find" role="search">
      <input
        ref={input}
        className="find__input"
        value={text}
        placeholder="Find in page"
        aria-label="Find in page"
        onChange={(e) => {
          setText(e.target.value);
          if (e.target.value) void api.invoke('find:start', { text: e.target.value, forward: true, findNext: false });
          else void api.invoke('find:stop');
        }}
        onKeyDown={(e) => {
          if (e.key === 'Enter') run(!e.shiftKey, true);
          if (e.key === 'Escape') close();
        }}
      />
      <span className="find__count" aria-live="polite">
        {text ? (matches ? `${activeMatch} of ${matches}` : 'No matches') : ''}
      </span>
      <IconButton label="Previous match (Shift+Enter)" onClick={() => run(false, true)} disabled={!matches}>
        <ChevronUp size={16} aria-hidden="true" />
      </IconButton>
      <IconButton label="Next match (Enter)" onClick={() => run(true, true)} disabled={!matches}>
        <ChevronDown size={16} aria-hidden="true" />
      </IconButton>
      <IconButton label="Close find (Esc)" onClick={close}>
        <X size={16} aria-hidden="true" />
      </IconButton>
    </div>
  );
}

export function SiteInfoBand() {
  const { state, dispatch } = useStore();
  const nav = state.nav;
  if (!state.siteInfoOpen || !nav || state.page !== 'portal') return null;
  const cert = nav.certificate;
  let headline: string;
  let detail: string;
  switch (nav.security) {
    case 'secure':
      headline = `Connection to ${hostOf(nav.url)} is encrypted (HTTPS).`;
      detail = cert
        ? `Certificate issued to ${cert.subject} by ${cert.issuer}, valid until ${new Date(cert.validExpiry * 1000).toLocaleDateString()}. Verified by Chromium.`
        : 'The certificate was verified by Chromium.';
      break;
    case 'insecure':
      headline = `Connection to ${hostOf(nav.url)} is not encrypted.`;
      detail = 'Do not enter passwords or personal information on this page.';
      break;
    case 'cert-error':
      headline = 'The certificate for this site could not be verified.';
      detail = 'Lapsus refused to load the page. This can mean the connection is being intercepted.';
      break;
    case 'local':
      headline = 'This page is served from your own computer.';
      detail = '';
      break;
    default:
      headline = 'No site information.';
      detail = '';
  }
  return (
    <div className={`band band--site ${nav.security === 'secure' ? 'band--site-secure' : 'band--warning'}`} role="status">
      <Lock size={16} aria-hidden="true" />
      <span className="band__text">
        <strong>{headline}</strong> {detail}
      </span>
      <IconButton label="Close site information" onClick={() => dispatch({ type: 'site-info', open: false })}>
        <X size={15} aria-hidden="true" />
      </IconButton>
    </div>
  );
}
