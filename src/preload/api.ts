import type { IpcArg, IpcChannel, IpcResponses } from '@shared/ipc';
import type { MainEvent } from '@shared/types';

type ArgList<C extends IpcChannel> = IpcArg<C> extends undefined ? [] : [arg: IpcArg<C>];

export interface LapsusApi {
  invoke<C extends IpcChannel>(channel: C, ...args: ArgList<C>): Promise<IpcResponses[C]>;
  onEvent(listener: (event: MainEvent) => void): () => void;
}
