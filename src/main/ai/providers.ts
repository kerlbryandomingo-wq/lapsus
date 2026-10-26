import Anthropic from '@anthropic-ai/sdk';
import { zodOutputFormat } from '@anthropic-ai/sdk/helpers/zod';
import { AI_ANSWER_JSON_SCHEMA, AI_SYSTEM_PROMPT, AIAnswerSchema } from '@shared/ai';

export class ProviderError extends Error {
  constructor(
    message: string,
    readonly code: 'auth' | 'network' | 'refused' | 'invalid-response' | 'cancelled' | 'unknown',
  ) {
    super(message);
  }
}

export interface ProviderRequest {
  apiKey: string;
  model: string;
  userMessage: string;
  signal: AbortSignal;
}

/** Every provider returns the raw text of a JSON answer; parsing and validation happen once, in the service. */
export interface AIProvider {
  readonly id: 'openai' | 'anthropic' | 'demo';
  readonly label: string;
  ask(req: ProviderRequest): Promise<string>;
  test(apiKey: string, model: string, signal: AbortSignal): Promise<void>;
}

const REQUEST_TIMEOUT_MS = 60_000;

function withTimeout(signal: AbortSignal): AbortSignal {
  return AbortSignal.any([signal, AbortSignal.timeout(REQUEST_TIMEOUT_MS)]);
}

// Server-side refusal fallback is only accepted on these model families.
const FALLBACK_MODELS = /^claude-(?:opus-5|opus-5-5|sonnet-5-5|fable-5-1)$/;

export class AnthropicProvider implements AIProvider {
  readonly id = 'anthropic' as const;
  readonly label = 'Anthropic Claude';

  private client(apiKey: string): Anthropic {
    // Only the explicit key is used; no environment or profile credentials are picked up silently.
    return new Anthropic({ apiKey, authToken: null, maxRetries: 1, timeout: REQUEST_TIMEOUT_MS });
  }

  async ask({ apiKey, model, userMessage, signal }: ProviderRequest): Promise<string> {
    const client = this.client(apiKey);
    const useFallback = FALLBACK_MODELS.test(model);
    try {
      const response = await client.beta.messages.create(
        {
          model,
          max_tokens: 16000,
          system: AI_SYSTEM_PROMPT,
          messages: [{ role: 'user', content: userMessage }],
          output_config: { effort: 'low', format: zodOutputFormat(AIAnswerSchema) },
          ...(useFallback ? { betas: ['server-side-fallback-2026-07-01'], fallbacks: 'default' as const } : {}),
        },
        { signal: withTimeout(signal) },
      );
      if (response.stop_reason === 'refusal') {
        throw new ProviderError('The AI provider declined to answer this request.', 'refused');
      }
      if (response.stop_reason === 'max_tokens') {
        throw new ProviderError('The AI response was cut off before it finished.', 'invalid-response');
      }
      const text = response.content.flatMap((b) => (b.type === 'text' ? [b.text] : [])).join('');
      if (!text) throw new ProviderError('The AI provider returned an empty response.', 'invalid-response');
      return text;
    } catch (err) {
      throw mapAnthropicError(err, signal);
    }
  }

  async test(apiKey: string, model: string, signal: AbortSignal): Promise<void> {
    try {
      await this.client(apiKey).models.retrieve(model, {}, { signal: withTimeout(signal) });
    } catch (err) {
      throw mapAnthropicError(err, signal);
    }
  }
}

function mapAnthropicError(err: unknown, signal: AbortSignal): ProviderError {
  if (err instanceof ProviderError) return err;
  if (signal.aborted) return new ProviderError('Request cancelled.', 'cancelled');
  if (err instanceof Anthropic.AuthenticationError || err instanceof Anthropic.PermissionDeniedError) {
    return new ProviderError('The Anthropic API key was rejected. Check it in Settings.', 'auth');
  }
  if (err instanceof Anthropic.NotFoundError) {
    return new ProviderError('The selected Claude model was not found. Check the model name in Settings.', 'auth');
  }
  if (err instanceof Anthropic.RateLimitError) {
    return new ProviderError('Anthropic rate limit or spending limit reached. Try again later.', 'network');
  }
  if (err instanceof Anthropic.BadRequestError) {
    return new ProviderError('Anthropic rejected the request. The model may not support this feature.', 'unknown');
  }
  if (err instanceof Anthropic.APIConnectionError) {
    return new ProviderError('Could not reach Anthropic. Check your internet connection.', 'network');
  }
  if (err instanceof Anthropic.APIError) {
    return new ProviderError(`Anthropic is unavailable right now (HTTP ${err.status ?? 'error'}).`, 'network');
  }
  return new ProviderError('The AI request failed.', 'unknown');
}

export class OpenAIProvider implements AIProvider {
  readonly id = 'openai' as const;
  readonly label = 'OpenAI';

  constructor(private readonly fetchImpl: typeof fetch = fetch) {}

  async ask({ apiKey, model, userMessage, signal }: ProviderRequest): Promise<string> {
    const res = await this.request(apiKey, signal, 'https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      body: JSON.stringify({
        model,
        messages: [
          { role: 'system', content: AI_SYSTEM_PROMPT },
          { role: 'user', content: userMessage },
        ],
        response_format: {
          type: 'json_schema',
          json_schema: { name: 'study_answer', strict: true, schema: AI_ANSWER_JSON_SCHEMA },
        },
      }),
    });
    const data = (await res.json().catch(() => null)) as {
      choices?: { message?: { content?: string | null; refusal?: string | null }; finish_reason?: string }[];
    } | null;
    const choice = data?.choices?.[0];
    if (choice?.message?.refusal) throw new ProviderError('The AI provider declined to answer this request.', 'refused');
    if (choice?.finish_reason === 'length') {
      throw new ProviderError('The AI response was cut off before it finished.', 'invalid-response');
    }
    const text = choice?.message?.content;
    if (typeof text !== 'string' || !text) {
      throw new ProviderError('The AI provider returned an empty response.', 'invalid-response');
    }
    return text;
  }

  async test(apiKey: string, model: string, signal: AbortSignal): Promise<void> {
    await this.request(apiKey, signal, `https://api.openai.com/v1/models/${encodeURIComponent(model)}`, {
      method: 'GET',
    });
  }

  private async request(apiKey: string, signal: AbortSignal, url: string, init: RequestInit): Promise<Response> {
    let res: Response;
    try {
      res = await this.fetchImpl(url, {
        ...init,
        headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
        signal: withTimeout(signal),
      });
    } catch {
      if (signal.aborted) throw new ProviderError('Request cancelled.', 'cancelled');
      throw new ProviderError('Could not reach OpenAI. Check your internet connection.', 'network');
    }
    if (res.ok) return res;
    if (res.status === 401 || res.status === 403) {
      throw new ProviderError('The OpenAI API key was rejected. Check it in Settings.', 'auth');
    }
    if (res.status === 404) {
      throw new ProviderError('The selected OpenAI model was not found. Check the model name in Settings.', 'auth');
    }
    if (res.status === 429) {
      throw new ProviderError('OpenAI rate limit or billing limit reached. Check your OpenAI account.', 'network');
    }
    throw new ProviderError(`OpenAI is unavailable right now (HTTP ${res.status}).`, 'network');
  }
}

/**
 * Offline provider for trying the assistant and for automated tests. Its answers are canned and
 * clearly labelled as a demo; it never contacts the network.
 */
export class DemoProvider implements AIProvider {
  readonly id = 'demo' as const;
  readonly label = 'Demo (offline, no AI)';

  async ask({ userMessage, signal }: ProviderRequest): Promise<string> {
    await new Promise((r) => setTimeout(r, 300));
    if (signal.aborted) throw new ProviderError('Request cancelled.', 'cancelled');
    const material = /<student_material>\n([\s\S]*)\n<\/student_material>/.exec(userMessage)?.[1] ?? '';
    const firstLine = material.split('\n').find((l) => l.trim())?.trim() ?? 'your question';
    return JSON.stringify({
      questionSummary: firstLine.slice(0, 200),
      leadingOption: null,
      explanation:
        'This is the offline demo provider. It shows how Lapsus presents an answer card, but it does not analyse your question. Choose OpenAI or Anthropic in Settings and add an API key to get real explanations.',
      evidence: ['No analysis was performed in demo mode.'],
      inferences: [],
      missingInformation: ['A configured AI provider'],
      confidence: 'low',
      confidenceReason: 'Demo mode never evaluates the question.',
      warning: 'Demo answer only. Do not rely on it.',
    });
  }

  async test(): Promise<void> {}
}
