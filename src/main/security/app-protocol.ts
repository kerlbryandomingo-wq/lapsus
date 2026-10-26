import { net, protocol, type Session } from 'electron';
import { join, normalize, relative, isAbsolute } from 'node:path';
import { pathToFileURL } from 'node:url';
import { APP_HOST, APP_SCHEME } from '@shared/constants';

/** CSP for application-owned pages. No remote scripts, no inline scripts, no framing. */
export const APP_CSP = [
  "default-src 'none'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data:",
  "font-src 'self'",
  "connect-src 'self'",
  "base-uri 'none'",
  "form-action 'none'",
  "frame-ancestors 'none'",
  "object-src 'none'",
].join('; ');

/** Must run before app is ready. */
export function registerAppScheme(): void {
  protocol.registerSchemesAsPrivileged([
    { scheme: APP_SCHEME, privileges: { standard: true, secure: true, supportFetchAPI: true } },
  ]);
}

/**
 * Serves the bundled UI from disk at app://lapsus/. Requests for other hosts or for paths
 * outside the renderer folder are refused.
 */
export function serveAppProtocol(ses: Session, rendererDir: string): void {
  ses.protocol.handle(APP_SCHEME, async (request) => {
    const url = new URL(request.url);
    if (url.host !== APP_HOST) return new Response('Not found', { status: 404 });
    const pathname = decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname);
    const target = normalize(join(rendererDir, pathname));
    const rel = relative(rendererDir, target);
    if (rel.startsWith('..') || isAbsolute(rel)) return new Response('Forbidden', { status: 403 });
    const res = await net.fetch(pathToFileURL(target).toString());
    const headers = new Headers(res.headers);
    headers.set('Content-Security-Policy', APP_CSP);
    headers.set('X-Content-Type-Options', 'nosniff');
    return new Response(res.body, { status: res.status, headers });
  });
}
