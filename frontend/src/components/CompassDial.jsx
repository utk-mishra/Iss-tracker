// Compass/sky-dial SVG for rise -> set direction (PROMPT.md section 7).
// Plain inline SVG: no external image assets (CSP-compliant). Null-safe.
function toXY(azimuthDeg, radius, center) {
  const radians = ((azimuthDeg - 90) * Math.PI) / 180;
  return {
    x: center + radius * Math.cos(radians),
    y: center + radius * Math.sin(radians),
  };
}

export default function CompassDial({ riseAzimuthDeg, setAzimuthDeg }) {
  const size = 120;
  const center = size / 2;
  const radius = size / 2 - 10;

  const hasRise = typeof riseAzimuthDeg === 'number' && !Number.isNaN(riseAzimuthDeg);
  const hasSet = typeof setAzimuthDeg === 'number' && !Number.isNaN(setAzimuthDeg);
  const risePoint = hasRise ? toXY(riseAzimuthDeg, radius, center) : null;
  const setPoint = hasSet ? toXY(setAzimuthDeg, radius, center) : null;

  return (
    <svg
      viewBox={`0 0 ${size} ${size}`}
      width={size}
      height={size}
      role="img"
      aria-label="Rise to set direction compass dial"
    >
      <circle cx={center} cy={center} r={radius} className="compass-ring" />
      {risePoint && <circle cx={risePoint.x} cy={risePoint.y} r={5} className="compass-rise" />}
      {setPoint && <circle cx={setPoint.x} cy={setPoint.y} r={5} className="compass-set" />}
      {risePoint && setPoint && (
        <line
          x1={risePoint.x}
          y1={risePoint.y}
          x2={setPoint.x}
          y2={setPoint.y}
          className="compass-path"
        />
      )}
    </svg>
  );
}
