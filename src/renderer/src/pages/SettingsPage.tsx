import { useState, type ReactNode } from 'react';
import { KeyRound, Trash2, PlugZap, ShieldCheck, Eye, EyeOff } from 'lucide-react';
import { AI_SHORTCUTS, type Settings, type SettingsPatch } from '@shared/settings';
import { SEARCH_ENGINES } from '@shared/url';
import { useStore } from '../lib/store';
import { api } from '../lib/api';
import { Button, Kbd, Pill, Switch } from '../components/ui';
import { PageLayout } from './Layout';

function Section({ id, title, description, children }: { id: string; title: string; description?: string; children: ReactNode }) {
  return (
    <section className="settings-card" aria-labelledby={id}>
      <header className="settings-card__head">
        <h2 id={id}>{title}</h2>
        {description && <p>{description}</p>}
      </header>
      <div className="settings-card__body">{children}</div>
    </section>
  );
}

function Field({ label, hint, children }: { label: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field__label">{label}</span>
      {children}
      {hint && <span className="field__hint">{hint}</span>}
    </label>
  );
}

function ApiKeyForm({ provider }: { provider: 'openai' | 'anthropic' }) {
  const { state, dispatch } = useStore();
  const [key, setKey] = useState('');
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const stored = state.ai?.provider === provider && state.ai.keyStored;
  const name = provider === 'openai' ? 'OpenAI' : 'Anthropic';
  const console = provider === 'openai' ? 'platform.openai.com' : 'platform.claude.com';

  const save = async () => {
    setBusy(true);
    try {
      await api.invoke('ai:set-key', { provider, key });
      setKey('');
      dispatch({ type: 'notice', notice: { level: 'success', message: `${name} API key saved securely on this computer.` } });
    } catch {
      dispatch({ type: 'notice', notice: { level: 'error', message: 'The key could not be saved. Secure storage may be unavailable.' } });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="keyform">
      <Field
        label={`${name} API key`}
        hint={
          <>
            Create a key at {console}. API use is billed by {name} separately from any ChatGPT or Claude subscription. The key is
            encrypted with Windows data protection and never shown again or sent anywhere except {name}.
          </>
        }
      >
        <div className="keyform__row">
          <input
            className="input"
            type={show ? 'text' : 'password'}
            value={key}
            autoComplete="off"
            spellCheck={false}
            placeholder={stored ? 'A key is saved. Enter a new one to replace it.' : 'Paste your API key'}
            onChange={(e) => setKey(e.target.value)}
          />
          <button type="button" className="icon-btn" aria-label={show ? 'Hide key' : 'Show key'} onClick={() => setShow((s) => !s)}>
            {show ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
          <Button variant="primary" icon={<KeyRound size={15} />} disabled={busy || key.trim().length < 10} onClick={() => void save()}>
            Save key
          </Button>
        </div>
      </Field>
      <div className="keyform__status">
        {stored ? <Pill tone="success">Key saved</Pill> : <Pill tone="neutral">No key saved</Pill>}
        {stored && (
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} onClick={() => void api.invoke('ai:clear-key', { provider })}>
            Remove key
          </Button>
        )}
        {!state.ai?.encryptionAvailable && <Pill tone="warning">Secure storage unavailable on this system</Pill>}
      </div>
    </div>
  );
}

export function SettingsPage() {
  const { state, dispatch } = useStore();
  const s = state.settings;
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; message: string } | null>(null);
  const [hostDraft, setHostDraft] = useState('');
  const [clear, setClear] = useState({ cookies: false, cache: true, history: false, downloads: true, aiKeys: false });
  if (!s) return null;

  const update = async (patch: SettingsPatch) => {
    try {
      await api.invoke('settings:update', patch);
    } catch {
      dispatch({ type: 'notice', notice: { level: 'error', message: 'That setting could not be saved.' } });
    }
  };
  const set = <K extends keyof Settings>(key: K) => (value: Settings[K]) => void update({ [key]: value } as SettingsPatch);

  return (
    <PageLayout title="Settings" subtitle="Changes are saved automatically and stay on this computer.">
      <div className="settings-grid">
        <Section id="set-appearance" title="Appearance">
          <Field label="Theme">
            <select className="input" value={s.theme} onChange={(e) => set('theme')(e.target.value as Settings['theme'])}>
              <option value="system">Match Windows</option>
              <option value="light">Light</option>
              <option value="dark">Dark</option>
            </select>
          </Field>
          <Field label="Your name" hint="Used only for the greeting on the Home screen.">
            <input className="input" value={s.displayName} maxLength={40} onChange={(e) => set('displayName')(e.target.value)} placeholder="Optional" />
          </Field>
        </Section>

        <Section id="set-startup" title="Startup and window">
          <Field label="When Lapsus starts">
            <select className="input" value={s.startup} onChange={(e) => set('startup')(e.target.value as Settings['startup'])}>
              <option value="portal">Open the Student Portal</option>
              <option value="home">Show the Home dashboard</option>
              <option value="restore">Reopen the last page</option>
            </select>
          </Field>
          <Switch
            checked={s.rememberFocusMode}
            onChange={set('rememberFocusMode')}
            label="Remember focus mode"
            description="Start in focus mode if it was on when you closed Lapsus."
          />
        </Section>

        <Section id="set-browsing" title="Browsing">
          <Field label="Search engine">
            <select className="input" value={s.searchEngine} onChange={(e) => set('searchEngine')(e.target.value as Settings['searchEngine'])}>
              {Object.entries(SEARCH_ENGINES).map(([id, e]) => (
                <option key={id} value={id}>
                  {e.label}
                </option>
              ))}
            </select>
          </Field>
          <Switch checked={s.historyEnabled} onChange={set('historyEnabled')} label="Save browsing history" description="Stored only on this computer." />
          <Switch
            checked={s.portalOnly}
            onChange={set('portalOnly')}
            label="Portal-only mode"
            description="Open only AMA portal sites inside Lapsus. Other links open in your default browser after you confirm."
          />
          {s.portalOnly && (
            <Field label="Extra sites allowed in portal-only mode" hint="For example a learning platform your course links to.">
              <div className="chips">
                {s.extraPortalHosts.map((h) => (
                  <span key={h} className="chip">
                    {h}
                    <button type="button" aria-label={`Remove ${h}`} onClick={() => void update({ extraPortalHosts: s.extraPortalHosts.filter((x) => x !== h) })}>
                      ×
                    </button>
                  </span>
                ))}
                <input
                  className="input input--narrow"
                  value={hostDraft}
                  placeholder="example.edu"
                  onChange={(e) => setHostDraft(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && hostDraft.trim()) {
                      void update({ extraPortalHosts: [...s.extraPortalHosts, hostDraft.trim().toLowerCase()] });
                      setHostDraft('');
                    }
                  }}
                />
              </div>
            </Field>
          )}
        </Section>

        <Section id="set-shortcuts" title="Keyboard shortcuts">
          <Switch
            checked={s.aiShortcutEnabled}
            onChange={set('aiShortcutEnabled')}
            label="Study Assistant shortcut"
            description="Lapsus takes this key before the page sees it. Pick another combination if a site you use needs Ctrl+K."
          />
          <Field label="Assistant shortcut">
            <select className="input" value={s.aiShortcut} disabled={!s.aiShortcutEnabled} onChange={(e) => set('aiShortcut')(e.target.value as Settings['aiShortcut'])}>
              {AI_SHORTCUTS.map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
            </select>
          </Field>
          <dl className="shortcut-list">
            {[
              ['Focus mode on or off', 'Ctrl+Shift+F'],
              ['Real fullscreen', 'F11'],
              ['Address bar', 'Ctrl+L'],
              ['Find in page', 'Ctrl+F'],
              ['Reload', 'Ctrl+R / F5'],
              ['Back / Forward', 'Alt+Left / Alt+Right'],
              ['Bookmarks / History / Downloads', 'Ctrl+D / Ctrl+H / Ctrl+J'],
              ['Zoom in / out / reset', 'Ctrl+= / Ctrl+- / Ctrl+0'],
            ].map(([what, key]) => (
              <div key={what}>
                <dt>{what}</dt>
                <dd>
                  <Kbd>{key}</Kbd>
                </dd>
              </div>
            ))}
          </dl>
        </Section>

        <Section
          id="set-ai"
          title="AI Study Assistant"
          description="The assistant explains concepts and reasons through questions you paste or select. Nothing is sent until you review the text and press Send."
        >
          <Field label="Provider">
            <select className="input" value={s.aiProvider} onChange={(e) => set('aiProvider')(e.target.value as Settings['aiProvider'])}>
              <option value="none">Off (no AI)</option>
              <option value="openai">OpenAI (your API key)</option>
              <option value="anthropic">Anthropic Claude (your API key)</option>
              <option value="demo">Demo (offline placeholder answers)</option>
            </select>
          </Field>
          {s.aiProvider === 'openai' && (
            <>
              <Field label="OpenAI model" hint="Any chat model your key can use that supports structured JSON output.">
                <input className="input" value={s.openaiModel} onChange={(e) => set('openaiModel')(e.target.value)} spellCheck={false} />
              </Field>
              <ApiKeyForm provider="openai" />
            </>
          )}
          {s.aiProvider === 'anthropic' && (
            <>
              <Field
                label="Claude model"
                hint="Default is claude-opus-5-5 (about US$4 per million input tokens and US$20 per million output tokens; roughly 2 to 4 US cents per question). claude-haiku-5-5 costs far less."
              >
                <input className="input" value={s.anthropicModel} onChange={(e) => set('anthropicModel')(e.target.value)} spellCheck={false} list="claude-models" />
                <datalist id="claude-models">
                  <option value="claude-opus-5-5" />
                  <option value="claude-sonnet-5-5" />
                  <option value="claude-haiku-5-5" />
                </datalist>
              </Field>
              <ApiKeyForm provider="anthropic" />
            </>
          )}
          <Field
            label="Monthly request limit"
            hint="Lapsus stops sending after this many requests in a calendar month. This is a local safeguard; set a hard spending limit in your provider's billing settings too."
          >
            <input
              className="input input--narrow"
              type="number"
              min={0}
              max={10000}
              value={s.aiMonthlyRequestLimit}
              onChange={(e) => {
                const n = Number(e.target.value);
                if (Number.isInteger(n) && n >= 0 && n <= 10000) set('aiMonthlyRequestLimit')(n);
              }}
            />
          </Field>
          <div className="settings-inline">
            <Button
              variant="secondary"
              icon={<PlugZap size={15} />}
              disabled={testing || s.aiProvider === 'none'}
              onClick={async () => {
                setTesting(true);
                setTestResult(await api.invoke('ai:test'));
                setTesting(false);
              }}
            >
              {testing ? 'Testing…' : 'Test connection'}
            </Button>
            {testResult && <Pill tone={testResult.ok ? 'success' : 'danger'}>{testResult.message}</Pill>}
          </div>
          <div className="callout">
            <ShieldCheck size={16} aria-hidden="true" />
            <div>
              <strong>What is sent:</strong> only the question text shown in the review step, after Lapsus removes things that look
              like passwords, tokens and email addresses. Never cookies, passwords, whole pages or your history. Each question is
              sent on its own, without earlier conversation. Requests go straight from your computer to the provider you chose.
            </div>
          </div>
        </Section>

        <Section id="set-privacy" title="Clear browsing data" description="Remove data stored by Lapsus on this computer.">
          <div className="checks">
            {(
              [
                ['cookies', 'Cookies and site data', 'Signs you out of the portal and other sites.'],
                ['cache', 'Cached images and files', 'Frees space. Pages may load slower the first time.'],
                ['history', 'Browsing history', 'Also forgets the last page for session restore.'],
                ['downloads', 'Download list', 'Files on disk are not deleted.'],
                ['aiKeys', 'Saved AI API keys', 'You will need to enter them again.'],
              ] as const
            ).map(([key, label, desc]) => (
              <label key={key} className="check">
                <input type="checkbox" checked={clear[key]} onChange={(e) => setClear({ ...clear, [key]: e.target.checked })} />
                <span>
                  <span className="check__label">{label}</span>
                  <span className="check__desc">{desc}</span>
                </span>
              </label>
            ))}
          </div>
          <Button
            variant="danger"
            icon={<Trash2 size={15} />}
            disabled={!Object.values(clear).some(Boolean)}
            onClick={async () => {
              await api.invoke('data:clear', clear);
              dispatch({ type: 'notice', notice: { level: 'success', message: 'Selected browsing data was cleared.' } });
            }}
          >
            Clear selected data
          </Button>
        </Section>
      </div>
    </PageLayout>
  );
}
