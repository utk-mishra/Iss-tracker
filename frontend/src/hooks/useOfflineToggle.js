import { useCallback, useState } from 'react';
import { safeGetItem, safeSetItem } from '../utils/storage.js';

const STORAGE_KEY = 'iss-tracker:offline';

// Offline toggle persisted in localStorage (PROMPT.md section 7); every access
// goes through the try/catch-wrapped storage utility.
export default function useOfflineToggle() {
  const [offline, setOfflineState] = useState(() => safeGetItem(STORAGE_KEY) === 'true');

  const setOffline = useCallback((value) => {
    setOfflineState(value);
    safeSetItem(STORAGE_KEY, value ? 'true' : 'false');
  }, []);

  return { offline, setOffline };
}
