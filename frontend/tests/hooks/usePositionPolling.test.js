import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import usePositionPolling from '../../src/hooks/usePositionPolling.js';
import * as client from '../../src/api/client.js';

vi.mock('../../src/api/client.js', async () => {
  const actual = await vi.importActual('../../src/api/client.js');
  return { ...actual, fetchPosition: vi.fn() };
});

describe('usePositionPolling', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('given a successful fetch, when resolved, then position data is exposed', async () => {
    const positionResult = { position: { latitude: 1, longitude: 2 }, meta: { source: 'FRESH', degraded: false, ageMs: 0 } };
    client.fetchPosition.mockResolvedValue(positionResult);

    const { result } = renderHook(() => usePositionPolling({ offline: false }));

    await waitFor(() => expect(result.current.data).toEqual(positionResult));
  });

  it('given the tab is hidden, when the poll interval elapses, then fetchPosition is not called again', async () => {
    vi.useFakeTimers();
    client.fetchPosition.mockResolvedValue({ position: {}, meta: {} });
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' });

    renderHook(() => usePositionPolling({ offline: false, pollMs: 5000 }));
    await act(async () => { await Promise.resolve(); });
    const callsAfterMount = client.fetchPosition.mock.calls.length;

    await act(async () => { vi.advanceTimersByTime(30000); });

    expect(client.fetchPosition.mock.calls.length).toBe(callsAfterMount);
  });
});
