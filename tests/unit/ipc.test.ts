import { describe, expect, it } from 'vitest';
import { IpcRequests, validateRequest } from '@shared/ipc';

describe('IPC validation', () => {
  it('rejects unknown channels', () => {
    for (const ch of [
      'fs:read',
      'shell:exec',
      '__proto__',
      'constructor',
      'toString',
      '',
      42,
      null,
    ]) {
      expect(validateRequest(ch, undefined).ok).toBe(false);
    }
  });

  it('rejects unexpected arguments on argument-less channels', () => {
    expect(validateRequest('nav:back', { evil: true }).ok).toBe(false);
    expect(validateRequest('nav:back', undefined).ok).toBe(true);
  });

  it('rejects extra keys and wrong types', () => {
    expect(validateRequest('nav:go', { input: 'x', extra: 1 }).ok).toBe(false);
    expect(validateRequest('nav:go', { input: 5 }).ok).toBe(false);
    expect(validateRequest('nav:go', { input: 'a'.repeat(5000) }).ok).toBe(false);
    expect(validateRequest('nav:go', { input: 'example.com' }).ok).toBe(true);
  });

  it('bounds window geometry', () => {
    expect(
      validateRequest('view:set-bounds', { x: 0, y: 0, width: 1e9, height: 1, visible: true }).ok,
    ).toBe(false);
    expect(
      validateRequest('view:set-bounds', {
        x: Number.NaN,
        y: 0,
        width: 1,
        height: 1,
        visible: true,
      }).ok,
    ).toBe(false);
    expect(
      validateRequest('view:set-bounds', { x: 0, y: 52, width: 800, height: 600, visible: true })
        .ok,
    ).toBe(true);
  });

  it('rejects unknown settings keys and invalid values', () => {
    expect(validateRequest('settings:update', { nodeIntegration: true }).ok).toBe(false);
    expect(validateRequest('settings:update', { theme: 'neon' }).ok).toBe(false);
    expect(validateRequest('settings:update', { aiMonthlyRequestLimit: -1 }).ok).toBe(false);
    expect(validateRequest('settings:update', { openaiModel: 'gpt; rm -rf /' }).ok).toBe(false);
    expect(validateRequest('settings:update', { theme: 'dark' }).ok).toBe(true);
  });

  it('only accepts API keys for known providers and safe ids', () => {
    expect(validateRequest('ai:set-key', { provider: 'evil', key: 'x'.repeat(20) }).ok).toBe(false);
    expect(validateRequest('bookmarks:remove', { id: '../../etc' }).ok).toBe(false);
  });

  it('has no generic or filesystem channels', () => {
    const names = Object.keys(IpcRequests);
    expect(names.some((n) => /^(fs|shell|exec|eval|file|node|require)/.test(n))).toBe(false);
    expect(names).not.toContain('invoke');
  });
});
