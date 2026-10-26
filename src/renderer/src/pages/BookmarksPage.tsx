import { useEffect, useMemo, useState } from 'react';
import { Bookmark as BookmarkIcon, Pencil, Trash2, Check, X, Plus, Folder } from 'lucide-react';
import type { Bookmark } from '@shared/types';
import { useStore } from '../lib/store';
import { api } from '../lib/api';
import { hostOf } from '../lib/format';
import { Button, EmptyState, IconButton } from '../components/ui';
import { PageLayout } from './Layout';

function Row({ b, onChange }: { b: Bookmark; onChange: () => void }) {
  const { dispatch } = useStore();
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(b.title);
  const [folder, setFolder] = useState(b.folder);
  const save = async () => {
    await api.invoke('bookmarks:update', { id: b.id, title, folder });
    setEditing(false);
    onChange();
  };
  if (editing) {
    return (
      <li className="row row--editing">
        <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} aria-label="Bookmark name" maxLength={300} />
        <input className="input input--narrow" value={folder} onChange={(e) => setFolder(e.target.value)} placeholder="Folder" aria-label="Folder" maxLength={60} />
        <IconButton label="Save" onClick={() => void save()}>
          <Check size={16} />
        </IconButton>
        <IconButton label="Cancel" onClick={() => setEditing(false)}>
          <X size={16} />
        </IconButton>
      </li>
    );
  }
  return (
    <li className="row">
      <button
        type="button"
        className="row__main"
        onClick={() => {
          dispatch({ type: 'page', page: 'portal' });
          void api.invoke('bookmarks:open', { id: b.id });
        }}
      >
        <span className="avatar avatar--maroon" aria-hidden="true">
          {hostOf(b.url).charAt(0).toUpperCase()}
        </span>
        <span className="row__text">
          <span className="row__title">{b.title}</span>
          <span className="row__meta">{b.url}</span>
        </span>
      </button>
      <IconButton label="Edit bookmark" onClick={() => setEditing(true)}>
        <Pencil size={15} />
      </IconButton>
      <IconButton
        label="Delete bookmark"
        onClick={async () => {
          await api.invoke('bookmarks:remove', { id: b.id });
          onChange();
        }}
      >
        <Trash2 size={15} />
      </IconButton>
    </li>
  );
}

export function BookmarksPage() {
  const { state } = useStore();
  const [items, setItems] = useState<Bookmark[]>([]);
  const load = () => void api.invoke('bookmarks:list').then(setItems);
  useEffect(load, [state.nav?.bookmarked]);

  const groups = useMemo(() => {
    const map = new Map<string, Bookmark[]>();
    for (const b of items) {
      const key = b.folder || '';
      map.set(key, [...(map.get(key) ?? []), b]);
    }
    return [...map.entries()].sort(([a], [b]) => (a === '' ? -1 : b === '' ? 1 : a.localeCompare(b)));
  }, [items]);

  const canAdd = !!state.nav?.url && /^https?:/.test(state.nav.url) && !state.nav.bookmarked;

  return (
    <PageLayout
      title="Bookmarks"
      subtitle="Saved pages, organised into folders."
      actions={
        <Button variant="primary" icon={<Plus size={16} />} disabled={!canAdd} onClick={() => void api.invoke('bookmarks:toggle-current').then(load)}>
          Bookmark current page
        </Button>
      }
    >
      {items.length === 0 ? (
        <EmptyState icon={<BookmarkIcon size={24} />} title="No bookmarks yet">
          Open a page and select the star in the address bar to save it here.
        </EmptyState>
      ) : (
        groups.map(([folder, list]) => (
          <section key={folder || 'none'} className="group">
            <h2 className="group__title">
              <Folder size={15} aria-hidden="true" /> {folder || 'Unsorted'}
            </h2>
            <ul className="rows">
              {list.map((b) => (
                <Row key={b.id} b={b} onChange={load} />
              ))}
            </ul>
          </section>
        ))
      )}
    </PageLayout>
  );
}
