/**
 * Validate that a string is a valid email address.
 */
export const isValidEmail = (email: string): boolean => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
};

/**
 * Validate that a number is positive.
 */
export const isPositiveNumber = (value: number | undefined | null): boolean =>
  value != null && !isNaN(value) && value > 0;

/**
 * Validate that a string is not empty after trimming.
 */
export const isNotBlank = (value: string | undefined | null): boolean =>
  value != null && value.trim().length > 0;

/**
 * Validate a product ID format (e.g., "BTC-USDC").
 */
export const isValidProductId = (productId: string): boolean => {
  const productIdRegex = /^[A-Z0-9]+-[A-Z0-9]+$/;
  return productIdRegex.test(productId);
};

/**
 * Sanitize a string by removing HTML tags to prevent XSS.
 * Uses iterative replacement to handle nested/broken tags.
 */
export const sanitizeInput = (input: string): string => {
  let result = input;
  let previous: string;
  do {
    previous = result;
    result = result.replace(/<[^>]*>/g, '');
  } while (result !== previous);
  return result.trim();
};

/**
 * Validate an order request for completeness.
 */
export const validateOrderRequest = (
  order: {
    productId: string;
    side: string;
    orderType: string;
    baseSize?: number;
    limitPrice?: number;
    quoteSize?: number;
  },
): string[] => {
  const errors: string[] = [];

  if (!isNotBlank(order.productId)) {
    errors.push('Product ID is required');
  } else if (!isValidProductId(order.productId)) {
    errors.push('Product ID must be in format like BTC-USDC');
  }

  if (!['BUY', 'SELL'].includes(order.side)) {
    errors.push('Side must be BUY or SELL');
  }

  if (!['MARKET', 'LIMIT'].includes(order.orderType)) {
    errors.push('Order type must be MARKET or LIMIT');
  }

  if (order.orderType === 'LIMIT') {
    if (!isPositiveNumber(order.baseSize)) {
      errors.push('Base size is required and must be positive for limit orders');
    }
    if (!isPositiveNumber(order.limitPrice)) {
      errors.push('Limit price is required and must be positive for limit orders');
    }
  }

  if (
    order.orderType === 'MARKET' &&
    !isPositiveNumber(order.baseSize) &&
    !isPositiveNumber(order.quoteSize)
  ) {
    errors.push(
      'Either base size or quote size must be provided for market orders',
    );
  }

  return errors;
};
