import { ipcMain, type IpcMainInvokeEvent, type WebContents } from 'electron';
import {
  IPC_INVOKE,
  validateRequest,
  type IpcArg,
  type IpcChannel,
  type IpcResponses,
} from '@shared/ipc';

type Handler<C extends IpcChannel> = (arg: IpcArg<C>) => IpcResponses[C] | Promise<IpcResponses[C]>;
export type Handlers = { [C in IpcChannel]: Handler<C> };

/**
 * Single entry point for requests from the app UI. A request is processed only when it comes
 * from the trusted UI view's top frame at the expected origin and its arguments pass validation.
 */
export function registerIpc(
  handlers: Handlers,
  isTrustedSender: (sender: WebContents, frameUrl: string | undefined) => boolean,
): void {
  ipcMain.handle(IPC_INVOKE, async (event: IpcMainInvokeEvent, channel: unknown, arg: unknown) => {
    const frame = event.senderFrame;
    if (!frame || frame.parent !== null || !isTrustedSender(event.sender, frame.url)) {
      throw new Error('Request refused');
    }
    const result = validateRequest(channel, arg);
    if (!result.ok) throw new Error(result.error);
    const handler = handlers[result.channel] as Handler<IpcChannel>;
    return handler(result.arg);
  });
}

/** Origin check for the UI frame: app://lapsus in production, the dev server in development. */
export function isTrustedUiUrl(frameUrl: string | undefined, uiUrl: string): boolean {
  if (!frameUrl) return false;
  try {
    return new URL(frameUrl).origin === new URL(uiUrl).origin;
  } catch {
    return false;
  }
}
