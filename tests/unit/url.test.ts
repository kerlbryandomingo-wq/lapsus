import { describe, expect, it } from 'vitest';
import {
  displayUrl,
  evaluateNavigation,
  isPortalHost,
  isSafeExternalUrl,
  normalizeAddressInput,
  securityLevelFor,
  type NavigationContext,
} from '@shared/url';

const ctx: NavigationContext = { portalOnly: false, extraPortalHosts: [], allowedHttpOrigins: new Set() };

describe('normalizeAddressInput', () => {
  it('adds https to bare domains', () => {
    expect(normalizeAddressInput('discipulusv2.amasystem.net', 'google')).toBe('https://discipulusv2.amasystem.net/');
    expect(normalizeAddressInput('example.com/path?q=1', 'google')).toBe('https://example.com/path?q=1');
  });
  it('uses http for loopback hosts', () => {
    expect(normalizeAddressInput('localhost:3000', 'google')).toBe('http://localhost:3000/');
    expect(normalizeAddressInput('127.0.0.1:8080/x', 'google')).toBe('http://127.0.0.1:8080/x');
  });
  it('keeps explicit schemes for the policy to judge', () => {
    expect(normalizeAddressInput('https://a.example/', 'google')).toBe('https://a.example/');
    expect(normalizeAddressInput('javascript:alert(1)', 'google')).toBe('javascript:alert(1)');
  });
  it('searches for everything else', () => {
    expect(normalizeAddressInput('what is a queue', 'google')).toBe('https://www.google.com/search?q=what%20is%20a%20queue');
    expect(normalizeAddressInput('fifo', 'duckduckgo')).toBe('https://duckduckgo.com/?q=fifo');
  });
  it('returns null for empty input', () => {
    expect(normalizeAddressInput('   ', 'google')).toBeNull();
  });
});

describe('evaluateNavigation', () => {
  it('allows https pages', () => {
    expect(evaluateNavigation('https://discipulusv2.amasystem.net/login', ctx)).toEqual({ action: 'allow' });
  });

  it.each([
    'file:///C:/Windows/System32/drivers/etc/hosts',
    'javascript:alert(document.cookie)',
    'JaVaScRiPt:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'blob:https://evil.example/uuid',
    'chrome://settings',
    'devtools://devtools/bundled/inspector.html',
    'app://lapsus/index.html',
    'about:config',
    'vbscript:msgbox(1)',
    'ms-settings:privacy',
    'smb://attacker/share',
  ])('blocks dangerous scheme %s', (url) => {
    expect(evaluateNavigation(url, ctx)).toMatchObject({ action: 'block' });
  });

  it('blocks malformed URLs', () => {
    expect(evaluateNavigation('http://', ctx)).toMatchObject({ action: 'block' });
    expect(evaluateNavigation('not a url', ctx)).toMatchObject({ action: 'block', reason: 'invalid' });
  });

  it('blocks URLs with embedded credentials (phishing pattern)', () => {
    expect(evaluateNavigation('https://discipulusv2.amasystem.net@evil.example/', ctx)).toMatchObject({
      action: 'block',
      reason: 'credentials',
    });
  });

  it('blocks plain http unless loopback or explicitly allowed', () => {
    expect(evaluateNavigation('http://example.com/', ctx)).toMatchObject({ action: 'block', reason: 'insecure' });
    expect(evaluateNavigation('http://127.0.0.1:5000/', ctx)).toEqual({ action: 'allow' });
    const allowed = { ...ctx, allowedHttpOrigins: new Set(['http://example.com']) };
    expect(evaluateNavigation('http://example.com/page', allowed)).toEqual({ action: 'allow' });
  });

  it('hands mail and phone links to the OS', () => {
    expect(evaluateNavigation('mailto:registrar@example.edu', ctx)).toMatchObject({ action: 'external' });
  });

  it('sends non-portal sites outside in portal-only mode', () => {
    const portalOnly = { ...ctx, portalOnly: true };
    expect(evaluateNavigation('https://discipulusv2.amasystem.net/', portalOnly)).toEqual({ action: 'allow' });
    expect(evaluateNavigation('https://www.youtube.com/watch?v=1', portalOnly)).toMatchObject({ action: 'external' });
    expect(evaluateNavigation('https://amasystem.net.evil.example/', portalOnly)).toMatchObject({ action: 'external' });
    const extra = { ...portalOnly, extraPortalHosts: ['youtube.com'] };
    expect(evaluateNavigation('https://www.youtube.com/watch?v=1', extra)).toEqual({ action: 'allow' });
  });
});

describe('helpers', () => {
  it('matches portal hosts by registrable domain only', () => {
    expect(isPortalHost('discipulusv2.amasystem.net')).toBe(true);
    expect(isPortalHost('amasystem.net')).toBe(true);
    expect(isPortalHost('notamasystem.net')).toBe(false);
    expect(isPortalHost('amasystem.net.attacker.io')).toBe(false);
  });
  it('only allows safe external schemes', () => {
    expect(isSafeExternalUrl('https://example.com')).toBe(true);
    expect(isSafeExternalUrl('mailto:a@b.c')).toBe(true);
    expect(isSafeExternalUrl('file:///C:/x.exe')).toBe(false);
    expect(isSafeExternalUrl('ms-msdt:/id')).toBe(false);
    expect(isSafeExternalUrl('https://user:pw@example.com')).toBe(false);
  });
  it('reports security level from the real URL and certificate state', () => {
    expect(securityLevelFor('https://a.example/', false)).toBe('secure');
    expect(securityLevelFor('https://a.example/', true)).toBe('cert-error');
    expect(securityLevelFor('http://a.example/', false)).toBe('insecure');
    expect(securityLevelFor('http://localhost:1/', false)).toBe('local');
  });
  it('shortens bare origins for display', () => {
    expect(displayUrl('https://a.example/')).toBe('https://a.example');
    expect(displayUrl('https://a.example/x?y=1')).toBe('https://a.example/x?y=1');
  });
});
