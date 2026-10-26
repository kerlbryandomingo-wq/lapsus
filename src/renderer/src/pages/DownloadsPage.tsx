import { Download, FolderOpen, X, ExternalLink, ShieldAlert } from 'lucide-react';
import { useStore } from '../lib/store';
import { api } from '../lib/api';
import { formatBytes, hostOf, relativeTime } from '../lib/format';
import { Button, EmptyState, IconButton, Pill, ProgressRing } from '../components/ui';
import { PageLayout } from './Layout';

export function DownloadsPage() {
  const { state, dispatch } = useStore();
  const items = state.downloads;
  return (
    <PageLayout
      title="Downloads"
      subtitle="Files saved from websites during this session."
      actions={
        <Button
          variant="secondary"
          disabled={!items.some((d) => d.state !== 'progressing')}
          onClick={() => void api.invoke('downloads:clear')}
        >
          Clear list
        </Button>
      }
    >
      {items.length === 0 ? (
        <EmptyState icon={<Download size={24} />} title="No downloads yet">
          Files you download from the portal, such as learning materials, will appear here. Lapsus
          always asks where to save them.
        </EmptyState>
      ) : (
        <ul className="rows">
          {items.map((d) => {
            const progress = d.totalBytes > 0 ? d.receivedBytes / d.totalBytes : undefined;
            const tone =
              d.state === 'completed' ? 'success' : d.state === 'progressing' ? 'gold' : 'danger';
            return (
              <li key={d.id} className="row row--download">
                <ProgressRing
                  value={d.state === 'completed' ? 1 : d.state === 'progressing' ? progress : 0}
                  label={`${d.filename} ${d.state}`}
                  size={44}
                  stroke={5}
                  tone={
                    d.state === 'completed'
                      ? 'success'
                      : d.state === 'progressing'
                        ? 'gold'
                        : 'maroon'
                  }
                >
                  <Download size={16} aria-hidden="true" />
                </ProgressRing>
                <div className="row__text">
                  <span className="row__title">
                    {d.filename}{' '}
                    {d.dangerous && (
                      <Pill tone="warning">
                        <ShieldAlert size={12} aria-hidden="true" /> Program
                      </Pill>
                    )}
                  </span>
                  <span className="row__meta">
                    {hostOf(d.url)} ·{' '}
                    {d.state === 'progressing'
                      ? `${formatBytes(d.receivedBytes)} of ${d.totalBytes ? formatBytes(d.totalBytes) : 'unknown size'}`
                      : formatBytes(d.receivedBytes)}{' '}
                    · {relativeTime(d.startedAt)}
                  </span>
                </div>
                <Pill tone={tone}>
                  {d.state === 'progressing'
                    ? 'Downloading'
                    : d.state === 'completed'
                      ? 'Done'
                      : d.state === 'cancelled'
                        ? 'Cancelled'
                        : 'Failed'}
                </Pill>
                {d.state === 'progressing' && (
                  <IconButton
                    label="Cancel download"
                    onClick={() => void api.invoke('downloads:cancel', { id: d.id })}
                  >
                    <X size={16} />
                  </IconButton>
                )}
                {d.state === 'completed' && (
                  <>
                    <IconButton
                      label={d.dangerous ? 'Show in folder' : 'Open file'}
                      onClick={async () => {
                        const r = await api.invoke('downloads:open', { id: d.id });
                        if (!r.ok && r.message)
                          dispatch({
                            type: 'notice',
                            notice: { level: 'info', message: r.message },
                          });
                      }}
                    >
                      <ExternalLink size={16} />
                    </IconButton>
                    <IconButton
                      label="Show in folder"
                      onClick={() => void api.invoke('downloads:show', { id: d.id })}
                    >
                      <FolderOpen size={16} />
                    </IconButton>
                  </>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </PageLayout>
  );
}
