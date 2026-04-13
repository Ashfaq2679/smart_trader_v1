import { useCallback, useEffect, useState } from 'react';
import { ordersApi } from '../../api';
import { useAuth } from '../../hooks/useAuth';
import { Card } from '../common/Card';
import { Badge } from '../common/Badge';
import { Button } from '../common/Button';
import { LoadingSpinner } from '../common/LoadingSpinner';
import { Alert } from '../common/Alert';
import { formatCurrency, formatDateTime, getStatusColor } from '../../utils/formatters';
import type { Order } from '../../types';

interface OrderListProps {
  onRefresh?: () => void;
}

/**
 * Component for displaying and managing user orders.
 */
export const OrderList = ({ onRefresh }: OrderListProps) => {
  const { user } = useAuth();
  const [orders, setOrders] = useState<Order[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);

  const fetchOrders = useCallback(async () => {
    if (!user?.userId) return;
    setIsLoading(true);
    setError(null);
    try {
      const response = await ordersApi.listByUser(user.userId);
      setOrders(response.data);
    } catch {
      setError('Failed to load orders');
    } finally {
      setIsLoading(false);
    }
  }, [user?.userId]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  const handleCancel = async (orderId: string) => {
    if (!user?.userId) return;
    setCancellingId(orderId);
    try {
      await ordersApi.cancel(user.userId, orderId);
      await fetchOrders();
      onRefresh?.();
    } catch {
      setError('Failed to cancel order');
    } finally {
      setCancellingId(null);
    }
  };

  const handleSync = async (orderId: string) => {
    if (!user?.userId) return;
    try {
      await ordersApi.sync(user.userId, orderId);
      await fetchOrders();
    } catch {
      setError('Failed to sync order');
    }
  };

  if (isLoading) {
    return <LoadingSpinner message="Loading orders..." />;
  }

  return (
    <Card
      title="Orders"
      actions={
        <Button variant="secondary" size="sm" onClick={fetchOrders}>
          Refresh
        </Button>
      }
    >
      {error && (
        <Alert type="error" message={error} onDismiss={() => setError(null)} />
      )}

      {orders.length === 0 ? (
        <p className="py-8 text-center text-gray-500">
          No orders found. Place your first order to get started.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-gray-200 text-xs uppercase text-gray-500">
              <tr>
                <th className="px-4 py-3">Product</th>
                <th className="px-4 py-3">Side</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Qty</th>
                <th className="px-4 py-3">Price</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Created</th>
                <th className="px-4 py-3">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {orders.map((order) => (
                <tr key={order.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-900">
                    {order.productId}
                  </td>
                  <td className="px-4 py-3">
                    <Badge
                      label={order.side}
                      className={
                        order.side === 'BUY'
                          ? 'bg-green-100 text-green-800'
                          : 'bg-red-100 text-red-800'
                      }
                    />
                  </td>
                  <td className="px-4 py-3 text-gray-600">{order.orderType}</td>
                  <td className="px-4 py-3">{order.qty}</td>
                  <td className="px-4 py-3">
                    {order.limitPrice != null
                      ? formatCurrency(order.limitPrice)
                      : '—'}
                  </td>
                  <td className="px-4 py-3">
                    <Badge
                      label={order.status}
                      className={getStatusColor(order.status)}
                    />
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">
                    {formatDateTime(order.createdAt)}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-1">
                      {(order.status === 'PENDING' || order.status === 'OPEN') && (
                        <Button
                          variant="danger"
                          size="sm"
                          isLoading={cancellingId === order.id}
                          onClick={() => handleCancel(order.id)}
                        >
                          Cancel
                        </Button>
                      )}
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleSync(order.id)}
                      >
                        Sync
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
};
