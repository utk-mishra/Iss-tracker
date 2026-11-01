// Accessible loading placeholder shown before the first successful fetch
// (PROMPT.md section 7: loading skeletons).
export default function LoadingSkeleton() {
  return (
    <div role="status" className="loading-skeleton" aria-label="Loading">
      <span className="skeleton-line" />
      <span className="skeleton-line" />
      <span className="skeleton-line" />
    </div>
  );
}
