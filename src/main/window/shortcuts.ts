import type { Settings } from '@shared/settings';

export type ShortcutAction =
  | 'toggle-ai'
  | 'focus-address'
  | 'find'
  | 'reload'
  | 'hard-reload'
  | 'back'
  | 'forward'
  | 'bookmark'
  | 'history'
  | 'downloads'
  | 'home'
  | 'toggle-focus-mode'
  | 'toggle-fullscreen'
  | 'escape'
  | 'zoom-in'
  | 'zoom-out'
  | 'zoom-reset'
  | 'reveal-chrome';

export interface KeyInput {
  type: string;
  key: string;
  code?: string;
  control: boolean;
  shift: boolean;
  alt: boolean;
  meta: boolean;
}

function aiShortcutMatches(input: KeyInput, setting: Settings['aiShortcut']): boolean {
  if (input.key.toLowerCase() !== 'k' || !input.control || input.meta) return false;
  switch (setting) {
    case 'Ctrl+K':
      return !input.shift && !input.alt;
    case 'Ctrl+Shift+K':
      return input.shift && !input.alt;
    case 'Ctrl+Alt+K':
      return input.alt && !input.shift;
    default:
      return false;
  }
}

/**
 * Maps a keydown to a Lapsus action. Runs before the page sees the key, so only keys that
 * belong to the browser are claimed; everything else passes through to the page untouched.
 */
export function matchShortcut(input: KeyInput, settings: Pick<Settings, 'aiShortcut' | 'aiShortcutEnabled'>): ShortcutAction | null {
  if (input.type !== 'keyDown') return null;
  const key = input.key;
  const lower = key.toLowerCase();
  const ctrl = input.control && !input.meta;
  const plain = !input.control && !input.alt && !input.shift && !input.meta;

  if (settings.aiShortcutEnabled && aiShortcutMatches(input, settings.aiShortcut)) return 'toggle-ai';

  if (ctrl && !input.alt) {
    if (!input.shift) {
      switch (lower) {
        case 'l':
          return 'focus-address';
        case 'f':
          return 'find';
        case 'r':
          return 'reload';
        case 'd':
          return 'bookmark';
        case 'h':
          return 'history';
        case 'j':
          return 'downloads';
        case '=':
        case '+':
          return 'zoom-in';
        case '-':
          return 'zoom-out';
        case '0':
          return 'zoom-reset';
      }
    } else {
      if (lower === 'r') return 'hard-reload';
      if (lower === 'f') return 'toggle-focus-mode';
      if (key === '+' || key === '=') return 'zoom-in';
    }
  }

  if (input.alt && !input.control && !input.shift && !input.meta) {
    if (key === 'ArrowLeft') return 'back';
    if (key === 'ArrowRight') return 'forward';
    if (key === 'Home') return 'home';
  }

  if (plain) {
    if (key === 'F5') return 'reload';
    if (key === 'F11') return 'toggle-fullscreen';
    if (key === 'Escape') return 'escape';
  }
  if (ctrl && !input.shift && !input.alt && key === 'F5') return 'hard-reload';

  return null;
}
