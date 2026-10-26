import { z } from 'zod';
import { SettingsPatchSchema } from './settings';
import type { Settings } from './settings';
import type {
  AIAskResult,
  AIStatus,
  AppInfo,
  Bookmark,
  DownloadInfo,
  HistoryEntry,
  Snapshot,
} from './types';

/**
 * Every request the app UI may make to the main process, with the schema its argument must
 * satisfy. There is deliberately no generic channel: anything not listed here is rejected.
 */
const url = z.string().min(1).max(4096);
const id = z.string().regex(/^[A-Za-z0-9_-]{1,64}$/);
const none = z.undefined();

export const IpcRequests = {
  'state:get': none,
  'nav:go': z.object({ input: z.string().max(4096) }).strict(),
  'nav:portal': none,
  'nav:back': none,
  'nav:forward': none,
  'nav:reload': z.object({ ignoreCache: z.boolean() }).strict(),
  'nav:stop': none,
  'nav:allow-insecure': z.object({ url }).strict(),
  'popups:allow-site': z.object({ origin: url }).strict(),
  'view:set-bounds': z
    .object({
      x: z.number().finite().min(0).max(20000),
      y: z.number().finite().min(0).max(20000),
      width: z.number().finite().min(0).max(20000),
      height: z.number().finite().min(0).max(20000),
      visible: z.boolean(),
    })
    .strict(),
  'find:start': z
    .object({ text: z.string().min(1).max(500), forward: z.boolean(), findNext: z.boolean() })
    .strict(),
  'find:stop': none,
  'zoom:step': z.object({ step: z.union([z.literal(-1), z.literal(0), z.literal(1)]) }).strict(),
  'window:minimize': none,
  'window:toggle-maximize': none,
  'window:close': none,
  'window:set-focus-mode': z.object({ enabled: z.boolean() }).strict(),
  'window:toggle-fullscreen': none,
  'settings:update': SettingsPatchSchema,
  'bookmarks:list': none,
  'bookmarks:toggle-current': none,
  'bookmarks:add': z
    .object({ url, title: z.string().max(300), folder: z.string().max(60) })
    .strict(),
  'bookmarks:update': z
    .object({ id, title: z.string().max(300).optional(), folder: z.string().max(60).optional() })
    .strict(),
  'bookmarks:remove': z.object({ id }).strict(),
  'bookmarks:open': z.object({ id }).strict(),
  'history:list': z
    .object({ query: z.string().max(200), limit: z.number().int().min(1).max(500) })
    .strict(),
  'history:remove': z.object({ id }).strict(),
  'history:clear': none,
  'history:open': z.object({ id }).strict(),
  'downloads:list': none,
  'downloads:show': z.object({ id }).strict(),
  'downloads:open': z.object({ id }).strict(),
  'downloads:cancel': z.object({ id }).strict(),
  'downloads:clear': none,
  'data:clear': z
    .object({
      cookies: z.boolean(),
      cache: z.boolean(),
      history: z.boolean(),
      downloads: z.boolean(),
      aiKeys: z.boolean(),
    })
    .strict(),
  'ai:status': none,
  'ai:set-key': z
    .object({ provider: z.enum(['openai', 'anthropic']), key: z.string().min(10).max(400) })
    .strict(),
  'ai:clear-key': z.object({ provider: z.enum(['openai', 'anthropic']) }).strict(),
  'ai:test': none,
  'ai:prepare': z.object({ material: z.string().max(50000) }).strict(),
  'ai:ask': z
    .object({ material: z.string().min(1).max(50000), note: z.string().max(500) })
    .strict(),
  'ai:cancel': none,
  'ai:get-selection': none,
  'external:open': z.object({ url }).strict(),
  'app:info': none,
} as const;

export type IpcChannel = keyof typeof IpcRequests;
export type IpcArg<C extends IpcChannel> = z.infer<(typeof IpcRequests)[C]>;

export interface IpcResponses {
  'state:get': Snapshot;
  'nav:go': void;
  'nav:portal': void;
  'nav:back': void;
  'nav:forward': void;
  'nav:reload': void;
  'nav:stop': void;
  'nav:allow-insecure': void;
  'popups:allow-site': void;
  'view:set-bounds': void;
  'find:start': void;
  'find:stop': void;
  'zoom:step': void;
  'window:minimize': void;
  'window:toggle-maximize': void;
  'window:close': void;
  'window:set-focus-mode': void;
  'window:toggle-fullscreen': void;
  'settings:update': Settings;
  'bookmarks:list': Bookmark[];
  'bookmarks:toggle-current': boolean;
  'bookmarks:add': Bookmark;
  'bookmarks:update': Bookmark | null;
  'bookmarks:remove': void;
  'bookmarks:open': void;
  'history:list': HistoryEntry[];
  'history:remove': void;
  'history:clear': void;
  'history:open': void;
  'downloads:list': DownloadInfo[];
  'downloads:show': void;
  'downloads:open': { ok: boolean; message?: string };
  'downloads:cancel': void;
  'downloads:clear': void;
  'data:clear': void;
  'ai:status': AIStatus;
  'ai:set-key': AIStatus;
  'ai:clear-key': AIStatus;
  'ai:test': { ok: boolean; message: string };
  'ai:prepare': { text: string; truncated: boolean; redactions: number };
  'ai:ask': AIAskResult;
  'ai:cancel': void;
  'ai:get-selection': { text: string; reason?: string };
  'external:open': boolean;
  'app:info': AppInfo;
}

export const IPC_INVOKE = 'lapsus:invoke';
export const IPC_EVENT = 'lapsus:event';

export type ValidationResult<C extends IpcChannel> =
  { ok: true; channel: C; arg: IpcArg<C> } | { ok: false; error: string };

/** Validates an incoming request. Used by the main process before any handler runs. */
export function validateRequest(channel: unknown, arg: unknown): ValidationResult<IpcChannel> {
  if (typeof channel !== 'string' || !Object.hasOwn(IpcRequests, channel)) {
    return { ok: false, error: 'Unknown request' };
  }
  const schema = IpcRequests[channel as IpcChannel];
  const parsed = schema.safeParse(arg);
  if (!parsed.success) return { ok: false, error: `Invalid arguments for ${channel}` };
  return { ok: true, channel: channel as IpcChannel, arg: parsed.data as IpcArg<IpcChannel> };
}
