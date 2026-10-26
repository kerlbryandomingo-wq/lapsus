import { randomUUID } from 'node:crypto';
import { join } from 'node:path';
import { JsonFile } from './json-file';
import { coerceSettings, DEFAULT_SETTINGS, SettingsPatchSchema } from '@shared/settings';
import type { Settings, SettingsPatch } from '@shared/settings';
import type { Bookmark, HistoryEntry } from '@shared/types';
import { MAX_HISTORY_ENTRIES } from '@shared/constants';

const newId = () => randomUUID().replace(/-/g, '').slice(0, 24);

function isRecordArray(raw: unknown): raw is Record<string, unknown>[] {
  return Array.isArray(raw) && raw.every((r) => r && typeof r === 'object');
}

function safeHttpUrl(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  try {
    const u = new URL(value);
    return u.protocol === 'https:' || u.protocol === 'http:' ? u.toString() : null;
  } catch {
    return null;
  }
}

export class SettingsStore {
  private file: JsonFile<Settings>;
  private value: Settings;

  constructor(dir: string) {
    this.file = new JsonFile(join(dir, 'settings.json'));
    this.value = this.file.read(() => ({ ...DEFAULT_SETTINGS }), coerceSettings);
  }

  get(): Settings {
    return this.value;
  }

  update(patch: SettingsPatch): Settings {
    const parsed = SettingsPatchSchema.parse(patch);
    this.value = coerceSettings({ ...this.value, ...parsed });
    this.file.write(this.value);
    return this.value;
  }

  flush(): void {
    this.file.flush();
  }
}

export class BookmarkStore {
  private file: JsonFile<Bookmark[]>;
  private items: Bookmark[];

  constructor(dir: string) {
    this.file = new JsonFile(join(dir, 'bookmarks.json'));
    this.items = this.file.read(
      () => [],
      (raw) =>
        isRecordArray(raw)
          ? raw.flatMap((r) => {
              const url = safeHttpUrl(r.url);
              if (!url || typeof r.id !== 'string') return [];
              return [
                {
                  id: r.id,
                  url,
                  title: String(r.title ?? url).slice(0, 300),
                  folder: String(r.folder ?? '').slice(0, 60),
                  createdAt: Number(r.createdAt) || Date.now(),
                },
              ];
            })
          : [],
    );
  }

  list(): Bookmark[] {
    return [...this.items];
  }

  findByUrl(url: string): Bookmark | undefined {
    return this.items.find((b) => b.url === url);
  }

  get(id: string): Bookmark | undefined {
    return this.items.find((b) => b.id === id);
  }

  add(url: string, title: string, folder: string): Bookmark {
    const safe = safeHttpUrl(url);
    if (!safe) throw new Error('Only web pages can be bookmarked');
    const existing = this.findByUrl(safe);
    if (existing) return existing;
    const bookmark = {
      id: newId(),
      url: safe,
      title: title.trim() || safe,
      folder: folder.trim(),
      createdAt: Date.now(),
    };
    this.items = [bookmark, ...this.items].slice(0, 2000);
    this.file.write(this.items);
    return bookmark;
  }

  update(id: string, patch: { title?: string; folder?: string }): Bookmark | null {
    const bookmark = this.get(id);
    if (!bookmark) return null;
    if (patch.title !== undefined) bookmark.title = patch.title.trim() || bookmark.url;
    if (patch.folder !== undefined) bookmark.folder = patch.folder.trim();
    this.file.write(this.items);
    return bookmark;
  }

  remove(id: string): void {
    this.items = this.items.filter((b) => b.id !== id);
    this.file.write(this.items);
  }

  flush(): void {
    this.file.flush();
  }
}

export class HistoryStore {
  private file: JsonFile<HistoryEntry[]>;
  private items: HistoryEntry[];

  constructor(dir: string) {
    this.file = new JsonFile(join(dir, 'history.json'), 1500);
    this.items = this.file.read(
      () => [],
      (raw) =>
        isRecordArray(raw)
          ? raw.flatMap((r) => {
              const url = safeHttpUrl(r.url);
              if (!url || typeof r.id !== 'string') return [];
              return [
                {
                  id: r.id,
                  url,
                  title: String(r.title ?? '').slice(0, 300),
                  visitedAt: Number(r.visitedAt) || 0,
                },
              ];
            })
          : [],
    );
  }

  record(url: string, title: string): void {
    const safe = safeHttpUrl(url);
    if (!safe) return;
    const now = Date.now();
    const last = this.items[0];
    // Collapse reloads and quick title updates of the same page.
    if (last && last.url === safe && now - last.visitedAt < 30 * 60 * 1000) {
      last.title = title || last.title;
      last.visitedAt = now;
    } else {
      this.items.unshift({ id: newId(), url: safe, title: title.slice(0, 300), visitedAt: now });
      if (this.items.length > MAX_HISTORY_ENTRIES) this.items.length = MAX_HISTORY_ENTRIES;
    }
    this.file.write(this.items);
  }

  updateTitle(url: string, title: string): void {
    const last = this.items[0];
    if (last && last.url === url && title) {
      last.title = title.slice(0, 300);
      this.file.write(this.items);
    }
  }

  list(query: string, limit: number): HistoryEntry[] {
    const q = query.trim().toLowerCase();
    const filtered = q
      ? this.items.filter(
          (e) => e.url.toLowerCase().includes(q) || e.title.toLowerCase().includes(q),
        )
      : this.items;
    return filtered.slice(0, limit);
  }

  get(id: string): HistoryEntry | undefined {
    return this.items.find((e) => e.id === id);
  }

  remove(id: string): void {
    this.items = this.items.filter((e) => e.id !== id);
    this.file.write(this.items);
  }

  clear(): void {
    this.items = [];
    this.file.write(this.items);
    this.file.flush();
  }

  flush(): void {
    this.file.flush();
  }
}

/** Remembers the last page for "restore session" startup. */
export class SessionStore {
  private file: JsonFile<{ lastUrl: string | null }>;
  private value: { lastUrl: string | null };

  constructor(dir: string) {
    this.file = new JsonFile(join(dir, 'session.json'));
    this.value = this.file.read(
      () => ({ lastUrl: null }),
      (raw) => ({ lastUrl: safeHttpUrl((raw as { lastUrl?: unknown })?.lastUrl) }),
    );
  }

  get lastUrl(): string | null {
    return this.value.lastUrl;
  }

  setLastUrl(url: string): void {
    const safe = safeHttpUrl(url);
    if (!safe || safe === this.value.lastUrl) return;
    this.value = { lastUrl: safe };
    this.file.write(this.value);
  }

  clear(): void {
    this.value = { lastUrl: null };
    this.file.delete();
  }

  flush(): void {
    this.file.flush();
  }
}
