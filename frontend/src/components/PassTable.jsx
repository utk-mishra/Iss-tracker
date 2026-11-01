import { formatLocalTime, formatUtcTooltip } from '../utils/time.js';

// Non-observable passes muted, observable highlighted, zero-length visible passes
// labelled "visible (instant)" (PROMPT.md section 7). Never crashes on null fields
// (visibleStart, magnitude, cloudCoverPct can all be null).
function rowClassName(pass) {
  if (pass.observable) {
    return 'pass-observable';
  }
  return 'pass-muted';
}

function visibilityLabel(pass) {
  if (pass.visible && pass.visibleDurationSec === 0) {
    return 'visible (instant)';
  }
  if (pass.observable) {
    return 'visible';
  }
  return 'not visible';
}

function TimeCell({ isoInstant }) {
  if (!isoInstant) {
    return <td>{'\u2014'}</td>;
  }
  return <td title={formatUtcTooltip(isoInstant)}>{formatLocalTime(isoInstant)}</td>;
}

export default function PassTable({ passes }) {
  return (
    <table className="pass-table">
      <thead>
        <tr>
          <th>Rise</th>
          <th>Culmination</th>
          <th>Set</th>
          <th>Direction</th>
          <th>Max elevation</th>
          <th>Duration</th>
          <th>Visibility</th>
          <th>Magnitude</th>
          <th>Cloud cover</th>
        </tr>
      </thead>
      <tbody>
        {passes.map((pass, index) => (
          <tr key={`${pass.rise}-${index}`} className={rowClassName(pass)}>
            <TimeCell isoInstant={pass.rise} />
            <TimeCell isoInstant={pass.culmination} />
            <TimeCell isoInstant={pass.set} />
            <td>{pass.riseDirection} &rarr; {pass.setDirection}</td>
            <td>{pass.maxElevationDeg}&deg;</td>
            <td>{pass.durationSec}s</td>
            <td>{visibilityLabel(pass)}</td>
            <td>{pass.magnitude === null || pass.magnitude === undefined ? '\u2014' : pass.magnitude}</td>
            <td>{pass.cloudCoverPct === null || pass.cloudCoverPct === undefined ? '\u2014' : `${pass.cloudCoverPct}%`}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
