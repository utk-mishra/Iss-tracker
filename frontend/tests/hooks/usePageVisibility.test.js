import { describe, it, expect, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import usePageVisibility from '../../src/hooks/usePageVisibility.js';

function setVisibility(state) {
  Object.defineProperty(document, 'visibilityState', { configurable: true, value: state });
  document.dispatchEvent(new Event('visibilitychange'));
}

describe('usePageVisibility', () => {
  afterEach(() => {
    act(() => setVisibility('visible'));
  });

  it('given the document is visible, when the hook reports, then it returns true', () => {
    setVisibility('visible');
    const { result } = renderHook(() => usePageVisibility());

    expect(result.current).toBe(true);
  });

  it('given the tab becomes hidden, when visibilitychange fires, then the hook returns false', () => {
    setVisibility('visible');
    const { result } = renderHook(() => usePageVisibility());

    act(() => setVisibility('hidden'));

    expect(result.current).toBe(false);
  });

  it('given the tab becomes visible again, when visibilitychange fires, then the hook returns true', () => {
    act(() => setVisibility('hidden'));
    const { result } = renderHook(() => usePageVisibility());

    act(() => setVisibility('visible'));

    expect(result.current).toBe(true);
  });
});
