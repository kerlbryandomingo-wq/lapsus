import { describe, expect, it, vi } from 'vitest';
import { AIService } from '../../src/main/ai/service';
import { OpenAIProvider, ProviderError, type AIProvider } from '../../src/main/ai/providers';
import { DEFAULT_SETTINGS, type Settings } from '@shared/settings';

const answer = JSON.stringify({
  questionSummary: 's',
  leadingOption: null,
  explanation: 'e',
  evidence: [],
  inferences: [],
  missingInformation: [],
  confidence: 'low',
  confidenceReason: 'r',
  warning: null,
});

function setup(
  settings: Partial<Settings>,
  provider?: Partial<AIProvider>,
  key: string | null = 'sk-test-key-123456',
) {
  const secrets = {
    encryptionAvailable: true,
    has: () => key !== null,
    get: async () => key,
  };
  let requests = 0;
  const usage = {
    snapshot: () => ({ month: '2026-10', requests }),
    canSpend: (limit: number) => requests < limit,
    record: () => {
      requests += 1;
    },
  };
  const ask = vi.fn(provider?.ask ?? (async () => answer));
  const fake: AIProvider = { id: 'openai', label: 'fake', ask, test: async () => {} };
  const svc = new AIService(
    secrets as never,
    usage as never,
    () => ({ ...DEFAULT_SETTINGS, ...settings }),
    { openai: fake, anthropic: { ...fake, id: 'anthropic' } },
  );
  return { svc, ask, getRequests: () => requests };
}

describe('AIService', () => {
  it('fails safely when no provider is configured', async () => {
    const { svc, ask } = setup({ aiProvider: 'none' });
    const r = await svc.ask('Q?', '');
    expect(r).toMatchObject({ ok: false, code: 'not-configured' });
    expect(ask).not.toHaveBeenCalled();
    expect(svc.status().ready).toBe(false);
  });

  it('fails safely when the key is missing', async () => {
    const { svc, ask } = setup({ aiProvider: 'openai' }, undefined, null);
    expect(await svc.ask('Q?', '')).toMatchObject({ ok: false, code: 'not-configured' });
    expect(ask).not.toHaveBeenCalled();
  });

  it('enforces the monthly request limit', async () => {
    const { svc, ask } = setup({ aiProvider: 'openai', aiMonthlyRequestLimit: 1 });
    expect((await svc.ask('Q1', '')).ok).toBe(true);
    expect(await svc.ask('Q2', '')).toMatchObject({ ok: false, code: 'limit' });
    expect(ask).toHaveBeenCalledTimes(1);
  });

  it('sends only the prepared, wrapped material', async () => {
    const { svc, ask } = setup({ aiProvider: 'openai' });
    await svc.ask('Q with password: secret123 inside', 'why?');
    const sent = ask.mock.calls[0]![0].userMessage as string;
    expect(sent).not.toContain('secret123');
    expect(sent).toContain('<student_material>');
  });

  it('reports malformed responses instead of showing them', async () => {
    const { svc } = setup({ aiProvider: 'openai' }, { ask: async () => '{"not":"an answer"}' });
    expect(await svc.ask('Q', '')).toMatchObject({ ok: false, code: 'invalid-response' });
  });

  it('maps provider errors to friendly messages', async () => {
    const { svc } = setup(
      { aiProvider: 'openai' },
      {
        ask: async () => {
          throw new ProviderError('The OpenAI API key was rejected. Check it in Settings.', 'auth');
        },
      },
    );
    expect(await svc.ask('Q', '')).toMatchObject({ ok: false, code: 'auth' });
  });

  it('demo mode works offline without a key', async () => {
    const { svc, getRequests } = setup({ aiProvider: 'demo' }, undefined, null);
    const r = await svc.ask('Which is FIFO?', '');
    expect(r.ok).toBe(true);
    expect(getRequests()).toBe(0);
  });
});

describe('OpenAIProvider', () => {
  const req = {
    apiKey: 'sk-secret-abcdef',
    model: 'm',
    userMessage: 'hi',
    signal: new AbortController().signal,
  };

  it('sends the key only in the Authorization header', async () => {
    const fetchMock = vi.fn(
      async () =>
        new Response(JSON.stringify({ choices: [{ message: { content: answer } }] }), {
          status: 200,
        }),
    );
    await new OpenAIProvider(fetchMock as never).ask(req);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('https://api.openai.com/v1/chat/completions');
    expect(String(init.body)).not.toContain('sk-secret-abcdef');
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer sk-secret-abcdef');
  });

  it.each([
    [401, 'auth'],
    [404, 'auth'],
    [429, 'network'],
    [500, 'network'],
  ])('maps HTTP %i to %s without echoing the key', async (status, code) => {
    const fetchMock = vi.fn(async () => new Response('{}', { status }));
    const err = await new OpenAIProvider(fetchMock as never).ask(req).catch((e) => e);
    expect(err).toBeInstanceOf(ProviderError);
    expect(err.code).toBe(code);
    expect(err.message).not.toContain('sk-secret');
  });

  it('treats refusals and empty output as errors', async () => {
    const refusal = vi.fn(
      async () => new Response(JSON.stringify({ choices: [{ message: { refusal: 'no' } }] })),
    );
    await expect(new OpenAIProvider(refusal as never).ask(req)).rejects.toMatchObject({
      code: 'refused',
    });
    const empty = vi.fn(async () => new Response(JSON.stringify({ choices: [] })));
    await expect(new OpenAIProvider(empty as never).ask(req)).rejects.toMatchObject({
      code: 'invalid-response',
    });
  });

  it('reports network failures', async () => {
    const down = vi.fn(async () => {
      throw new TypeError('fetch failed');
    });
    await expect(new OpenAIProvider(down as never).ask(req)).rejects.toMatchObject({
      code: 'network',
    });
  });
});
