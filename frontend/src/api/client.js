// Thin fetch wrapper for the ISS Tracker backend API (PROMPT.md section 7).
// Dev proxy rewrites /api -> backend (see vite.config.js); in production the
// build is served by the same origin, so relative paths work everywhere.

export class ApiError extends Error {
  constructor(message, status, retryAfter) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

async function get(path, params) {
  const query = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      query.set(key, String(value));
    }
  });
  const url = `${path}?${query.toString()}`;

  let response;
  try {
    response = await fetch(url, { headers: { Accept: 'application/json' } });
  } catch (networkError) {
    throw new ApiError('Network error: could not reach the server.', 0, null);
  }

  const retryAfter = response.headers.get('Retry-After');

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = await response.json();
      if (body && body.error) {
        message = body.error;
      }
    } catch {
      // Response body wasn't JSON; keep the generic message.
    }
    throw new ApiError(message, response.status, retryAfter);
  }

  return response.json();
}

export function fetchOverview({ lat, lon, offline }) {
  return get('/api/v1/iss/overview', { lat, lon, offline });
}

export function fetchPosition({ offline }) {
  return get('/api/v1/iss/position', { offline });
}

export function fetchPasses({ lat, lon, offline }) {
  return get('/api/v1/iss/passes', { lat, lon, offline });
}
