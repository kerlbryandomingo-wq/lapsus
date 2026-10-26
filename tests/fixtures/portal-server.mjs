// Local stand-in for the student portal, used only by automated tests and screenshots.
// It is clearly labelled and contains no real AMA content or credentials.
import { createServer } from 'node:http';

const page = (
  title,
  body,
) => `<!doctype html><html><head><meta charset="utf-8"><title>${title}</title>
<style>
body{font-family:Segoe UI,system-ui,sans-serif;margin:0;background:#f4f6fa;color:#1d2433}
header{background:#24305e;color:#fff;padding:14px 28px;display:flex;justify-content:space-between;align-items:center}
main{max-width:880px;margin:28px auto;padding:0 24px}
.card{background:#fff;border-radius:10px;padding:20px 24px;margin-bottom:18px;box-shadow:0 1px 3px rgba(0,0,0,.08)}
.banner{background:#fff3cd;color:#664d03;padding:8px 28px;font-size:13px}
label{display:block;margin:8px 0}
button,a.btn{background:#24305e;color:#fff;border:0;border-radius:6px;padding:8px 14px;text-decoration:none;display:inline-block;margin-right:8px}
</style></head><body>
<div class="banner">Local test page used by Lapsus automated tests. This is a stand-in, not the real Discipulus portal.</div>
<header><strong>Test Portal</strong><span>Student: Test User</span></header>
<main>${body}</main></body></html>`;

const home = page(
  'Test Portal - Dashboard',
  `<div class="card"><h2>My Courses</h2><p>Introduction to Computing · Data Structures · Technical Writing</p>
  <a class="btn" id="materials" href="/materials">Learning materials</a>
  <a class="btn" id="popup" href="/popup" target="_blank">Open pop-up</a>
  <button id="fs" onclick="document.documentElement.requestFullscreen()">Request fullscreen</button></div>
  <div class="card"><h2>Practice quiz (ungraded)</h2>
  <p id="q">1. Which data structure follows the First-In, First-Out (FIFO) principle?</p>
  <label><input type="radio" name="q1"> A. Stack</label>
  <label><input type="radio" name="q1"> B. Queue</label>
  <label><input type="radio" name="q1"> C. Binary tree</label>
  <label><input type="radio" name="q1"> D. Hash table</label></div>
  <div class="card"><h2>Sign in test form</h2><input id="user" placeholder="Student ID"> <input id="pw" type="password" placeholder="Password"></div>
  <div class="card"><a id="evil-file" href="file:///C:/Windows/win.ini">file link</a> · <a id="evil-js" href="javascript:alert(1)">script link</a> · <a id="http-link" href="http://example.com/">http link</a></div>`,
);

// Exam probe: listens to every common signal a web exam can use to notice leaving fullscreen
// or the window, and records what it saw. Tests read window.__probe.
const examProbe = page(
  'Test Portal - Exam probe',
  `<div class="card"><h2>Exam probe (ungraded stand-in)</h2>
  <p>Records fullscreen, size, visibility and focus signals.</p>
  <button id="fs-btn">Enter fullscreen</button>
  <button id="exit-btn">Exit fullscreen</button>
  <button id="open-btn">Open material window</button>
  <a class="btn" id="blank-link" href="/popup" target="_blank">Open link in new window</a>
  <a id="app-link" href="ms-teams:/l/meetup-join/test">Join call (app link)</a>
  <a id="reload-link" href="/exam">Reload exam page</a>
  <input id="answer" placeholder="Answer"></div>
  <script>
  window.__probe = [];
  const log = (type, extra) => window.__probe.push(Object.assign({ type, t: Math.round(performance.now()),
    fs: !!document.fullscreenElement, w: innerWidth, h: innerHeight, sw: screen.width, sh: screen.height,
    vis: document.visibilityState, focus: document.hasFocus() }, extra || {}));
  document.addEventListener('fullscreenchange', () => log('fullscreenchange'));
  document.addEventListener('fullscreenerror', () => log('fullscreenerror'));
  document.addEventListener('visibilitychange', () => log('visibilitychange'));
  addEventListener('resize', () => log('resize'));
  addEventListener('blur', () => log('blur'));
  addEventListener('focus', () => log('focus'));
  addEventListener('keydown', (e) => log('keydown', { key: e.key, ctrl: e.ctrlKey }));
  document.getElementById('fs-btn').onclick = () =>
    document.documentElement.requestFullscreen().then(() => log('request-resolved'), () => log('request-rejected'));
  document.getElementById('exit-btn').onclick = () => document.exitFullscreen().catch(() => {});
  document.getElementById('open-btn').onclick = () => { const w = window.open('/popup', 'material'); log('open-result', { opened: !!w }); };
  const q = new URLSearchParams(location.search);
  if (q.has('autofs')) document.documentElement.requestFullscreen().then(() => log('request-resolved'), () => log('request-rejected'));
  if (q.has('autopopup')) setTimeout(() => { const w = window.open('/popup', 'auto'); log('open-result', { opened: !!w }); }, 200);
  log('load');
  </script>`,
);

export function startPortalServer(port = 0) {
  const server = createServer((req, res) => {
    const url = new URL(req.url ?? '/', 'http://localhost');
    if (url.pathname === '/' || url.pathname === '/dashboard') {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      return res.end(home);
    }
    if (url.pathname === '/materials') {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      return res.end(
        page(
          'Test Portal - Materials',
          '<div class="card"><h2>Week 3 notes</h2><p>Queues and stacks.</p><a class="btn" href="/notes.txt" download>Download notes</a></div>',
        ),
      );
    }
    if (url.pathname === '/exam') {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      return res.end(examProbe);
    }
    if (url.pathname === '/popup') {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      return res.end(
        page('Test Portal - Pop-up', '<div class="card"><h2>Pop-up window</h2></div>'),
      );
    }
    if (url.pathname === '/notes.txt') {
      res.writeHead(200, {
        'content-type': 'text/plain',
        'content-disposition': 'attachment; filename="week3-notes.txt"',
      });
      return res.end('Queue: FIFO. Stack: LIFO.\n');
    }
    res.writeHead(404);
    res.end('not found');
  });
  return new Promise((resolve) => {
    server.listen(port, '127.0.0.1', () => {
      const addr = server.address();
      resolve({ server, url: `http://127.0.0.1:${addr.port}/` });
    });
  });
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].split('/').pop())) {
  startPortalServer(Number(process.env.PORT) || 4789).then(({ url }) =>
    console.log(`Test portal on ${url}`),
  );
}
