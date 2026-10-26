import { join } from 'node:path';
import { JsonFile } from './json-file';

interface UsageData {
  month: string;
  requests: number;
}

export function currentMonth(now = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/**
 * Local count of AI requests per calendar month. This is a safeguard Lapsus enforces on its
 * own requests; it is not the provider's billing limit, which must be set in the provider console.
 */
export class UsageStore {
  private file: JsonFile<UsageData>;
  private data: UsageData;

  constructor(dir: string) {
    this.file = new JsonFile(join(dir, 'ai-usage.json'), 0);
    this.data = this.file.read(
      () => ({ month: currentMonth(), requests: 0 }),
      (raw) => {
        const r = raw as Partial<UsageData>;
        return {
          month: typeof r?.month === 'string' ? r.month : currentMonth(),
          requests:
            Number.isInteger(r?.requests) && (r.requests as number) >= 0
              ? (r.requests as number)
              : 0,
        };
      },
    );
  }

  snapshot(): UsageData {
    const month = currentMonth();
    if (this.data.month !== month) this.data = { month, requests: 0 };
    return { ...this.data };
  }

  canSpend(limit: number): boolean {
    return this.snapshot().requests < limit;
  }

  record(): void {
    const snap = this.snapshot();
    this.data = { month: snap.month, requests: snap.requests + 1 };
    this.file.write(this.data);
    this.file.flush();
  }
}
