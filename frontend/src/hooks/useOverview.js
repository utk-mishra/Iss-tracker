import { useEffect, useRef, useState } from 'react';
import { fetchOverview } from '../api/client.js';
import usePageVisibility from './usePageVisibility.js';

const DEFAULT_POLL_MS = 15000;

// Fetches /overview on mount and whenever lat/lon/offline change, then polls.
// On error, the LAST GOOD data is kept on screen alongside an error banner
// (PROMPT.md section 7). Polling pauses while the tab is hidden.
export default function useOverview({ lat, lon, offline, pollMs = DEFAULT_POLL_MS }) {
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
        const result = await fetchOverview({ lat, lon, offline });
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err);
          // Intentionally do NOT clear `data` here: keep the last good data on screen.
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
  }, [lat, lon, offline, pollMs]);

  return { data, error };
}
