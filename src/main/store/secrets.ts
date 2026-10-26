import { safeStorage } from 'electron';
import { join } from 'node:path';
import { JsonFile } from './json-file';

type SecretName = 'openai' | 'anthropic';
type SecretFile = Partial<Record<SecretName, string>>;

/**
 * API keys encrypted with the operating system's credential protection
 * (DPAPI on Windows, via Electron safeStorage). Keys are never written in plain text:
 * if encryption is unavailable, storing is refused. Decrypted keys stay in the main process.
 */
export class SecretStore {
  private file: JsonFile<SecretFile>;
  private data: SecretFile;

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

  get encryptionAvailable(): boolean {
    return safeStorage.isEncryptionAvailable();
  }

  has(name: SecretName): boolean {
    return typeof this.data[name] === 'string';
  }

  set(name: SecretName, value: string): void {
    if (!this.encryptionAvailable) {
      throw new Error('Secure storage is not available on this system, so the key was not saved.');
    }
    this.data[name] = safeStorage.encryptString(value).toString('base64');
    this.file.write(this.data);
    this.file.flush();
  }

  get(name: SecretName): string | null {
    const enc = this.data[name];
    if (!enc || !this.encryptionAvailable) return null;
    try {
      return safeStorage.decryptString(Buffer.from(enc, 'base64'));
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
