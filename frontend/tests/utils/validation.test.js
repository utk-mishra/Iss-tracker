import { describe, it, expect } from 'vitest';
import { validateLatitude, validateLongitude, validateOfflineFlag } from '../../src/utils/validation.js';

describe('validateLatitude', () => {
  it('given -90 exactly, when validated, then it is valid', () => {
    expect(validateLatitude('-90')).toEqual({ valid: true, value: -90 });
  });

  it('given 90 exactly, when validated, then it is valid', () => {
    expect(validateLatitude('90')).toEqual({ valid: true, value: 90 });
  });

  it('given -90.1, when validated, then it is invalid', () => {
    expect(validateLatitude('-90.1').valid).toBe(false);
  });

  it('given 90.1, when validated, then it is invalid', () => {
    expect(validateLatitude('90.1').valid).toBe(false);
  });

  it('given NaN input, when validated, then it is invalid', () => {
    expect(validateLatitude('abc').valid).toBe(false);
  });

  it('given an empty string, when validated, then it is invalid', () => {
    expect(validateLatitude('').valid).toBe(false);
  });

  it('given Infinity, when validated, then it is invalid', () => {
    expect(validateLatitude('Infinity').valid).toBe(false);
  });
});

describe('validateLongitude', () => {
  it('given -180 exactly, when validated, then it is valid', () => {
    expect(validateLongitude('-180')).toEqual({ valid: true, value: -180 });
  });

  it('given 180 exactly, when validated, then it is valid', () => {
    expect(validateLongitude('180')).toEqual({ valid: true, value: 180 });
  });

  it('given -180.1, when validated, then it is invalid', () => {
    expect(validateLongitude('-180.1').valid).toBe(false);
  });

  it('given 180.1, when validated, then it is invalid', () => {
    expect(validateLongitude('180.1').valid).toBe(false);
  });

  it('given NaN input, when validated, then it is invalid', () => {
    expect(validateLongitude('xyz').valid).toBe(false);
  });
});

describe('validateOfflineFlag', () => {
  it('given true, when validated, then it returns the string "true"', () => {
    expect(validateOfflineFlag(true)).toBe('true');
  });

  it('given false, when validated, then it returns the string "false"', () => {
    expect(validateOfflineFlag(false)).toBe('false');
  });
});
