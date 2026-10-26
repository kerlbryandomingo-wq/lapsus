import { ExternalLink, ShieldCheck } from 'lucide-react';
import { REPO_URL } from '@shared/constants';
import { useStore } from '../lib/store';
import { api } from '../lib/api';
import { LapsusMark } from '../components/Brand';
import { Button, Pill } from '../components/ui';
import { PageLayout } from './Layout';
import amaLogo from '../assets/ama-logo.png';

export function AboutPage() {
  const { state } = useStore();
  const info = state.info;
  return (
    <PageLayout title="About Lapsus">
      <section className="about-hero">
        <LapsusMark size={72} />
        <div>
          <h2 className="about-hero__name">Lapsus</h2>
          <p className="about-hero__by">
            Version {info?.version} · by {info?.publisher}
          </p>
          <div className="about-hero__pills">
            <Pill tone="maroon">Windows x64</Pill>
            <Pill tone="neutral">Chromium {info?.chromium}</Pill>
            <Pill tone="neutral">Electron {info?.electron}</Pill>
          </div>
        </div>
      </section>

      <div className="about-grid">
        <section className="settings-card">
          <header className="settings-card__head">
            <h2>What Lapsus is</h2>
          </header>
          <div className="settings-card__body prose">
            <p>
              Lapsus is a focused browser for the AMA Discipulus student portal. It wraps the real portal in a clean window with
              focus mode, bookmarks, downloads and an optional AI study assistant. The portal itself is not changed.
            </p>
            <div className="about-affiliation">
              <img src={amaLogo} alt="AMA Computer College logo" className="about-affiliation__logo" draggable={false} />
              <p>
                Lapsus is an independent student project by KRLBRYN. It is <strong>not</strong> an official AMA product and is not
                affiliated with or endorsed by AMA Education System. The AMA name and logo belong to their owners.
              </p>
            </div>
          </div>
        </section>

        <section className="settings-card">
          <header className="settings-card__head">
            <h2>Security and privacy</h2>
          </header>
          <div className="settings-card__body prose">
            <p>
              <ShieldCheck size={15} aria-hidden="true" /> Web pages run in a sandbox with no access to your files or to Lapsus
              internals. Settings, history and bookmarks stay on this computer. AI requests are only sent when you press Send.
            </p>
            <p className="muted">
              No software can be guaranteed completely secure. See the security documentation in the source repository for the
              checks performed and known limitations.
            </p>
            <p className="muted">Data folder: <span data-selectable>{info?.userDataPath}</span></p>
            <Button variant="secondary" icon={<ExternalLink size={15} />} onClick={() => void api.invoke('external:open', { url: REPO_URL })}>
              Source repository
            </Button>
          </div>
        </section>
      </div>
    </PageLayout>
  );
}
