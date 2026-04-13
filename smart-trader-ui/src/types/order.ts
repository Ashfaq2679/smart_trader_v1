export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'MARKET' | 'LIMIT';
export type OrderStatus = 'PENDING' | 'OPEN' | 'FILLED' | 'CANCELLED' | 'FAILED';

export interface Order {
  id: string;
  userId: string;
  coinbaseOrderId: string;
  clientOrderId: string;
  productId: string;
  side: OrderSide;
  orderType: OrderType;
  qty: number;
  limitPrice: number | null;
  quoteSize: number | null;
  status: OrderStatus;
  filledSize: string;
  averageFilledPrice: string;
  totalFees: string;
  failureReason: string;
  decisionFactors: Record<string, string>;
  comments: string;
  createdAt: string;
  updatedAt: string;
}

export interface OrderRequest {
  productId: string;
  side: OrderSide;
  orderType: OrderType;
  baseSize?: number;
  limitPrice?: number;
  quoteSize?: number;
  decisionFactors?: Record<string, string>;
  comments?: string;
}

export interface OrderResponse {
  success: boolean;
  orderId: string;
  coinbaseOrderId: string;
  productId: string;
  side: OrderSide;
  orderType: OrderType;
  status: OrderStatus;
  failureReason: string;
  message: string;
}
