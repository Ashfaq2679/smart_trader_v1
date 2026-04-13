export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const API_ENDPOINTS = {
  USERS: '/api/users',
  CREDENTIALS: '/api/credentials',
  ORDERS: '/api/orders',
  SCANNER: '/api/scanner',
  PREFERENCES: '/preferences',
  HEALTH: '/actuator/health',
} as const;

export const ORDER_SIDES = ['BUY', 'SELL'] as const;
export const ORDER_TYPES = ['MARKET', 'LIMIT'] as const;
export const ORDER_STATUSES = [
  'PENDING',
  'OPEN',
  'FILLED',
  'CANCELLED',
  'FAILED',
] as const;

export const SIGNALS = ['BUY', 'SELL', 'HOLD'] as const;

export const STRATEGIES = ['PRICE_ACTION'] as const;

export const GRANULARITIES = [
  'ONE_MINUTE',
  'FIVE_MINUTE',
  'FIFTEEN_MINUTE',
  'THIRTY_MINUTE',
  'ONE_HOUR',
  'TWO_HOUR',
  'SIX_HOUR',
  'ONE_DAY',
] as const;

export const TIMEZONES = [
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Berlin',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'UTC',
] as const;
