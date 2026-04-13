import { apiClient } from './apiClient';
import { API_ENDPOINTS } from '../utils/constants';
import type { Order, OrderRequest, OrderResponse } from '../types';

export const ordersApi = {
  place: (userId: string, data: OrderRequest) =>
    apiClient.post<OrderResponse>(
      `${API_ENDPOINTS.ORDERS}/${encodeURIComponent(userId)}`,
      data,
    ),

  get: (orderId: string) =>
    apiClient.get<Order>(
      `${API_ENDPOINTS.ORDERS}/${encodeURIComponent(orderId)}`,
    ),

  listByUser: (userId: string, productId?: string) => {
    const params = productId ? { productId } : undefined;
    return apiClient.get<Order[]>(
      `${API_ENDPOINTS.ORDERS}/user/${encodeURIComponent(userId)}`,
      { params },
    );
  },

  cancel: (userId: string, orderId: string) =>
    apiClient.post<OrderResponse>(
      `${API_ENDPOINTS.ORDERS}/${encodeURIComponent(userId)}/cancel/${encodeURIComponent(orderId)}`,
    ),

  sync: (userId: string, orderId: string) =>
    apiClient.post<Order>(
      `${API_ENDPOINTS.ORDERS}/${encodeURIComponent(userId)}/sync/${encodeURIComponent(orderId)}`,
    ),
};
