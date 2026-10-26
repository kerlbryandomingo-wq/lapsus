import { useEffect, useMemo, useState } from 'react';
import { History as HistoryIcon, Search, Trash2 } from 'lucide-react';
import type { HistoryEntry } from '@shared/types';
import { useStore } from '../lib/store';
import { api } from '../lib/api';
import { dayLabel, hostOf } from '../lib/format';
import { Button, EmptyState, IconButton } from '../components/ui';
import { PageLayout } from './Layout';

export function HistoryPage() {
  const { state, dispatch } = useStore();
  const [query, setQuery] = useState('');
  const [items, setItems] = useState<HistoryEntry[]>([]);
  const [confirm, setConfirm] = useState(false);
  const load = () => void api.invoke('history:list', { query, limit: 500 }).then(setItems);
  useEffect(load, [query]);

  const groups = useMemo(() => {
    const map = new Map<string, HistoryEntry[]>();
    for (const e of items) {
      const k = dayLabel(e.visitedAt);
      map.set(k, [...(map.get(k) ?? []), e]);
    }
    return [...map.entries()];
  }, [items]);

  return (
    <PageLayout
      title="History"
      subtitle={
        state.settings?.historyEnabled
          ? 'Pages you visited in Lapsus. Stored only on this computer.'
          : 'History is turned off in Settings. Nothing new is recorded.'
      }
      actions={
        confirm ? (
          <>
            <span className="confirm-text">Delete all history?</span>
            <Button
              variant="danger"
              onClick={async () => {
                await api.invoke('history:clear');
                setConfirm(false);
                load();
                dispatch({
                  type: 'notice',
                  notice: { level: 'success', message: 'Browsing history cleared.' },
                });
              }}
            >
              Delete all
            </Button>
            <Button variant="ghost" onClick={() => setConfirm(false)}>
              Cancel
            </Button>
          </>
        ) : (
          <Button
            variant="secondary"
            icon={<Trash2 size={16} />}
            disabled={items.length === 0 && !query}
            onClick={() => setConfirm(true)}
          >
            Clear history
          </Button>
        )
      }
    >
      <label className="searchbox">
        <Search size={16} aria-hidden="true" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search history"
          aria-label="Search history"
        />
      </label>
      {items.length === 0 ? (
        <EmptyState
          icon={<HistoryIcon size={24} />}
          title={query ? 'No matching pages' : 'No history'}
        >
          {query ? 'Try a different search.' : 'Pages you open will be listed here.'}
        </EmptyState>
      ) : (
        groups.map(([day, list]) => (
          <section key={day} className="group">
            <h2 className="group__title">{day}</h2>
            <ul className="rows">
              {list.map((e) => (
                <li key={e.id} className="row">
                  <span className="row__time">
                    {new Date(e.visitedAt).toLocaleTimeString(undefined, {
                      hour: 'numeric',
                      minute: '2-digit',
                    })}
                  </span>
                  <button
                    type="button"
                    className="row__main"
                    onClick={() => {
                      dispatch({ type: 'page', page: 'portal' });
                      void api.invoke('history:open', { id: e.id });
                    }}
                  >
                    <span className="avatar avatar--navy" aria-hidden="true">
                      {hostOf(e.url).charAt(0).toUpperCase()}
                    </span>
                    <span className="row__text">
                      <span className="row__title">{e.title || hostOf(e.url)}</span>
                      <span className="row__meta">{e.url}</span>
                    </span>
                  </button>
                  <IconButton
                    label="Remove from history"
                    onClick={async () => {
                      await api.invoke('history:remove', { id: e.id });
                      load();
                    }}
                  >
                    <Trash2 size={15} />
                  </IconButton>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}
    </PageLayout>
  );
}
