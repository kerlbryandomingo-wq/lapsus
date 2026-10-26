import { app, dialog, session, type BaseWindow, type Session } from 'electron';
import { WEB_PARTITION } from '@shared/constants';
import type { CertificateInfo } from '@shared/types';
import {
  checkPermission,
  decidePermission,
  originOf,
  permissionLabel,
} from '../security/permission-policy';

/**
 * Configures the session used for remote web content (the portal and anything else browsed).
 * It is separate from the session that serves the app UI.
 */
export class WebSession {
  readonly session: Session;
  private readonly grants = new Set<string>();
  private readonly certificates = new Map<string, CertificateInfo>();

  constructor(private readonly getWindow: () => BaseWindow | null) {
    this.session = session.fromPartition(WEB_PARTITION);
    this.configure();
  }

  private configure(): void {
    const ses = this.session;

    // Present as the Chromium version Lapsus actually ships, without the Electron and app tokens
    // that some sites reject. Nothing else about the browser is disguised.
    ses.setUserAgent(sanitizeUserAgent(ses.getUserAgent()));

    ses.setPermissionRequestHandler((wc, permission, callback, details) => {
      const requestingUrl = details.requestingUrl || wc?.getURL() || '';
      const decision = decidePermission(permission, requestingUrl);
      if (decision !== 'prompt') return callback(decision === 'allow');
      const key = `${originOf(requestingUrl)}|${permission}`;
      if (this.grants.has(key)) return callback(true);
      const win = this.getWindow();
      if (!win) return callback(false);
      const mediaTypes =
        'mediaTypes' in details && Array.isArray(details.mediaTypes) ? details.mediaTypes : [];
      void dialog
        .showMessageBox(win, {
          type: 'question',
          title: 'Permission request',
          message: `${originOf(requestingUrl)} wants to ${permissionLabel(permission, mediaTypes)}.`,
          detail: 'Allow only if you trust this site. The permission lasts until you close Lapsus.',
          buttons: ['Block', 'Allow'],
          defaultId: 0,
          cancelId: 0,
          noLink: true,
        })
        .then(({ response }) => {
          if (response === 1) this.grants.add(key);
          callback(response === 1);
        })
        .catch(() => callback(false));
    });

    ses.setPermissionCheckHandler((_wc, permission, requestingOrigin) =>
      checkPermission(permission, requestingOrigin, this.grants),
    );

    // No USB/HID/serial/Bluetooth device access for web content.
    ses.setDevicePermissionHandler(() => false);
    ses.on('select-hid-device', (event, _details, callback) => {
      event.preventDefault();
      callback();
    });
    ses.on('select-serial-port', (event, _ports, _wc, callback) => {
      event.preventDefault();
      callback('');
    });
    ses.setDisplayMediaRequestHandler((_request, callback) => callback({}));

    // Record certificate details for the security indicator, but always defer to
    // Chromium's own verification result (-3). Invalid certificates stay rejected.
    ses.setCertificateVerifyProc((request, callback) => {
      if (request.errorCode === 0) {
        this.certificates.set(request.hostname, {
          host: request.hostname,
          issuer: request.certificate.issuerName,
          subject: request.certificate.subjectName,
          validExpiry: request.certificate.validExpiry,
        });
      } else {
        this.certificates.delete(request.hostname);
      }
      callback(-3);
    });
  }

  certificateFor(hostname: string): CertificateInfo | null {
    return this.certificates.get(hostname) ?? null;
  }

  revokeGrants(): void {
    this.grants.clear();
  }

  async clear(opts: { cookies: boolean; cache: boolean }): Promise<void> {
    if (opts.cookies) {
      await this.session.clearStorageData();
      await this.session.clearAuthCache();
      this.grants.clear();
    }
    if (opts.cache) {
      await this.session.clearCache();
      await this.session.clearHostResolverCache();
    }
  }
}

export function sanitizeUserAgent(ua: string): string {
  return ua
    .replace(new RegExp(`\\s${app.getName()}/[^\\s]+`, 'i'), '')
    .replace(/\slapsus\/[^\s]+/i, '')
    .replace(/\sElectron\/[^\s]+/, '');
}
