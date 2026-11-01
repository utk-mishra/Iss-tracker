import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fetchOverview, fetchPosition, ApiError } from '../../src/api/client.js';

describe('fetchOverview', () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a successful response, when overview is fetched, then the parsed JSON is returned', async () => {
    const body = { position: {}, passes: [] };
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => body,
      headers: new Headers(),
    });

    const result = await fetchOverview({ lat: 28.6139, lon: 77.209, offline: false });

    expect(result).toEqual(body);
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/iss/overview?lat=28.6139&lon=77.209&offline=false'),
      expect.any(Object)
    );
  });

  it('given a 429 response, when overview is fetched, then an ApiError carrying retryAfter is thrown', async () => {
    const headers = new Headers({ 'Retry-After': '42' });
    global.fetch.mockResolvedValue({
      ok: false,
      status: 429,
      json: async () => ({ error: 'Rate limit exceeded', status: 429 }),
      headers,
    });

    await expect(fetchOverview({ lat: 0, lon: 0, offline: false })).rejects.toMatchObject({
      status: 429,
      retryAfter: '42',
    });
  });

  it('given a 400 response, when overview is fetched, then an ApiError with the server message is thrown', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ error: 'lat must be in [-90,90]', status: 400 }),
      headers: new Headers(),
    });

    await expect(fetchOverview({ lat: 999, lon: 0, offline: false })).rejects.toMatchObject({
      status: 400,
      message: 'lat must be in [-90,90]',
    });
  });

  it('given fetch rejects (network error), when overview is fetched, then an ApiError is thrown without crashing', async () => {
    global.fetch.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(fetchOverview({ lat: 0, lon: 0, offline: false })).rejects.toBeInstanceOf(ApiError);
  });
});

describe('fetchPosition', () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given offline=true, when position is fetched, then the query string includes offline=true', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ position: {} }),
      headers: new Headers(),
    });

    await fetchPosition({ offline: true });

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('offline=true'),
      expect.any(Object)
    );
  });
});
