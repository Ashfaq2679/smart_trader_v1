import { apiClient } from './apiClient';
import { API_ENDPOINTS } from '../utils/constants';
import type { CoinScanResult, TradeDecision } from '../types';

export const scannerApi = {
  scan: (userId: string, limit = 10) =>
    apiClient.post<CoinScanResult[]>(
      `${API_ENDPOINTS.SCANNER}/scan`,
      null,
      { params: { userId, limit } },
    ),

  getResults: () =>
    apiClient.get<CoinScanResult[]>(`${API_ENDPOINTS.SCANNER}/results`),

  analyze: (productId: string, userId: string) =>
    apiClient.get<TradeDecision>(
      `${API_ENDPOINTS.SCANNER}/analyze/${encodeURIComponent(productId)}`,
      { params: { userId } },
    ),
};
