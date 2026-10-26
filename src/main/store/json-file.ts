import { mkdirSync, readFileSync, renameSync, writeFileSync, rmSync } from 'node:fs';
import { dirname } from 'node:path';

/**
 * Small JSON persistence helper. Writes go to a temp file first and are renamed into place,
 * so a crash mid-write never leaves a half-written file. Corrupt files fall back to defaults.
 */
export class JsonFile<T> {
  private timer: NodeJS.Timeout | null = null;
  private pending: T | null = null;

  constructor(
    private readonly path: string,
    private readonly delayMs = 400,
  ) {}

  read(fallback: () => T, coerce: (raw: unknown) => T = (raw) => raw as T): T {
    try {
      return coerce(JSON.parse(readFileSync(this.path, 'utf8')));
    } catch {
      return fallback();
    }
  }

  /** Schedules a debounced write. */
  write(value: T): void {
    this.pending = value;
    if (this.timer) return;
    this.timer = setTimeout(() => this.flush(), this.delayMs);
  }

  flush(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    if (this.pending === null) return;
    const value = this.pending;
    this.pending = null;
    mkdirSync(dirname(this.path), { recursive: true });
    const tmp = `${this.path}.tmp`;
    writeFileSync(tmp, JSON.stringify(value), { encoding: 'utf8', mode: 0o600 });
    renameSync(tmp, this.path);
  }

  delete(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.pending = null;
    rmSync(this.path, { force: true });
  }
}
