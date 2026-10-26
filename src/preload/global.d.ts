import type { LapsusApi } from './api';

declare global {
  interface Window {
    lapsus: LapsusApi;
  }
}
