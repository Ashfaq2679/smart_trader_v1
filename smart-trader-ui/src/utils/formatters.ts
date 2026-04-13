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
    PENDING: 'bg-yellow-100 text-yellow-800',
    OPEN: 'bg-blue-100 text-blue-800',
    FILLED: 'bg-green-100 text-green-800',
    CANCELLED: 'bg-gray-100 text-gray-800',
    FAILED: 'bg-red-100 text-red-800',
  };
  return colors[status] ?? 'bg-gray-100 text-gray-800';
};

/**
 * Get a CSS class name for a trading signal badge.
 */
export const getSignalColor = (
  signal: string,
): string => {
  const colors: Record<string, string> = {
    BUY: 'bg-green-100 text-green-800',
    SELL: 'bg-red-100 text-red-800',
    HOLD: 'bg-yellow-100 text-yellow-800',
  };
  return colors[signal] ?? 'bg-gray-100 text-gray-800';
};
