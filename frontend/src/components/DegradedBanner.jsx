// A single global banner when ANY section is degraded (PROMPT.md section 7).
export default function DegradedBanner({ meta }) {
  if (!meta) {
    return null;
  }
  const isDegraded =
    meta.position?.degraded || meta.passes?.meta?.degraded || meta.cloud?.degraded;

  if (!isDegraded) {
    return null;
  }

  return (
    <div role="status" className="banner banner-degraded">
      Some data is degraded (cached or fallback). Live values may be out of date.
    </div>
  );
}
