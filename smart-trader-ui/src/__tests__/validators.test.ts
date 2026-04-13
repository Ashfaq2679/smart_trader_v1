import { describe, it, expect } from 'vitest';
import {
  isValidEmail,
  isPositiveNumber,
  isNotBlank,
  isValidProductId,
  sanitizeInput,
  validateOrderRequest,
} from '../utils/validators';

describe('isValidEmail', () => {
  it('returns true for valid emails', () => {
    expect(isValidEmail('user@example.com')).toBe(true);
    expect(isValidEmail('test.user@domain.co')).toBe(true);
  });

  it('returns false for invalid emails', () => {
    expect(isValidEmail('')).toBe(false);
    expect(isValidEmail('notanemail')).toBe(false);
    expect(isValidEmail('@domain.com')).toBe(false);
    expect(isValidEmail('user@')).toBe(false);
  });
});

describe('isPositiveNumber', () => {
  it('returns true for positive numbers', () => {
    expect(isPositiveNumber(1)).toBe(true);
    expect(isPositiveNumber(0.001)).toBe(true);
  });

  it('returns false for zero, negative, null, undefined', () => {
    expect(isPositiveNumber(0)).toBe(false);
    expect(isPositiveNumber(-1)).toBe(false);
    expect(isPositiveNumber(null)).toBe(false);
    expect(isPositiveNumber(undefined)).toBe(false);
    expect(isPositiveNumber(NaN)).toBe(false);
  });
});

describe('isNotBlank', () => {
  it('returns true for non-empty strings', () => {
    expect(isNotBlank('hello')).toBe(true);
  });

  it('returns false for blank strings, null, undefined', () => {
    expect(isNotBlank('')).toBe(false);
    expect(isNotBlank('   ')).toBe(false);
    expect(isNotBlank(null)).toBe(false);
    expect(isNotBlank(undefined)).toBe(false);
  });
});

describe('isValidProductId', () => {
  it('returns true for valid product IDs', () => {
    expect(isValidProductId('BTC-USDC')).toBe(true);
    expect(isValidProductId('ETH-USD')).toBe(true);
    expect(isValidProductId('SHIB-USDC')).toBe(true);
  });

  it('returns false for invalid product IDs', () => {
    expect(isValidProductId('')).toBe(false);
    expect(isValidProductId('BTC')).toBe(false);
    expect(isValidProductId('btc-usdc')).toBe(false);
    expect(isValidProductId('BTC_USDC')).toBe(false);
  });
});

describe('sanitizeInput', () => {
  it('removes HTML tags', () => {
    expect(sanitizeInput('<script>alert("xss")</script>')).toBe(
      'alert("xss")',
    );
  });

  it('trims whitespace', () => {
    expect(sanitizeInput('  hello  ')).toBe('hello');
  });

  it('preserves normal text', () => {
    expect(sanitizeInput('BTC-USDC')).toBe('BTC-USDC');
  });
});

describe('validateOrderRequest', () => {
  it('returns no errors for valid market buy with quoteSize', () => {
    const errors = validateOrderRequest({
      productId: 'BTC-USDC',
      side: 'BUY',
      orderType: 'MARKET',
      quoteSize: 100,
    });
    expect(errors).toHaveLength(0);
  });

  it('returns no errors for valid limit buy', () => {
    const errors = validateOrderRequest({
      productId: 'ETH-USDC',
      side: 'BUY',
      orderType: 'LIMIT',
      baseSize: 0.5,
      limitPrice: 3000,
    });
    expect(errors).toHaveLength(0);
  });

  it('returns errors for empty product ID', () => {
    const errors = validateOrderRequest({
      productId: '',
      side: 'BUY',
      orderType: 'MARKET',
      quoteSize: 100,
    });
    expect(errors).toContain('Product ID is required');
  });

  it('returns errors for invalid product ID format', () => {
    const errors = validateOrderRequest({
      productId: 'invalid',
      side: 'BUY',
      orderType: 'MARKET',
      quoteSize: 100,
    });
    expect(errors).toContain('Product ID must be in format like BTC-USDC');
  });

  it('returns errors for invalid side', () => {
    const errors = validateOrderRequest({
      productId: 'BTC-USDC',
      side: 'INVALID',
      orderType: 'MARKET',
      quoteSize: 100,
    });
    expect(errors).toContain('Side must be BUY or SELL');
  });

  it('returns errors for limit order without baseSize', () => {
    const errors = validateOrderRequest({
      productId: 'BTC-USDC',
      side: 'BUY',
      orderType: 'LIMIT',
      limitPrice: 50000,
    });
    expect(errors).toContain(
      'Base size is required and must be positive for limit orders',
    );
  });

  it('returns errors for limit order without limitPrice', () => {
    const errors = validateOrderRequest({
      productId: 'BTC-USDC',
      side: 'BUY',
      orderType: 'LIMIT',
      baseSize: 1,
    });
    expect(errors).toContain(
      'Limit price is required and must be positive for limit orders',
    );
  });

  it('returns errors for market order without baseSize or quoteSize', () => {
    const errors = validateOrderRequest({
      productId: 'BTC-USDC',
      side: 'BUY',
      orderType: 'MARKET',
    });
    expect(errors).toContain(
      'Either base size or quote size must be provided for market orders',
    );
  });
});
