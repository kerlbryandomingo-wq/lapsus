// Starts the packaged Lapsus executable against the local stand-in portal and checks that it
// launches, loads the portal, and stays running. Usage: node scripts/smoke-packaged.mjs <exe>
import { spawn, execFileSync } from 'node:child_process';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createServer } from 'node:http';

const exe = process.argv[2];
if (!exe) {
  console.error('usage: node scripts/smoke-packaged.mjs <path to Lapsus executable>');
  process.exit(2);
}

const requests = [];
const server = createServer((req, res) => {
  requests.push(req.url);
  res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
  res.end('<!doctype html><title>Smoke test portal</title><h1>Smoke test portal</h1>');
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/`;
const userData = mkdtempSync(join(tmpdir(), 'lapsus-smoke-'));

const child = spawn(exe, [], {
  env: { ...process.env, LAPSUS_E2E_PORTAL_URL: url, LAPSUS_E2E_USER_DATA: userData },
  stdio: 'ignore',
});
let exited = null;
child.on('exit', (code) => (exited = code));

const deadline = Date.now() + 45_000;
while (Date.now() < deadline && !requests.includes('/') && exited === null) {
  await new Promise((r) => setTimeout(r, 250));
}
const loaded = requests.includes('/');
await new Promise((r) => setTimeout(r, 3000));
const stillRunning = exited === null;

if (process.platform === 'win32') {
  try {
    execFileSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore' });
  } catch {
    /* already gone */
  }
} else {
  child.kill();
}
server.close();

console.log(`${loaded ? 'PASS' : 'FAIL'} packaged app loaded the portal page`);
console.log(`${stillRunning ? 'PASS' : 'FAIL'} packaged app kept running (exit code: ${exited})`);
process.exit(loaded && stillRunning ? 0 : 1);
