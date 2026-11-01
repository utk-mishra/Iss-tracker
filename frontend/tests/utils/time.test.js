import { describe, it, expect } from 'vitest';
import { formatLocalTime, formatUtcTooltip, minutesAgo } from '../../src/utils/time.js';

describe('formatLocalTime', () => {
  it('given a valid ISO instant, when formatted, then a non-empty local time string is returned', () => {
    const result = formatLocalTime('2026-11-01T16:22:18.143805Z');
    expect(typeof result).toBe('string');
    expect(result.length).toBeGreaterThan(0);
  });

  it('given null, when formatted, then a placeholder is returned instead of crashing', () => {
    expect(() => formatLocalTime(null)).not.toThrow();
    expect(formatLocalTime(null)).toBe('\u2014');
  });

  it('given undefined, when formatted, then a placeholder is returned instead of crashing', () => {
    expect(formatLocalTime(undefined)).toBe('\u2014');
  });
});

describe('formatUtcTooltip', () => {
  it('given a valid ISO instant, when formatted, then it contains "UTC"', () => {
    const result = formatUtcTooltip('2026-11-01T16:22:18.143805Z');
    expect(result).toContain('UTC');
  });

  it('given null, when formatted, then an empty string is returned instead of crashing', () => {
    expect(formatUtcTooltip(null)).toBe('');
  });
});

describe('minutesAgo', () => {
  it('given 0 milliseconds, when converted, then it returns 0', () => {
    expect(minutesAgo(0)).toBe(0);
  });

  it('given 90000 milliseconds (1.5 minutes), when converted, then it rounds down to 1', () => {
    expect(minutesAgo(90000)).toBe(1);
  });

  it('given null, when converted, then it returns 0 instead of crashing', () => {
    expect(minutesAgo(null)).toBe(0);
  });
});
