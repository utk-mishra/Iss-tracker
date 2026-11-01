import { useEffect, useState } from 'react';

// Tracks document.visibilityState so polling hooks can pause while the tab is
// hidden (PROMPT.md section 7).
export default function usePageVisibility() {
  const [isVisible, setIsVisible] = useState(() => document.visibilityState === 'visible');

  useEffect(() => {
    function handleChange() {
      setIsVisible(document.visibilityState === 'visible');
    }
    document.addEventListener('visibilitychange', handleChange);
    return () => document.removeEventListener('visibilitychange', handleChange);
  }, []);

  return isVisible;
}
