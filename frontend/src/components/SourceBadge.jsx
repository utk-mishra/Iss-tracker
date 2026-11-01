import { minutesAgo } from '../utils/time.js';

// Per-section source badges must follow meta exactly (PROMPT.md section 7):
// never infer "live" from data presence, only from meta.source.
const LABELS = {
  FRESH: () => 'Live',
  STALE: (ageMs) => `Cached ${minutesAgo(ageMs)} min ago`,
  MOCK: () => 'Fallback data',
};

const CLASS_NAMES = {
  FRESH: 'badge-fresh',
  STALE: 'badge-stale',
  MOCK: 'badge-mock',
};

export default function SourceBadge({ source, ageMs }) {
  const label = (LABELS[source] || (() => source))(ageMs);
  const className = CLASS_NAMES[source] || 'badge-unknown';

  return <span className={`badge ${className}`}>{label}</span>;
}
