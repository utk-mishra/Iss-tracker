import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { safeGetItem, safeSetItem } from '../../src/utils/storage.js';

describe('safeGetItem', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('given a stored value, when read, then it is returned', () => {
    window.localStorage.setItem('key', 'value');
    expect(safeGetItem('key')).toBe('value');
  });

  it('given no stored value, when read, then null is returned', () => {
    expect(safeGetItem('missing')).toBeNull();
  });

  it('given localStorage.getItem throws, when read, then null is returned instead of crashing', () => {
    const spy = vi.spyOn(window.localStorage.__proto__, 'getItem').mockImplementation(() => {
      throw new Error('disabled storage');
    });

    expect(() => safeGetItem('key')).not.toThrow();
    expect(safeGetItem('key')).toBeNull();

    spy.mockRestore();
  });
});

describe('safeSetItem', () => {
  afterEach(() => {
    window.localStorage.clear();
  });

  it('given a value, when set, then it is stored', () => {
    safeSetItem('key', 'value');
    expect(window.localStorage.getItem('key')).toBe('value');
  });

  it('given localStorage.setItem throws, when set, then it does not crash', () => {
    const spy = vi.spyOn(window.localStorage.__proto__, 'setItem').mockImplementation(() => {
      throw new Error('quota exceeded');
    });

    expect(() => safeSetItem('key', 'value')).not.toThrow();

    spy.mockRestore();
  });
});
