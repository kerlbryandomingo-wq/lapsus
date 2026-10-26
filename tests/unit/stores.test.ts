import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { BookmarkStore, HistoryStore, SettingsStore } from '../../src/main/store/stores';
import { coerceSettings, DEFAULT_SETTINGS } from '@shared/settings';

const dir = () => mkdtempSync(join(tmpdir(), 'lapsus-test-'));

describe('settings', () => {
  it('falls back to defaults per key for corrupt values', () => {
    const s = coerceSettings({ theme: 'neon', historyEnabled: false, nodeIntegration: true, aiMonthlyRequestLimit: -5 });
    expect(s.theme).toBe(DEFAULT_SETTINGS.theme);
    expect(s.historyEnabled).toBe(false);
    expect(s.aiMonthlyRequestLimit).toBe(DEFAULT_SETTINGS.aiMonthlyRequestLimit);
    expect('nodeIntegration' in s).toBe(false);
  });
  it('survives a corrupt settings file', () => {
    const d = dir();
    writeFileSync(join(d, 'settings.json'), '{not json');
    expect(new SettingsStore(d).get()).toEqual(DEFAULT_SETTINGS);
  });
  it('rejects unknown keys in updates', () => {
    const store = new SettingsStore(dir());
    expect(() => store.update({ evil: 1 } as never)).toThrow();
  });
  it('defaults keep risky features off', () => {
    expect(DEFAULT_SETTINGS.aiProvider).toBe('none');
    expect(DEFAULT_SETTINGS.portalOnly).toBe(false);
  });
});

describe('bookmarks and history', () => {
  it('only stores web URLs', () => {
    const b = new BookmarkStore(dir());
    expect(() => b.add('javascript:alert(1)', 'x', '')).toThrow();
    expect(() => b.add('file:///C:/x', 'x', '')).toThrow();
    expect(b.add('https://a.example/', '', '').title).toBe('https://a.example/');
  });
  it('drops tampered entries when loading', () => {
    const d = dir();
    writeFileSync(
      join(d, 'bookmarks.json'),
      JSON.stringify([
        { id: 'a', url: 'javascript:alert(1)', title: 'x' },
        { id: 'b', url: 'https://ok.example/', title: 'ok' },
      ]),
    );
    expect(new BookmarkStore(d).list().map((x) => x.id)).toEqual(['b']);
  });
  it('records, searches, and clears history', () => {
    const d = dir();
    const h = new HistoryStore(d);
    h.record('https://a.example/', 'Alpha');
    h.record('https://b.example/', 'Beta');
    h.record('data:text/html,x', 'nope');
    expect(h.list('', 10)).toHaveLength(2);
    expect(h.list('alp', 10)).toHaveLength(1);
    h.clear();
    expect(h.list('', 10)).toHaveLength(0);
    expect(JSON.parse(readFileSync(join(d, 'history.json'), 'utf8'))).toEqual([]);
  });
});
