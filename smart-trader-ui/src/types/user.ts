export interface User {
  userId: string;
  email: string;
  displayName: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  userId: string;
  email: string;
  displayName: string;
  enabled?: boolean;
}

export interface UpdateUserRequest {
  email?: string;
  displayName?: string;
  enabled?: boolean;
}
