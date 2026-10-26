import { buildUserMessage, parseAnswer, prepareStudyInput } from '@shared/ai';
import type { Settings } from '@shared/settings';
import type { AIAskResult, AIStatus } from '@shared/types';
import type { SecretStore } from '../store/secrets';
import type { UsageStore } from '../store/usage';
import { AnthropicProvider, DemoProvider, OpenAIProvider, ProviderError, type AIProvider } from './providers';

/**
 * Owns every AI request. Runs in the main process only: API keys never reach a renderer,
 * and nothing is sent unless the student pressed Send on text they reviewed.
 */
export class AIService {
  private controller: AbortController | null = null;
  private readonly providers: Record<'openai' | 'anthropic' | 'demo', AIProvider>;

  constructor(
    private readonly secrets: SecretStore,
    private readonly usage: UsageStore,
    private readonly getSettings: () => Settings,
    providers?: Partial<Record<'openai' | 'anthropic' | 'demo', AIProvider>>,
  ) {
    this.providers = {
      openai: providers?.openai ?? new OpenAIProvider(),
      anthropic: providers?.anthropic ?? new AnthropicProvider(),
      demo: providers?.demo ?? new DemoProvider(),
    };
  }

  private modelFor(settings: Settings): string | null {
    if (settings.aiProvider === 'openai') return settings.openaiModel;
    if (settings.aiProvider === 'anthropic') return settings.anthropicModel;
    if (settings.aiProvider === 'demo') return 'demo';
    return null;
  }

  status(): AIStatus {
    const settings = this.getSettings();
    const provider = settings.aiProvider;
    const usage = { ...this.usage.snapshot(), limit: settings.aiMonthlyRequestLimit };
    const encryptionAvailable = this.secrets.encryptionAvailable;
    const keyStored = provider === 'openai' || provider === 'anthropic' ? this.secrets.has(provider) : false;
    let ready = false;
    let message: string;
    if (provider === 'none') {
      message = 'No AI provider is configured. Choose one in Settings.';
    } else if (provider === 'demo') {
      ready = true;
      message = 'Demo mode: answers are placeholders and nothing is sent anywhere.';
    } else if (!keyStored) {
      message = `Add your ${provider === 'openai' ? 'OpenAI' : 'Anthropic'} API key in Settings.`;
    } else {
      ready = true;
      message = `Ready. Requests go to ${provider === 'openai' ? 'OpenAI' : 'Anthropic'} only when you press Send.`;
    }
    if (ready && usage.requests >= usage.limit) {
      ready = false;
      message = 'Monthly request limit reached. Raise it in Settings to continue.';
    }
    return { provider, model: this.modelFor(settings), keyStored, encryptionAvailable, ready, usage, message };
  }

  prepare(material: string) {
    return prepareStudyInput(material);
  }

  async ask(material: string, note: string): Promise<AIAskResult> {
    const settings = this.getSettings();
    const provider = settings.aiProvider;
    if (provider === 'none') return { ok: false, code: 'not-configured', error: this.status().message };

    const prepared = prepareStudyInput(material);
    if (!prepared.text) return { ok: false, code: 'input', error: 'There is no question text to send.' };

    let apiKey = '';
    if (provider !== 'demo') {
      apiKey = (await this.secrets.get(provider)) ?? '';
      if (!apiKey) return { ok: false, code: 'not-configured', error: this.status().message };
      if (!this.usage.canSpend(settings.aiMonthlyRequestLimit)) {
        return { ok: false, code: 'limit', error: 'Monthly request limit reached. Raise it in Settings to continue.' };
      }
    }

    this.controller?.abort();
    const controller = new AbortController();
    this.controller = controller;
    const model = this.modelFor(settings) ?? '';
    try {
      if (provider !== 'demo') this.usage.record();
      const raw = await this.providers[provider].ask({
        apiKey,
        model,
        userMessage: buildUserMessage(prepared.text, note),
        signal: controller.signal,
      });
      const parsed = parseAnswer(raw);
      if (!parsed.ok) return { ok: false, code: 'invalid-response', error: parsed.error };
      return { ok: true, answer: parsed.answer, provider, model, sentText: prepared.text };
    } catch (err) {
      if (err instanceof ProviderError) return { ok: false, code: err.code, error: err.message };
      return { ok: false, code: 'unknown', error: 'The AI request failed.' };
    } finally {
      if (this.controller === controller) this.controller = null;
    }
  }

  cancel(): void {
    this.controller?.abort();
    this.controller = null;
  }

  async test(): Promise<{ ok: boolean; message: string }> {
    const settings = this.getSettings();
    const provider = settings.aiProvider;
    if (provider === 'none') return { ok: false, message: 'Choose a provider first.' };
    if (provider === 'demo') return { ok: true, message: 'Demo mode works offline.' };
    const key = await this.secrets.get(provider);
    if (!key) return { ok: false, message: 'No API key saved for this provider.' };
    try {
      await this.providers[provider].test(key, this.modelFor(settings) ?? '', new AbortController().signal);
      return { ok: true, message: 'Connected. The key and model are valid. No request was billed.' };
    } catch (err) {
      return { ok: false, message: err instanceof ProviderError ? err.message : 'Connection test failed.' };
    }
  }
}
