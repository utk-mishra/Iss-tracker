// Validation rules mirroring the backend contract exactly (PROMPT.md sections 6, 7):
// lat in [-90,90], lon in [-180,180], reject NaN/Infinity/non-numeric; offline must be
// sent as exactly "true" or "false".

function parseFiniteNumber(raw) {
  if (raw === null || raw === undefined || String(raw).trim() === '') {
    return null;
  }
  const value = Number(raw);
  if (Number.isNaN(value) || !Number.isFinite(value)) {
    return null;
  }
  return value;
}

export function validateLatitude(raw) {
  const value = parseFiniteNumber(raw);
  if (value === null || value < -90 || value > 90) {
    return { valid: false, value: null, error: 'Latitude must be a number between -90 and 90.' };
  }
  return { valid: true, value };
}

export function validateLongitude(raw) {
  const value = parseFiniteNumber(raw);
  if (value === null || value < -180 || value > 180) {
    return { valid: false, value: null, error: 'Longitude must be a number between -180 and 180.' };
  }
  return { valid: true, value };
}

export function validateOfflineFlag(isOffline) {
  return isOffline ? 'true' : 'false';
}
