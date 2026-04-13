/**
 * Format a number as USD currency.
 */
export const formatCurrency = (value: number): string =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 6,
  }).format(value);

/**
 * Format a number as a percentage with 2 decimal places.
 */
export const formatPercent = (value: number): string =>
  `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;

/**
 * Format a large number with K/M/B suffixes.
 */
export const formatCompactNumber = (value: number): string =>
  new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: 2,
  }).format(value);

/**
 * Format an ISO timestamp to a human-readable date/time string.
 */
export const formatDateTime = (isoString: string): string => {
  const date = new Date(isoString);
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
};

/**
 * Format an ISO timestamp to a relative time string (e.g., "2 hours ago").
 */
export const formatRelativeTime = (isoString: string): string => {
  const now = Date.now();
  const then = new Date(isoString).getTime();
  const diffMs = now - then;
  const diffSeconds = Math.floor(diffMs / 1000);

  if (diffSeconds < 60) return 'just now';
  if (diffSeconds < 3600)
    return `${Math.floor(diffSeconds / 60)} min${Math.floor(diffSeconds / 60) > 1 ? 's' : ''} ago`;
  if (diffSeconds < 86400)
    return `${Math.floor(diffSeconds / 3600)} hour${Math.floor(diffSeconds / 3600) > 1 ? 's' : ''} ago`;
  return `${Math.floor(diffSeconds / 86400)} day${Math.floor(diffSeconds / 86400) > 1 ? 's' : ''} ago`;
};

/**
 * Capitalize the first letter of a string.
 */
export const capitalize = (str: string): string =>
  str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();

/**
 * Get a CSS class name for an order status badge.
 */
export const getStatusColor = (
  status: string,
): string => {
  const colors: Record<string, string> = {
    PENDING: 'bg-warning-subtle text-warning-emphasis',
    OPEN: 'bg-info-subtle text-info-emphasis',
    FILLED: 'bg-success-subtle text-success-emphasis',
    CANCELLED: 'bg-secondary-subtle text-secondary-emphasis',
    FAILED: 'bg-danger-subtle text-danger-emphasis',
  };
  return colors[status] ?? 'bg-secondary-subtle text-secondary-emphasis';
};

/**
 * Get a CSS class name for a trading signal badge.
 */
export const getSignalColor = (
  signal: string,
): string => {
  const colors: Record<string, string> = {
    BUY: 'bg-success-subtle text-success-emphasis',
    SELL: 'bg-danger-subtle text-danger-emphasis',
    HOLD: 'bg-warning-subtle text-warning-emphasis',
  };
  return colors[signal] ?? 'bg-secondary-subtle text-secondary-emphasis';
};
