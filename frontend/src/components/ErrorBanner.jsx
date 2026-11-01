// On fetch error, keep the last good data on screen with an error banner
// (PROMPT.md section 7). Shows Retry-After when present (429).
export default function ErrorBanner({ error }) {
  if (!error) {
    return null;
  }

  return (
    <div role="alert" className="banner banner-error">
      {error.message}
      {error.retryAfter && (
        <span> Try again in {error.retryAfter} seconds.</span>
      )}
    </div>
  );
}
