// Offline toggle; persistence to localStorage is handled by the parent via the
// useOfflineToggle hook (PROMPT.md section 7), this component is presentation-only.
export default function OfflineToggle({ offline, onChange }) {
  return (
    <label className="offline-toggle">
      <input
        type="checkbox"
        checked={offline}
        onChange={(e) => onChange(e.target.checked)}
      />
      Offline mode
    </label>
  );
}
