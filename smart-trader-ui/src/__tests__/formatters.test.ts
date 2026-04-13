import { describe, it, expect } from 'vitest';
import {
  formatCurrency,
  formatPercent,
  formatCompactNumber,
  formatDateTime,
  capitalize,
  getStatusColor,
  getSignalColor,
} from '../utils/formatters';

describe('formatCurrency', () => {
  it('formats positive numbers as USD', () => {
    expect(formatCurrency(1234.56)).toBe('$1,234.56');
  });

  it('formats zero', () => {
    expect(formatCurrency(0)).toBe('$0.00');
  });

  it('formats small decimals', () => {
    const result = formatCurrency(0.000123);
    expect(result).toContain('$');
    expect(result).toContain('0.000123');
  });
});

describe('formatPercent', () => {
  it('formats positive percentages with + sign', () => {
    expect(formatPercent(5.5)).toBe('+5.50%');
  });

  it('formats negative percentages', () => {
    expect(formatPercent(-3.14)).toBe('-3.14%');
  });

  it('formats zero', () => {
    expect(formatPercent(0)).toBe('+0.00%');
  });
});

describe('formatCompactNumber', () => {
  it('formats thousands', () => {
    const result = formatCompactNumber(1500);
    expect(result).toContain('1.5');
  });

  it('formats millions', () => {
    const result = formatCompactNumber(2500000);
    expect(result).toContain('2.5');
  });

  it('formats small numbers as-is', () => {
    expect(formatCompactNumber(42)).toBe('42');
  });
});

describe('formatDateTime', () => {
  it('formats an ISO string to readable date', () => {
    const result = formatDateTime('2024-01-15T10:30:00Z');
    expect(result).toBeTruthy();
    expect(typeof result).toBe('string');
  });
});

describe('capitalize', () => {
  it('capitalizes the first letter', () => {
    expect(capitalize('hello')).toBe('Hello');
  });

  it('lowercases the rest', () => {
    expect(capitalize('HELLO')).toBe('Hello');
  });

  it('handles single character', () => {
    expect(capitalize('a')).toBe('A');
  });
});

describe('getStatusColor', () => {
  it('returns correct class for FILLED', () => {
    expect(getStatusColor('FILLED')).toContain('success');
  });

  it('returns correct class for FAILED', () => {
    expect(getStatusColor('FAILED')).toContain('danger');
  });

  it('returns default class for unknown status', () => {
    expect(getStatusColor('UNKNOWN')).toContain('secondary');
  });
});

describe('getSignalColor', () => {
  it('returns success for BUY', () => {
    expect(getSignalColor('BUY')).toContain('success');
  });

  it('returns danger for SELL', () => {
    expect(getSignalColor('SELL')).toContain('danger');
  });

  it('returns warning for HOLD', () => {
    expect(getSignalColor('HOLD')).toContain('warning');
  });

  it('returns default for unknown', () => {
    expect(getSignalColor('UNKNOWN')).toContain('secondary');
  });
});
