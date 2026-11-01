import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import useOfflineToggle from '../../src/hooks/useOfflineToggle.js';

describe('useOfflineToggle', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('given nothing stored, when initialised, then it defaults to false', () => {
    const { result } = renderHook(() => useOfflineToggle());

    expect(result.current.offline).toBe(false);
  });

  it('given the toggle is set to true, when set, then it is persisted to localStorage', () => {
    const { result } = renderHook(() => useOfflineToggle());

    act(() => result.current.setOffline(true));

    expect(result.current.offline).toBe(true);
    expect(window.localStorage.getItem('iss-tracker:offline')).toBe('true');
  });

  it('given a previous session persisted offline=true, when re-initialised, then it restores true', () => {
    window.localStorage.setItem('iss-tracker:offline', 'true');

    const { result } = renderHook(() => useOfflineToggle());

    expect(result.current.offline).toBe(true);
  });
});
