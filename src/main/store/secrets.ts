import { safeStorage } from 'electron';
import { join } from 'node:path';
import { JsonFile } from './json-file';

type SecretName = 'openai' | 'anthropic';
type SecretFile = Partial<Record<SecretName, string>>;

/**
 * API keys encrypted with the operating system's credential protection
 * (DPAPI on Windows, via Electron safeStorage). Keys are never written in plain text:
 * if real encryption is unavailable, storing is refused. Decrypted keys stay in the main process.
 */
export class SecretStore {
  private file: JsonFile<SecretFile>;
  private data: SecretFile;
  private available = false;

  constructor(dir: string) {
    this.file = new JsonFile(join(dir, 'secrets.json'), 0);
    this.data = this.file.read(
      () => ({}),
      (raw) => {
        const out: SecretFile = {};
        if (raw && typeof raw === 'object') {
          for (const name of ['openai', 'anthropic'] as const) {
            const v = (raw as Record<string, unknown>)[name];
            if (typeof v === 'string' && /^[A-Za-z0-9+/=]+$/.test(v)) out[name] = v;
          }
        }
        return out;
      },
    );
  }

  /** Must be awaited once after app ready. */
  async init(): Promise<void> {
    try {
      const ok = await safeStorage.isAsyncEncryptionAvailable();
      // On Linux without a keyring, Chromium falls back to a fixed key, which is not real protection.
      const weak = process.platform === 'linux' && safeStorage.getSelectedStorageBackend() === 'basic_text';
      this.available = ok && !weak;
    } catch {
      this.available = false;
    }
  }

  get encryptionAvailable(): boolean {
    return this.available;
  }

  has(name: SecretName): boolean {
    return typeof this.data[name] === 'string';
  }

  async set(name: SecretName, value: string): Promise<void> {
    if (!this.available) {
      throw new Error('Secure storage is not available on this system, so the key was not saved.');
    }
    const encrypted = await safeStorage.encryptStringAsync(value);
    this.data[name] = encrypted.toString('base64');
    this.file.write(this.data);
    this.file.flush();
  }

  async get(name: SecretName): Promise<string | null> {
    const enc = this.data[name];
    if (!enc || !this.available) return null;
    try {
      const { result, shouldReEncrypt } = await safeStorage.decryptStringAsync(Buffer.from(enc, 'base64'));
      if (shouldReEncrypt) await this.set(name, result);
      return result;
    } catch {
      return null;
    }
  }

  clear(name?: SecretName): void {
    if (name) delete this.data[name];
    else this.data = {};
    this.file.write(this.data);
    this.file.flush();
  }
}
