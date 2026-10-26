import { describe, expect, it } from 'vitest';
import { checkPermission, decidePermission } from '../../src/main/security/permission-policy';
import { isDangerousDownload, sanitizeFilename } from '../../src/main/browser/download-policy';
import { matchShortcut, type KeyInput } from '../../src/main/window/shortcuts';
import { DEFAULT_SETTINGS } from '@shared/settings';

describe('permission policy', () => {
  it('denies everything for insecure origins', () => {
    expect(decidePermission('fullscreen', 'http://example.com/')).toBe('deny');
    expect(decidePermission('media', 'file:///x')).toBe('deny');
  });
  it('auto-allows only harmless permissions', () => {
    expect(decidePermission('fullscreen', 'https://discipulusv2.amasystem.net/')).toBe('allow');
    expect(decidePermission('clipboard-sanitized-write', 'https://a.example/')).toBe('allow');
  });
  it('asks for camera, microphone, notifications, location', () => {
    for (const p of ['media', 'notifications', 'geolocation', 'clipboard-read']) {
      expect(decidePermission(p, 'https://a.example/')).toBe('prompt');
    }
  });
  it('denies device and system access', () => {
    for (const p of ['hid', 'serial', 'usb', 'midiSysex', 'openExternal', 'window-management', 'idle-detection', 'display-capture']) {
      expect(decidePermission(p, 'https://a.example/')).toBe('deny');
    }
  });
  it('check handler honours only granted prompts', () => {
    expect(checkPermission('media', 'https://a.example', new Set())).toBe(false);
    expect(checkPermission('media', 'https://a.example', new Set(['https://a.example|media']))).toBe(true);
    expect(checkPermission('media', 'https://b.example', new Set(['https://a.example|media']))).toBe(false);
  });
});

describe('download policy', () => {
  it('flags executable types', () => {
    for (const n of ['setup.exe', 'run.BAT', 'x.ps1', 'doc.pdf.js', 'archive.iso', 'macro.docm', 'shortcut.lnk']) {
      expect(isDangerousDownload(n)).toBe(true);
    }
    for (const n of ['notes.pdf', 'slides.pptx', 'data.csv', 'README', 'image.png']) {
      expect(isDangerousDownload(n)).toBe(false);
    }
  });
  it('sanitizes hostile filenames', () => {
    expect(sanitizeFilename('..\\..\\Windows\\evil.exe')).toBe('evil.exe');
    expect(sanitizeFilename('a<b>c:d"e|f?g*.txt')).toBe('a_b_c_d_e_f_g_.txt');
    expect(sanitizeFilename('CON')).toBe('download_CON');
    expect(sanitizeFilename('   ')).toBe('download');
    expect(sanitizeFilename('...hidden.')).toBe('hidden');
    const bidi = `invoice${String.fromCharCode(0x202e)}fdp.exe`;
    expect(sanitizeFilename(bidi)).toBe('invoicefdp.exe');
    expect(isDangerousDownload(sanitizeFilename(bidi))).toBe(true);
    expect(sanitizeFilename('x'.repeat(300) + '.pdf').length).toBeLessThanOrEqual(180);
  });
});

describe('shortcuts', () => {
  const key = (k: string, mods: Partial<KeyInput> = {}): KeyInput => ({
    type: 'keyDown',
    key: k,
    control: false,
    shift: false,
    alt: false,
    meta: false,
    ...mods,
  });
  const s = DEFAULT_SETTINGS;

  it('Ctrl+K toggles the assistant by default', () => {
    expect(matchShortcut(key('k', { control: true }), s)).toBe('toggle-ai');
    expect(matchShortcut(key('K', { control: true }), s)).toBe('toggle-ai');
  });
  it('the assistant shortcut is configurable and can be disabled', () => {
    const alt = { ...s, aiShortcut: 'Ctrl+Shift+K' as const };
    expect(matchShortcut(key('k', { control: true }), alt)).toBeNull();
    expect(matchShortcut(key('k', { control: true, shift: true }), alt)).toBe('toggle-ai');
    expect(matchShortcut(key('k', { control: true }), { ...s, aiShortcutEnabled: false })).toBeNull();
  });
  it('maps browser shortcuts', () => {
    expect(matchShortcut(key('f', { control: true }), s)).toBe('find');
    expect(matchShortcut(key('F', { control: true, shift: true }), s)).toBe('toggle-focus-mode');
    expect(matchShortcut(key('F11'), s)).toBe('toggle-fullscreen');
    expect(matchShortcut(key('ArrowLeft', { alt: true }), s)).toBe('back');
    expect(matchShortcut(key('Escape'), s)).toBe('escape');
  });
  it('leaves ordinary typing and page keys alone', () => {
    for (const k of ['a', 'k', 'Enter', 'Tab', ' ', 'ArrowLeft']) expect(matchShortcut(key(k), s)).toBeNull();
    expect(matchShortcut(key('c', { control: true }), s)).toBeNull();
    expect(matchShortcut(key('v', { control: true }), s)).toBeNull();
    expect(matchShortcut({ ...key('k', { control: true }), type: 'keyUp' }, s)).toBeNull();
  });
});
