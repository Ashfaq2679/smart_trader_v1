import { apiClient } from './apiClient';
import { API_ENDPOINTS } from '../utils/constants';
import type { UserPreferences } from '../types';

export const userPreferencesApi = {
  get: (userId: string) =>
    apiClient.get<UserPreferences>(
      `${API_ENDPOINTS.USERS}/${encodeURIComponent(userId)}${API_ENDPOINTS.PREFERENCES}`,
    ),

  save: (userId: string, data: UserPreferences) =>
    apiClient.put<UserPreferences>(
      `${API_ENDPOINTS.USERS}/${encodeURIComponent(userId)}${API_ENDPOINTS.PREFERENCES}`,
      data,
    ),

  delete: (userId: string) =>
    apiClient.delete<{ message: string }>(
      `${API_ENDPOINTS.USERS}/${encodeURIComponent(userId)}${API_ENDPOINTS.PREFERENCES}`,
    ),
};
