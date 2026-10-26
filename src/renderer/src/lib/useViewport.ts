import { useEffect, useLayoutEffect, useRef } from 'react';
import { api } from './api';

/**
 * Reports where the web content view should sit. The main process places the sandboxed page
 * view over this rectangle, so the app UI and the page never overlap.
 */
export function useViewport(visible: boolean) {
  const ref = useRef<HTMLDivElement>(null);
  const last = useRef('');

  const report = () => {
    const el = ref.current;
    const rect = el?.getBoundingClientRect();
    const payload =
      rect && visible
        ? { x: rect.left, y: rect.top, width: rect.width, height: rect.height, visible: true }
        : { x: 0, y: 0, width: 0, height: 0, visible: false };
    const key = JSON.stringify(payload);
    if (key === last.current) return;
    last.current = key;
    void api.invoke('view:set-bounds', payload);
  };

  useLayoutEffect(report);

  useEffect(() => {
    const el = ref.current;
    const ro = new ResizeObserver(() => report());
    if (el) ro.observe(el);
    window.addEventListener('resize', report);
    return () => {
      ro.disconnect();
      window.removeEventListener('resize', report);
    };
  });

  return ref;
}
