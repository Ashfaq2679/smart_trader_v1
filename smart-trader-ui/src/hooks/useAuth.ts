import { useContext } from 'react';
import { AuthContext } from '../context/AuthContext';
import type { AuthState } from '../context/AuthContext';

/**
 * Hook to access authentication state and actions.
 */
export const useAuth = (): AuthState => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
