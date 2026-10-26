import { app, dialog, shell, type BaseWindow, type DownloadItem, type Session } from 'electron';
import { randomUUID } from 'node:crypto';
import { join } from 'node:path';
import type { DownloadInfo } from '@shared/types';
import { isDangerousDownload, sanitizeFilename } from './download-policy';

/**
 * Tracks downloads from the browsing session. Every download goes through the standard Windows
 * save dialog; executable file types get an extra confirmation first.
 */
export class DownloadManager {
  private items = new Map<string, { info: DownloadInfo; item: DownloadItem | null }>();

  constructor(
    private readonly getWindow: () => BaseWindow | null,
    private readonly emit: (downloads: DownloadInfo[]) => void,
  ) {}

  attach(ses: Session): void {
    ses.on('will-download', (_event, item) => this.onDownload(item));
  }

  private onDownload(item: DownloadItem): void {
    const filename = sanitizeFilename(item.getFilename());
    const dangerous = isDangerousDownload(filename);
    const win = this.getWindow();

    if (dangerous && win) {
      const choice = dialog.showMessageBoxSync(win, {
        type: 'warning',
        title: 'Download a program?',
        message: `"${filename}" is a file type that can run programs on your computer.`,
        detail: `Source: ${safeOrigin(item.getURL())}\nOnly keep it if you trust where it came from.`,
        buttons: ['Cancel download', 'Save anyway'],
        defaultId: 0,
        cancelId: 0,
        noLink: true,
      });
      if (choice !== 1) {
        item.cancel();
        return;
      }
    }

    item.setSaveDialogOptions({
      title: 'Save download',
      defaultPath: join(app.getPath('downloads'), filename),
    });

    const id = randomUUID().replace(/-/g, '').slice(0, 24);
    const info: DownloadInfo = {
      id,
      filename,
      url: item.getURL(),
      state: 'progressing',
      receivedBytes: 0,
      totalBytes: item.getTotalBytes(),
      savePath: null,
      startedAt: Date.now(),
      dangerous,
    };
    this.items.set(id, { info, item });
    this.publish();

    item.on('updated', (_e, state) => {
      info.state = state === 'interrupted' ? 'interrupted' : 'progressing';
      info.receivedBytes = item.getReceivedBytes();
      info.totalBytes = item.getTotalBytes();
      const savePath = item.getSavePath();
      if (savePath) {
        info.savePath = savePath;
        info.filename = savePath.split(/[\\/]/).pop() ?? info.filename;
      }
      this.publish();
    });
    item.once('done', (_e, state) => {
      info.state = state;
      info.receivedBytes = item.getReceivedBytes();
      info.savePath = item.getSavePath() || null;
      const entry = this.items.get(id);
      if (entry) entry.item = null;
      this.publish();
    });
  }

  list(): DownloadInfo[] {
    return [...this.items.values()]
      .map((e) => ({ ...e.info }))
      .sort((a, b) => b.startedAt - a.startedAt);
  }

  cancel(id: string): void {
    this.items.get(id)?.item?.cancel();
  }

  show(id: string): void {
    const path = this.items.get(id)?.info.savePath;
    if (path) shell.showItemInFolder(path);
  }

  async open(id: string): Promise<{ ok: boolean; message?: string }> {
    const info = this.items.get(id)?.info;
    if (!info || info.state !== 'completed' || !info.savePath)
      return { ok: false, message: 'File is not available.' };
    if (info.dangerous) {
      // Programs are never launched from inside Lapsus; show them in Explorer instead.
      shell.showItemInFolder(info.savePath);
      return { ok: false, message: 'Programs are shown in File Explorer instead of being opened.' };
    }
    const error = await shell.openPath(info.savePath);
    return error ? { ok: false, message: error } : { ok: true };
  }

  clear(): void {
    for (const [id, entry] of this.items) {
      if (entry.info.state !== 'progressing') this.items.delete(id);
    }
    this.publish();
  }

  private publish(): void {
    this.emit(this.list());
  }
}

function safeOrigin(raw: string): string {
  try {
    return new URL(raw).origin;
  } catch {
    return 'unknown';
  }
}
