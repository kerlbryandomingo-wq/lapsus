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
        'JSON.stringify(window.__probe.splice(0).map(e => ({ type: e.type, vis: e.vis, focus: e.focus, w: e.w, h: e.h })))',
      );
      result.steps.push({
        name,
        minimized: main.isMinimized(),
        fullscreen: main.isFullScreen(),
        bounds: main.getBounds(),
        events: JSON.parse(events),
      });
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

    // A pop-up opened by a real click on the page takes focus; the page must see blur.
    const box = await page.executeJavaScript(
      "(() => { const r = document.getElementById('open-btn').getBoundingClientRect(); return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) }; })()",
    );
    page.sendInputEvent({ type: 'mouseDown', x: box.x, y: box.y, button: 'left', clickCount: 1 });
    page.sendInputEvent({ type: 'mouseUp', x: box.x, y: box.y, button: 'left', clickCount: 1 });
    await wait(1500);
    const popups = BrowserWindow.getAllWindows();
    result.popupFocus = {
      opened: popups.length,
      popupFocused: popups[0]?.isFocused() ?? false,
      mainFocused: main.isFocused(),
    };
    await take('popup-opened');
    for (const p of popups) p.destroy();
    main.focus();
    page.focus();
    await wait(1000);
    await take('popup-closed');

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
    // Control: does a plain Electron window (not Lapsus) change size the same way when it is
    // fullscreen and another window takes focus? Reported for information only.
    if (main.isFullScreen()) {
      const control = new BrowserWindow({ show: true });
      await control.loadURL('data:text/html,control');
      control.setFullScreen(true);
      control.focus();
      await wait(1500);
      const before = control.getBounds();
      const other2 = new BrowserWindow({ width: 400, height: 300, show: true });
      await other2.loadURL('data:text/html,other app');
      other2.focus();
      await wait(1000);
      const after = control.getBounds();
      other2.destroy();
      control.destroy();
      result.control = { before, after };
    }
  } catch (err) {
    result.error = String(err && err.stack ? err.stack : err);
  }
  process.stdout.write(`LAPSUS_RESULT ${JSON.stringify(result)}\n`);
  app.exit(0);
});

require(resolve(__dirname, '../../../out/main/index.js'));
