import { useEffect, useState } from 'react';

// Countdown to the next observable pass (PROMPT.md section 7). Null-safe.
function formatRemaining(ms) {
  if (ms <= 0) {
    return 'now';
  }
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return `${hours}h ${minutes}m ${seconds}s`;
}

export default function Countdown({ targetIso }) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!targetIso) {
      return undefined;
    }
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, [targetIso]);

  if (!targetIso) {
    return <span className="countdown">{'\u2014'}</span>;
  }

  const target = new Date(targetIso).getTime();
  if (Number.isNaN(target)) {
    return <span className="countdown">{'\u2014'}</span>;
  }

  return <span className="countdown">{formatRemaining(target - now)}</span>;
}
