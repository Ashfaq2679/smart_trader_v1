import { useCallback, useState } from 'react';
import type { AxiosError } from 'axios';

interface ApiState<T> {
  data: T | null;
  error: string | null;
  isLoading: boolean;
}

interface UseApiReturn<T> extends ApiState<T> {
  execute: (...args: unknown[]) => Promise<T | null>;
  reset: () => void;
}

/**
 * Generic hook for API calls with loading/error state management.
 */
export const useApi = <T>(
  apiFn: (...args: never[]) => Promise<{ data: T }>,
): UseApiReturn<T> => {
  const [state, setState] = useState<ApiState<T>>({
    data: null,
    error: null,
    isLoading: false,
  });

  const execute = useCallback(
    async (...args: unknown[]): Promise<T | null> => {
      setState({ data: null, error: null, isLoading: true });
      try {
        const response = await (apiFn as (...a: unknown[]) => Promise<{ data: T }>)(...args);
        setState({ data: response.data, error: null, isLoading: false });
        return response.data;
      } catch (err) {
        const axiosError = err as AxiosError<{ message?: string }>;
        const message =
          axiosError.response?.data?.message ??
          axiosError.message ??
          'An unexpected error occurred';
        setState({ data: null, error: message, isLoading: false });
        return null;
      }
    },
    [apiFn],
  );

  const reset = useCallback(() => {
    setState({ data: null, error: null, isLoading: false });
  }, []);

  return { ...state, execute, reset };
};
