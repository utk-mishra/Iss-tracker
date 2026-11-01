import SourceBadge from './SourceBadge.jsx';
import { formatLocalTime, formatUtcTooltip } from '../utils/time.js';

// Current ISS position card, polled every 5-10s by usePositionPolling
// (PROMPT.md section 7). Null-safe: position may not have loaded yet.
export default function PositionCard({ position, meta }) {
  if (!position) {
    return (
      <section className="card position-card" aria-label="ISS position">
        <p>Position not available yet.</p>
      </section>
    );
  }

  return (
    <section className="card position-card" aria-label="ISS position">
      <header>
        <h2>Current position</h2>
        {meta && <SourceBadge source={meta.source} ageMs={meta.ageMs} />}
      </header>
      <dl>
        <dt>Latitude</dt>
        <dd>{position.latitude}&deg;</dd>
        <dt>Longitude</dt>
        <dd>{position.longitude}&deg;</dd>
        <dt>Altitude</dt>
        <dd>{position.altitudeKm} km</dd>
        <dt>Velocity</dt>
        <dd>{position.velocityKmh} km/h</dd>
      </dl>
      {position.observedAt && (
        <p className="observed-at" title={formatUtcTooltip(position.observedAt)}>
          Observed at {formatLocalTime(position.observedAt)}
        </p>
      )}
      {position.note && <p className="note">{position.note}</p>}
    </section>
  );
}
