// WIN-01b / WIN-02: focus and visibility signals with no Playwright attached.
// Usage: node tests/e2e/raw/visibility.mjs   (on Linux, run as a non-root user with a display)
import { spawn } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { startPortalServer } from '../../fixtures/portal-server.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const electron = createRequire(import.meta.url)('electron');
const { server, url } = await startPortalServer();

async function runHarness(windowMode) {
  const userData = mkdtempSync(join(tmpdir(), 'lapsus-raw-'));
  writeFileSync(join(userData, 'settings.json'), JSON.stringify({ windowMode }));
  const child = spawn(electron, [join(here, 'harness.cjs')], {
    env: { ...process.env, LAPSUS_E2E_PORTAL_URL: `${url}exam`, LAPSUS_E2E_USER_DATA: userData },
    stdio: ['ignore', 'pipe', 'inherit'],
  });
  let out = '';
  child.stdout.on('data', (d) => (out += d));
  const timer = setTimeout(() => child.kill(), 60_000);
  await new Promise((r) => child.on('exit', r));
  clearTimeout(timer);
  const line = out.split('\n').find((l) => l.startsWith('LAPSUS_RESULT '));
  if (!line) throw new Error('No result from harness. Output:\n' + out);
  const result = JSON.parse(line.slice('LAPSUS_RESULT '.length));
  if (result.error) throw new Error(result.error);
  return result;
}

const results = [];
let failed = 0;
for (const mode of ['maximized', 'fullscreen']) {
  const result = await runHarness(mode);
  const step = (n) => result.steps.find((s) => s.name === n);
  const types = (n) =>
    step(n).events.map((e) => (e.type === 'visibilitychange' ? `visibility:${e.vis}` : e.type));
  console.log(`[${mode} start]`);
  for (const s of result.steps) {
    const sizes = s.events.filter((e) => e.type === 'resize').map((e) => `${e.w}x${e.h}`);
    console.log(
      `  ${s.name}: ${types(s.name).join(', ') || '(no events)'}` +
        (sizes.length ? ` [page ${sizes.join(' -> ')}]` : '') +
        ` [window ${s.bounds.width}x${s.bounds.height}${s.fullscreen ? ', fullscreen' : ''}]`,
    );
  }
  if (result.control) {
    const { before, after } = result.control;
    console.log(
      `INFO plain Electron fullscreen window: ${before.width}x${before.height} focused, ${after.width}x${after.height} after another window took focus`,
    );
  }
  const tag = mode === 'fullscreen' ? 'WIN-02F' : 'WIN-02';
  const checks = [
    [
      `WIN-01b (${mode}) another window taking focus blurs the page`,
      types('other-window-focused').includes('blur'),
    ],
    [
      `WIN-01b (${mode}) refocusing Lapsus gives the page focus back`,
      types('main-refocused').includes('focus'),
    ],
  ];
  if (result.popupFocus?.opened && !result.popupFocus.mainFocused) {
    checks.push([
      `WIN-01c (${mode}) a clicked pop-up taking focus blurs the page`,
      types('popup-opened').includes('blur'),
    ]);
  } else {
    console.log(`NOT RUN WIN-01c (${mode}) pop-up state: ${JSON.stringify(result.popupFocus)}`);
  }
  if (step('minimized').minimized) {
    checks.push([
      `${tag} minimizing reports the page hidden`,
      types('minimized').includes('visibility:hidden'),
    ]);
    checks.push([
      `${tag} restoring reports the page visible`,
      types('restored').includes('visibility:visible'),
    ]);
  } else {
    // Some Linux window managers refuse to minimize a fullscreen window (plain Electron too).
    // That is not a pass: report it as not run, so Windows CI is where this case is verified.
    console.log(`NOT RUN ${tag} the window manager did not minimize the window`);
    if (process.platform === 'win32') checks.push([`${tag} window minimizes`, false]);
  }
  if (mode === 'maximized') {
    checks.push([
      'REG-05 a crash shows the error card',
      result.crash?.errorAfterCrash === 'crashed',
    ]);
    checks.push([
      'REG-05 Try again reloads a crashed page and clears the error',
      result.crash?.recovered === true && !result.crash?.errorAfterReload,
    ]);
  }
  for (const [name, ok] of checks) {
    console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
    if (!ok) failed++;
  }
  results.push(result);
}
server.close();
process.exit(failed ? 1 : 0);
