import { apiClient } from './apiClient';
import { API_ENDPOINTS } from '../utils/constants';
import type { User, CreateUserRequest, UpdateUserRequest } from '../types';

export const usersApi = {
  create: (data: CreateUserRequest) =>
    apiClient.post<User>(API_ENDPOINTS.USERS, data),

  get: (userId: string) =>
    apiClient.get<User>(`${API_ENDPOINTS.USERS}/${encodeURIComponent(userId)}`),

  update: (userId: string, data: UpdateUserRequest) =>
    apiClient.put<User>(
      `${API_ENDPOINTS.USERS}/${encodeURIComponent(userId)}`,
      data,
    ),

  delete: (userId: string) =>
    apiClient.delete<{ message: string }>(
      `${API_ENDPOINTS.USERS}/${encodeURIComponent(userId)}`,
    ),
};
