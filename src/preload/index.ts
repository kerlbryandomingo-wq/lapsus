import { contextBridge, ipcRenderer, type IpcRendererEvent } from 'electron';
import { IpcRequests, IPC_EVENT, IPC_INVOKE } from '@shared/ipc';
import type { LapsusApi } from './api';

const allowed = new Set(Object.keys(IpcRequests));

/**
 * The only bridge between the app UI and the main process. Loaded into the app UI view only;
 * remote web content never gets a preload. The main process re-validates every call.
 */
const api: LapsusApi = {
  invoke: (channel, ...args) => {
    if (!allowed.has(channel)) return Promise.reject(new Error('Unknown request'));
    return ipcRenderer.invoke(IPC_INVOKE, channel, args[0]);
  },
  onEvent: (listener) => {
    const handler = (_event: IpcRendererEvent, payload: unknown) => listener(payload as never);
    ipcRenderer.on(IPC_EVENT, handler);
    return () => ipcRenderer.removeListener(IPC_EVENT, handler);
  },
};

contextBridge.exposeInMainWorld('lapsus', Object.freeze(api));
