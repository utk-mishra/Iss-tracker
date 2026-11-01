// Every localStorage access is wrapped in try/catch (PROMPT.md section 7): private
// browsing, disabled storage, or quota errors must never crash the app.

export function safeGetItem(key) {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function safeSetItem(key, value) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // Swallow: storage may be disabled/full/unavailable (e.g. private browsing).
  }
}
