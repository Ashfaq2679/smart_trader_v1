import { useState } from 'react';
import { OrderForm } from '../components/orders/OrderForm';
import { OrderList } from '../components/orders/OrderList';

/**
 * Orders page — place new orders and view order history.
 */
const OrdersPage = () => {
  const [refreshKey, setRefreshKey] = useState(0);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Orders</h1>
      <OrderForm onOrderPlaced={() => setRefreshKey((k) => k + 1)} />
      <OrderList key={refreshKey} />
    </div>
  );
};

export default OrdersPage;
