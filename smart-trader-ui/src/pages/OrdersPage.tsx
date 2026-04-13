import { useState } from 'react';
import { OrderForm } from '../components/orders/OrderForm';
import { OrderList } from '../components/orders/OrderList';

/**
 * Orders page — place new orders and view order history.
 */
const OrdersPage = () => {
  const [refreshKey, setRefreshKey] = useState(0);

  return (
    <div className="d-flex flex-column gap-4">
      <h1 className="h4 fw-bold">Orders</h1>
      <OrderForm onOrderPlaced={() => setRefreshKey((k) => k + 1)} />
      <OrderList key={refreshKey} />
    </div>
  );
};

export default OrdersPage;
