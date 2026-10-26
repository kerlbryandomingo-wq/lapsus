// Runs the built Lapsus main process with no DevTools client attached, then drives the window
// the way a student would (another window takes focus, minimize, restore) and records what
// the exam probe page observed. Used by visibility.mjs (test WIN-01b and WIN-02).
const { app, BaseWindow, BrowserWindow, webContents } = require('electron');
const { resolve } = require('node:path');

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitForProbe() {
  for (let i = 0; i < 100; i++) {
    const page = webContents.getAllWebContents().find((w) => w.getURL().includes('/exam'));
    if (page && (await page.executeJavaScript('Array.isArray(window.__probe)').catch(() => false)))
      return page;
    await wait(100);
  }
  throw new Error('exam probe page did not load');
}

app.on('ready', async () => {
  const result = { steps: [] };
  try {
    const page = await waitForProbe();
    const main = BaseWindow.getAllWindows().find((w) => !(w instanceof BrowserWindow));
    const take = async (name) => {
      const events = await page.executeJavaScript(
        'JSON.stringify(window.__probe.splice(0).map(e => ({ type: e.type, vis: e.vis, focus: e.focus })))',
      );
      result.steps.push({ name, minimized: main.isMinimized(), events: JSON.parse(events) });
    };
    main.focus();
    page.focus();
    await wait(800);
    await take('start');

    const other = new BrowserWindow({ width: 400, height: 300, show: true });
    await other.loadURL('data:text/html,other app');
    other.focus();
    await wait(1000);
    await take('other-window-focused');
    other.destroy();
    main.focus();
    page.focus();
    await wait(1000);
    await take('main-refocused');

    main.minimize();
    await wait(1500);
    await take('minimized');
    main.restore();
    main.focus();
    await wait(1500);
    await take('restored');

    // Crash recovery: the UI's "Try again" button calls nav:reload.
    const ui = webContents
      .getAllWebContents()
      .find((w) => w.getURL().startsWith('app://lapsus/index'));
    page.forcefullyCrashRenderer();
    await wait(800);
    const errorAfterCrash = await ui.executeJavaScript(
      "window.lapsus.invoke('state:get').then(s => s.nav.error && s.nav.error.kind)",
    );
    await ui.executeJavaScript("window.lapsus.invoke('nav:reload', { ignoreCache: false })");
    let recovered = false;
    for (let i = 0; i < 50 && !recovered; i++) {
      await wait(100);
      recovered =
        !page.isCrashed() &&
        (await page.executeJavaScript('Array.isArray(window.__probe)').catch(() => false));
    }
    const errorAfterReload = await ui.executeJavaScript(
      "window.lapsus.invoke('state:get').then(s => s.nav.error && s.nav.error.kind)",
    );
    result.crash = { errorAfterCrash, recovered, errorAfterReload };
  } catch (err) {
    result.error = String(err && err.stack ? err.stack : err);
  }
  process.stdout.write(`LAPSUS_RESULT ${JSON.stringify(result)}\n`);
  app.exit(0);
});

require(resolve(__dirname, '../../../out/main/index.js'));
