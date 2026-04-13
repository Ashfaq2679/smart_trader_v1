import { apiClient } from './apiClient';
import { API_ENDPOINTS } from '../utils/constants';

export const credentialsApi = {
  register: (userId: string, credentials: string) =>
    apiClient.post<{ message: string }>(
      `${API_ENDPOINTS.CREDENTIALS}/${encodeURIComponent(userId)}`,
      { credentials },
    ),

  exists: (userId: string) =>
    apiClient.get<{ exists: boolean }>(
      `${API_ENDPOINTS.CREDENTIALS}/${encodeURIComponent(userId)}/exists`,
    ),

  delete: (userId: string) =>
    apiClient.delete<{ message: string }>(
      `${API_ENDPOINTS.CREDENTIALS}/${encodeURIComponent(userId)}`,
    ),
};
