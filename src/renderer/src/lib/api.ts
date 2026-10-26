import type { LapsusApi } from '../../../preload/api';

/** The bridge exposed by the preload script. It is the UI's only way to reach the main process. */
export const api: LapsusApi = window.lapsus;
