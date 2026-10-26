import { z } from 'zod';

export const AI_SHORTCUTS = ['Ctrl+K', 'Ctrl+Shift+K', 'Ctrl+Alt+K'] as const;
export const AI_PROVIDERS = ['none', 'openai', 'anthropic', 'demo'] as const;
export type AIProviderId = (typeof AI_PROVIDERS)[number];

const hostname = z
  .string()
  .trim()
  .toLowerCase()
  .regex(/^(?:[a-z0-9-]+\.)+[a-z]{2,63}$/, 'Enter a host name such as example.edu');

export const SettingsSchema = z.object({
  theme: z.enum(['system', 'light', 'dark']).default('system'),
  displayName: z.string().trim().max(40).default(''),
  startup: z.enum(['portal', 'home', 'restore']).default('portal'),
  /** How the window opens: genuine OS fullscreen (left with F11) or a maximized window. */
  windowMode: z.enum(['fullscreen', 'maximized']).default('fullscreen'),
  /** Where links and scripts that ask for a new window open: a pop-up window, or the main window. */
  newWindows: z.enum(['popup', 'same-window']).default('popup'),
  searchEngine: z.enum(['google', 'bing', 'duckduckgo']).default('google'),
  historyEnabled: z.boolean().default(true),
  portalOnly: z.boolean().default(false),
  extraPortalHosts: z.array(hostname).max(20).default([]),
  sidebarCollapsed: z.boolean().default(false),
  focusMode: z.boolean().default(false),
  rememberFocusMode: z.boolean().default(true),
  aiShortcutEnabled: z.boolean().default(true),
  aiShortcut: z.enum(AI_SHORTCUTS).default('Ctrl+K'),
  aiProvider: z.enum(AI_PROVIDERS).default('none'),
  openaiModel: z
    .string()
    .trim()
    .regex(/^[\w.:-]{1,80}$/)
    .default('gpt-5-mini'),
  anthropicModel: z
    .string()
    .trim()
    .regex(/^[\w.:-]{1,80}$/)
    .default('claude-opus-5-5'),
  aiMonthlyRequestLimit: z.number().int().min(0).max(10000).default(100),
});

export type Settings = z.infer<typeof SettingsSchema>;

export const DEFAULT_SETTINGS: Settings = SettingsSchema.parse({});

/** Partial update coming from the UI. Unknown keys are rejected. */
export const SettingsPatchSchema = SettingsSchema.partial().strict();
export type SettingsPatch = z.infer<typeof SettingsPatchSchema>;

/** Reads stored settings, falling back to defaults key by key when a value is invalid. */
export function coerceSettings(raw: unknown): Settings {
  const result: Record<string, unknown> = { ...DEFAULT_SETTINGS };
  if (raw && typeof raw === 'object') {
    for (const [key, value] of Object.entries(raw)) {
      const field = SettingsSchema.shape[key as keyof Settings];
      if (!field) continue;
      const parsed = field.safeParse(value);
      if (parsed.success) result[key] = parsed.data;
    }
  }
  return SettingsSchema.parse(result);
}
