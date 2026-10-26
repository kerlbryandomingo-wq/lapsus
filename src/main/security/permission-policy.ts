import { isLoopbackHost } from '@shared/url';

export type PermissionDecision = 'allow' | 'deny' | 'prompt';

/** Permissions granted without asking for secure origins: harmless or needed for normal use. */
const AUTO_ALLOW = new Set(['clipboard-sanitized-write', 'fullscreen', 'pointerLock']);

/** Permissions a site may request but only with the student's explicit approval. */
const ASK = new Set(['media', 'notifications', 'geolocation', 'clipboard-read']);

/**
 * Decides a web permission request. Everything else (USB, HID, serial, MIDI, idle detection,
 * window management, storage access, etc.) is denied by default.
 */
export function decidePermission(permission: string, requestingUrl: string): PermissionDecision {
  let url: URL;
  try {
    url = new URL(requestingUrl);
  } catch {
    return 'deny';
  }
  const secure = url.protocol === 'https:' || (url.protocol === 'http:' && isLoopbackHost(url.hostname));
  if (!secure) return 'deny';
  if (AUTO_ALLOW.has(permission)) return 'allow';
  if (ASK.has(permission)) return 'prompt';
  return 'deny';
}

/** Synchronous permission checks (no prompting possible). */
export function checkPermission(permission: string, requestingOrigin: string, granted: ReadonlySet<string>): boolean {
  const decision = decidePermission(permission, requestingOrigin);
  if (decision === 'allow') return true;
  if (decision === 'prompt') return granted.has(`${originOf(requestingOrigin)}|${permission}`);
  return false;
}

export function originOf(raw: string): string {
  try {
    return new URL(raw).origin;
  } catch {
    return 'null';
  }
}

export function permissionLabel(permission: string, mediaTypes: readonly string[] = []): string {
  switch (permission) {
    case 'media':
      if (mediaTypes.includes('video') && mediaTypes.includes('audio')) return 'use your camera and microphone';
      if (mediaTypes.includes('video')) return 'use your camera';
      return 'use your microphone';
    case 'notifications':
      return 'show notifications';
    case 'geolocation':
      return 'know your location';
    case 'clipboard-read':
      return 'read your clipboard';
    default:
      return permission;
  }
}
