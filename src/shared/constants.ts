export const APP_NAME = 'Lapsus';
export const PUBLISHER = 'KRLBRYN';
export const DEFAULT_PORTAL_URL = 'https://discipulusv2.amasystem.net/';
export const REPO_URL = 'https://github.com/kerlbryandomingo-wq/lapsus';

/** Origin of the trusted application UI when packaged. */
export const APP_SCHEME = 'app';
export const APP_HOST = 'lapsus';
export const APP_ORIGIN = `${APP_SCHEME}://${APP_HOST}`;

/** Registrable domains treated as part of the student portal. */
export const PORTAL_DOMAINS = ['amasystem.net', 'amaes.edu.ph'] as const;

/** Session partition for remote web content. Kept separate from the app UI session. */
export const WEB_PARTITION = 'persist:lapsus-web';

export const MAX_POPUP_WINDOWS = 5;
export const MAX_HISTORY_ENTRIES = 5000;
export const MAX_AI_INPUT_CHARS = 6000;
