import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { User } from '../types';
import { usersApi } from '../api';
import { AuthContext } from './AuthContext';
import type { AuthState } from './AuthContext';

interface AuthProviderProps {
  children: ReactNode;
}

/**
 * Reads the stored token from sessionStorage at module load time
 * to initialize state synchronously and avoid cascading renders.
 */
const getInitialToken = (): string | null =>
  sessionStorage.getItem('access_token');
const getInitialUserId = (): string | null =>
  sessionStorage.getItem('user_id');

export const AuthProvider = ({ children }: AuthProviderProps) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(getInitialToken);
  const [isLoading, setIsLoading] = useState(!!getInitialToken());

  const logout = useCallback(() => {
    sessionStorage.removeItem('access_token');
    sessionStorage.removeItem('user_id');
    setToken(null);
    setUser(null);
  }, []);

  const login = useCallback(async (newToken: string, userId: string) => {
    sessionStorage.setItem('access_token', newToken);
    sessionStorage.setItem('user_id', userId);
    setToken(newToken);
    try {
      const response = await usersApi.get(userId);
      setUser(response.data);
    } catch {
      setUser({
        userId,
        email: '',
        displayName: userId,
        enabled: true,
        createdAt: '',
        updatedAt: '',
      });
    }
  }, []);

  // Fetch user profile on mount when token is present
  useEffect(() => {
    const storedUserId = getInitialUserId();
    if (!token || !storedUserId) {
      setIsLoading(false);
      return;
    }

    usersApi
      .get(storedUserId)
      .then((response) => setUser(response.data))
      .catch(() => {
        setUser({
          userId: storedUserId,
          email: '',
          displayName: storedUserId,
          enabled: true,
          createdAt: '',
          updatedAt: '',
        });
      })
      .finally(() => setIsLoading(false));
    // Only run on mount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Listen for unauthorized events from API interceptor
  useEffect(() => {
    const handler = () => logout();
    window.addEventListener('auth:unauthorized', handler);
    return () => window.removeEventListener('auth:unauthorized', handler);
  }, [logout]);

  const value = useMemo<AuthState>(
    () => ({
      isAuthenticated: !!token,
      isLoading,
      user,
      token,
      login,
      logout,
    }),
    [token, isLoading, user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
