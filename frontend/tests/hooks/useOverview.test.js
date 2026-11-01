import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import useOverview from '../../src/hooks/useOverview.js';
import * as client from '../../src/api/client.js';
import fixture from '../fixtures/overview-response.json';

vi.mock('../../src/api/client.js', async () => {
  const actual = await vi.importActual('../../src/api/client.js');
  return { ...actual, fetchOverview: vi.fn() };
});

describe('useOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('given a successful fetch, when the hook resolves, then it exposes the overview data and no error', async () => {
    client.fetchOverview.mockResolvedValue(fixture);

    const { result } = renderHook(() => useOverview({ lat: 28.6139, lon: 77.209, offline: false }));

    await waitFor(() => expect(result.current.data).toEqual(fixture));
    expect(result.current.error).toBeNull();
  });

  it('given a fetch error after a successful fetch, when it occurs, then the last good data is kept and an error is set', async () => {
    client.fetchOverview.mockResolvedValueOnce(fixture);
    const { result, rerender } = renderHook(
      ({ lat, lon }) => useOverview({ lat, lon, offline: false }),
      { initialProps: { lat: 28.6139, lon: 77.209 } }
    );
    await waitFor(() => expect(result.current.data).toEqual(fixture));

    client.fetchOverview.mockRejectedValueOnce({ message: 'Network error', status: 0 });
    rerender({ lat: 10, lon: 10 });

    await waitFor(() => expect(result.current.error).not.toBeNull());
    expect(result.current.data).toEqual(fixture);
  });

  it('given the tab is hidden, when the poll interval elapses, then fetchOverview is not called again', async () => {
    vi.useFakeTimers();
    client.fetchOverview.mockResolvedValue(fixture);
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' });

    renderHook(() => useOverview({ lat: 28.6139, lon: 77.209, offline: false, pollMs: 5000 }));
    await act(async () => { await Promise.resolve(); });
    const callsAfterMount = client.fetchOverview.mock.calls.length;

    await act(async () => { vi.advanceTimersByTime(20000); });

    expect(client.fetchOverview.mock.calls.length).toBe(callsAfterMount);

    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
  });
});
