// Warning shown whenever meta.passes.tleStale is true (PROMPT.md section 7).
export default function TleStaleWarning({ tleStale }) {
  if (!tleStale) {
    return null;
  }

  return (
    <div role="alert" className="banner banner-warning">
      Orbital data (TLE) is stale - pass predictions may be less accurate.
    </div>
  );
}
