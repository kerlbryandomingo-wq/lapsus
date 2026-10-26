import { PORTAL_DOMAINS } from './constants';

export type SearchEngine = 'google' | 'bing' | 'duckduckgo';

export const SEARCH_ENGINES: Record<SearchEngine, { label: string; template: string }> = {
  google: { label: 'Google', template: 'https://www.google.com/search?q=%s' },
  bing: { label: 'Bing', template: 'https://www.bing.com/search?q=%s' },
  duckduckgo: { label: 'DuckDuckGo', template: 'https://duckduckgo.com/?q=%s' },
};

const SCHEME_RE = /^[a-zA-Z][a-zA-Z0-9+.-]*:/;
const HOST_LIKE_RE =
  /^(localhost|\[[0-9a-fA-F:]+\]|\d{1,3}(?:\.\d{1,3}){3}|(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,63})(:\d{1,5})?([/?#].*)?$/;

export function isLoopbackHost(hostname: string): boolean {
  const h = hostname.toLowerCase();
  return h === 'localhost' || h === '[::1]' || h === '::1' || /^127(?:\.\d{1,3}){3}$/.test(h);
}

/**
 * Turns what the user typed into the address bar into a URL. Returns null for empty input.
 * The result is not yet approved: callers must still run it through evaluateNavigation.
 */
export function normalizeAddressInput(raw: string, engine: SearchEngine): string | null {
  const input = raw.trim();
  if (!input) return null;

  const hostMatch = HOST_LIKE_RE.exec(input);
  if (hostMatch && !/\s/.test(input)) {
    const host = hostMatch[1] ?? '';
    const scheme = isLoopbackHost(host) ? 'http' : 'https';
    try {
      return new URL(`${scheme}://${input}`).toString();
    } catch {
      /* fall through to search */
    }
  }

  if (SCHEME_RE.test(input) && !/\s/.test(input)) {
    try {
      return new URL(input).toString();
    } catch {
      /* fall through to search */
    }
  }

  return SEARCH_ENGINES[engine].template.replace('%s', encodeURIComponent(input));
}

export function isPortalHost(hostname: string, extraHosts: readonly string[] = []): boolean {
  const h = hostname.toLowerCase().replace(/\.$/, '');
  return [...PORTAL_DOMAINS, ...extraHosts].some((d) => {
    const domain = d.toLowerCase();
    return h === domain || h.endsWith(`.${domain}`);
  });
}

export interface NavigationContext {
  portalOnly: boolean;
  extraPortalHosts: readonly string[];
  /** Origins the user explicitly chose to open over plain HTTP this session. */
  allowedHttpOrigins: ReadonlySet<string>;
}

export type NavigationDecision =
  | { action: 'allow' }
  | { action: 'external'; url: string }
  | { action: 'block'; reason: 'invalid' | 'scheme' | 'insecure' | 'credentials' };

/** Policy for top-level navigation in the browsing view. Pure, so it is unit-tested directly. */
export function evaluateNavigation(rawUrl: string, ctx: NavigationContext): NavigationDecision {
  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    return { action: 'block', reason: 'invalid' };
  }

  switch (url.protocol) {
    case 'https:':
    case 'http:': {
      if (url.username || url.password) return { action: 'block', reason: 'credentials' };
      if (!url.hostname) return { action: 'block', reason: 'invalid' };
      if (url.protocol === 'http:') {
        const allowed = isLoopbackHost(url.hostname) || ctx.allowedHttpOrigins.has(url.origin);
        if (!allowed) return { action: 'block', reason: 'insecure' };
      }
      if (ctx.portalOnly && !isLoopbackHost(url.hostname)) {
        if (!isPortalHost(url.hostname, ctx.extraPortalHosts)) {
          return { action: 'external', url: url.toString() };
        }
      }
      return { action: 'allow' };
    }
    case 'about:':
      return url.href === 'about:blank'
        ? { action: 'allow' }
        : { action: 'block', reason: 'scheme' };
    case 'mailto:':
    case 'tel:':
      return { action: 'external', url: url.toString() };
    default:
      // file:, javascript:, data:, blob:, chrome:, devtools:, app:, and anything unknown.
      return { action: 'block', reason: 'scheme' };
  }
}

/** URLs Lapsus may hand to the operating system's default handler. */
export function isSafeExternalUrl(rawUrl: string): boolean {
  try {
    const url = new URL(rawUrl);
    if (url.username || url.password) return false;
    return ['https:', 'http:', 'mailto:', 'tel:'].includes(url.protocol);
  } catch {
    return false;
  }
}

export type SecurityLevel = 'secure' | 'local' | 'insecure' | 'cert-error' | 'internal' | 'none';

export function securityLevelFor(rawUrl: string, certError: boolean): SecurityLevel {
  if (certError) return 'cert-error';
  try {
    const url = new URL(rawUrl);
    if (url.protocol === 'https:') return 'secure';
    if (url.protocol === 'http:') return isLoopbackHost(url.hostname) ? 'local' : 'insecure';
    if (url.href === 'about:blank') return 'none';
    return 'internal';
  } catch {
    return 'none';
  }
}

/** Display form of a URL for the address bar: drops the trailing slash on bare origins. */
export function displayUrl(rawUrl: string): string {
  try {
    const url = new URL(rawUrl);
    if (url.pathname === '/' && !url.search && !url.hash) return `${url.protocol}//${url.host}`;
    return url.toString();
  } catch {
    return rawUrl;
  }
}
