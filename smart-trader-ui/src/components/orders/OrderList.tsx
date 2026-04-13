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
        <p className="py-4 text-center text-secondary">
          No orders found. Place your first order to get started.
        </p>
      ) : (
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th className="small text-uppercase text-secondary">Product</th>
                <th className="small text-uppercase text-secondary">Side</th>
                <th className="small text-uppercase text-secondary">Type</th>
                <th className="small text-uppercase text-secondary">Qty</th>
                <th className="small text-uppercase text-secondary">Price</th>
                <th className="small text-uppercase text-secondary">Status</th>
                <th className="small text-uppercase text-secondary">Created</th>
                <th className="small text-uppercase text-secondary">Actions</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id}>
                  <td className="fw-medium">
                    {order.productId}
                  </td>
                  <td>
                    <Badge
                      label={order.side}
                      className={
                        order.side === 'BUY'
                          ? 'bg-success-subtle text-success-emphasis'
                          : 'bg-danger-subtle text-danger-emphasis'
                      }
                    />
                  </td>
                  <td className="text-secondary">{order.orderType}</td>
                  <td>{order.qty}</td>
                  <td>
                    {order.limitPrice != null
                      ? formatCurrency(order.limitPrice)
                      : '—'}
                  </td>
                  <td>
                    <Badge
                      label={order.status}
                      className={getStatusColor(order.status)}
                    />
                  </td>
                  <td>
                    <small className="text-secondary">
                      {formatDateTime(order.createdAt)}
                    </small>
                  </td>
                  <td>
                    <div className="d-flex gap-1">
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
