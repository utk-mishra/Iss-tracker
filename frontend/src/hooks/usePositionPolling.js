import { useEffect, useRef, useState } from 'react';
import { fetchPosition } from '../api/client.js';
import usePageVisibility from './usePageVisibility.js';

const DEFAULT_POLL_MS = 5000;

// Fast poll of /position only (every 5-10s per PROMPT.md section 7), independent of
// the slower /overview poll. Pauses while the tab is hidden; keeps last good data on error.
export default function usePositionPolling({ offline, pollMs = DEFAULT_POLL_MS }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const isVisible = usePageVisibility();
  const isVisibleRef = useRef(isVisible);
  isVisibleRef.current = isVisible;

  useEffect(() => {
    let cancelled = false;

    async function load() {
      if (!isVisibleRef.current) {
        return;
      }
      try {
        const result = await fetchPosition({ offline });
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err);
        }
      }
    }

    load();
    const interval = setInterval(load, pollMs);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [offline, pollMs]);

  return { data, error };
}
