import { forwardRef } from 'react';
import { RotateCw, WifiOff, ShieldAlert, LockOpen, Ban, Bug, GraduationCap } from 'lucide-react';
import { useStore } from '../lib/store';
import { api } from '../lib/api';
import { Button, ProgressRing } from '../components/ui';
import type { PageError } from '@shared/types';
import { hostOf } from '../lib/format';

function ErrorCard({ error }: { error: PageError }) {
  const { dispatch } = useStore();
  const icon =
    error.kind === 'offline' ? <WifiOff size={28} /> :
    error.kind === 'cert' ? <ShieldAlert size={28} /> :
    error.kind === 'insecure' ? <LockOpen size={28} /> :
    error.kind === 'blocked' ? <Ban size={28} /> :
    error.kind === 'crashed' ? <Bug size={28} /> : <WifiOff size={28} />;
  const title =
    error.kind === 'offline' ? 'You are offline' :
    error.kind === 'cert' ? 'Connection not private' :
    error.kind === 'insecure' ? 'This page is not secure' :
    error.kind === 'blocked' ? 'Lapsus blocked this address' :
    error.kind === 'crashed' ? 'This page crashed' : 'Page could not be loaded';

  return (
    <div className="page-error" role="alert">
      <div className={`page-error__icon page-error__icon--${error.kind}`} aria-hidden="true">{icon}</div>
      <h2>{title}</h2>
      <p className="page-error__text">{error.description}</p>
      <p className="page-error__url" data-selectable>{error.url}</p>
      <div className="page-error__actions">
        {error.kind !== 'blocked' && error.kind !== 'insecure' && error.kind !== 'cert' && (
          <Button variant="primary" icon={<RotateCw size={16} />} onClick={() => void api.invoke('nav:reload', { ignoreCache: false })}>
            Try again
          </Button>
        )}
        <Button variant="secondary" icon={<GraduationCap size={16} />} onClick={() => void api.invoke('nav:portal')}>
          Go to Student Portal
        </Button>
        {error.kind === 'insecure' && (
          <Button variant="ghost" onClick={() => void api.invoke('nav:allow-insecure', { url: error.url })}>
            Continue anyway (not secure)
          </Button>
        )}
        <Button variant="ghost" onClick={() => dispatch({ type: 'page', page: 'home' })}>
          Back to Home
        </Button>
      </div>
    </div>
  );
}

export const PortalPage = forwardRef<HTMLDivElement>(function PortalPage(_props, ref) {
  const { state } = useStore();
  const error = state.nav?.error;
  return (
    <div className="portal">
      {error ? (
        <ErrorCard error={error} />
      ) : (
        <div ref={ref} className="portal__viewport">
          <div className="portal__placeholder">
            <ProgressRing label="Loading page" tone="maroon" size={44} stroke={5} />
            <p>Loading {state.nav?.url ? hostOf(state.nav.url) : 'the Student Portal'}…</p>
          </div>
        </div>
      )}
    </div>
  );
});
