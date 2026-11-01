// Alert cards for the next visible pass's conditional alerts (PROMPT.md sections 1, 7).
export default function AlertList({ alerts }) {
  if (!alerts || alerts.length === 0) {
    return null;
  }

  return (
    <ul className="alert-list">
      {alerts.map((alertText) => (
        <li key={alertText} className="alert-card">{alertText}</li>
      ))}
    </ul>
  );
}
