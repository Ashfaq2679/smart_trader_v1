import { useState } from 'react';
import type { FormEvent } from 'react';
import { ordersApi } from '../../api';
import { useAuth } from '../../hooks/useAuth';
import { Card } from '../common/Card';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { validateOrderRequest, sanitizeInput } from '../../utils/validators';
import { ORDER_SIDES, ORDER_TYPES } from '../../utils/constants';
import type { OrderRequest, OrderSide, OrderType } from '../../types';

interface OrderFormProps {
  onOrderPlaced?: () => void;
}

/**
 * Form component for placing new buy/sell orders.
 */
export const OrderForm = ({ onOrderPlaced }: OrderFormProps) => {
  const { user } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [success, setSuccess] = useState<string | null>(null);

  const [formData, setFormData] = useState<OrderRequest>({
    productId: '',
    side: 'BUY',
    orderType: 'MARKET',
    baseSize: undefined,
    limitPrice: undefined,
    quoteSize: undefined,
    comments: '',
  });

  const handleChange = (
    field: keyof OrderRequest,
    value: string | number | undefined,
  ) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    setErrors([]);
    setSuccess(null);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrors([]);
    setSuccess(null);

    const sanitizedData = {
      ...formData,
      productId: sanitizeInput(formData.productId).toUpperCase(),
      comments: formData.comments ? sanitizeInput(formData.comments) : undefined,
    };

    const validationErrors = validateOrderRequest(sanitizedData);
    if (validationErrors.length > 0) {
      setErrors(validationErrors);
      return;
    }

    if (!user?.userId) {
      setErrors(['User not authenticated']);
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await ordersApi.place(user.userId, sanitizedData);
      if (response.data.success) {
        setSuccess(
          `Order placed successfully! Order ID: ${response.data.orderId}`,
        );
        setFormData({
          productId: '',
          side: 'BUY',
          orderType: 'MARKET',
          baseSize: undefined,
          limitPrice: undefined,
          quoteSize: undefined,
          comments: '',
        });
        onOrderPlaced?.();
      } else {
        setErrors([response.data.failureReason || 'Order failed']);
      }
    } catch {
      setErrors(['Failed to place order. Please try again.']);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Card title="Place Order">
      <form onSubmit={handleSubmit} className="space-y-4">
        {errors.length > 0 && (
          <Alert
            type="error"
            message={errors.join('. ')}
            onDismiss={() => setErrors([])}
          />
        )}
        {success && (
          <Alert
            type="success"
            message={success}
            onDismiss={() => setSuccess(null)}
          />
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {/* Product ID */}
          <div>
            <label
              htmlFor="productId"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Product ID
            </label>
            <input
              id="productId"
              type="text"
              placeholder="e.g., BTC-USDC"
              value={formData.productId}
              onChange={(e) => handleChange('productId', e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              required
            />
          </div>

          {/* Side */}
          <div>
            <label
              htmlFor="side"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Side
            </label>
            <select
              id="side"
              value={formData.side}
              onChange={(e) => handleChange('side', e.target.value as OrderSide)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              {ORDER_SIDES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>

          {/* Order Type */}
          <div>
            <label
              htmlFor="orderType"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Order Type
            </label>
            <select
              id="orderType"
              value={formData.orderType}
              onChange={(e) =>
                handleChange('orderType', e.target.value as OrderType)
              }
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              {ORDER_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>

          {/* Base Size */}
          <div>
            <label
              htmlFor="baseSize"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Base Size
            </label>
            <input
              id="baseSize"
              type="number"
              step="any"
              min="0"
              placeholder="Amount of base currency"
              value={formData.baseSize ?? ''}
              onChange={(e) =>
                handleChange(
                  'baseSize',
                  e.target.value ? Number(e.target.value) : undefined,
                )
              }
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          {/* Limit Price (only for LIMIT orders) */}
          {formData.orderType === 'LIMIT' && (
            <div>
              <label
                htmlFor="limitPrice"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Limit Price
              </label>
              <input
                id="limitPrice"
                type="number"
                step="any"
                min="0"
                placeholder="Price per unit"
                value={formData.limitPrice ?? ''}
                onChange={(e) =>
                  handleChange(
                    'limitPrice',
                    e.target.value ? Number(e.target.value) : undefined,
                  )
                }
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
          )}

          {/* Quote Size (only for MARKET orders) */}
          {formData.orderType === 'MARKET' && (
            <div>
              <label
                htmlFor="quoteSize"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Quote Size (USD)
              </label>
              <input
                id="quoteSize"
                type="number"
                step="any"
                min="0"
                placeholder="Amount in quote currency"
                value={formData.quoteSize ?? ''}
                onChange={(e) =>
                  handleChange(
                    'quoteSize',
                    e.target.value ? Number(e.target.value) : undefined,
                  )
                }
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
          )}
        </div>

        {/* Comments */}
        <div>
          <label
            htmlFor="comments"
            className="mb-1 block text-sm font-medium text-gray-700"
          >
            Comments (optional)
          </label>
          <textarea
            id="comments"
            rows={2}
            placeholder="Add notes about this order..."
            value={formData.comments ?? ''}
            onChange={(e) => handleChange('comments', e.target.value)}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        <div className="flex justify-end">
          <Button
            type="submit"
            variant={formData.side === 'BUY' ? 'primary' : 'danger'}
            isLoading={isSubmitting}
          >
            Place {formData.side} Order
          </Button>
        </div>
      </form>
    </Card>
  );
};
