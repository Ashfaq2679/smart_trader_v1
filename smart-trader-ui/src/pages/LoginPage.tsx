import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { Button } from '../components/common/Button';
import { Alert } from '../components/common/Alert';
import { sanitizeInput } from '../utils/validators';

/**
 * Login page for JWT token authentication.
 */
const LoginPage = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [token, setToken] = useState('');
  const [userId, setUserId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    const sanitizedUserId = sanitizeInput(userId);
    const trimmedToken = token.trim();

    if (!sanitizedUserId || !trimmedToken) {
      setError('Both User ID and Access Token are required');
      return;
    }

    setIsLoading(true);
    try {
      await login(trimmedToken, sanitizedUserId);
      navigate('/', { replace: true });
    } catch {
      setError('Login failed. Please check your credentials.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md space-y-8">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-blue-600">Smart Trader</h1>
          <p className="mt-2 text-gray-500">
            Sign in with your JWT token to continue
          </p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="space-y-6 rounded-lg border border-gray-200 bg-white p-8 shadow-sm"
        >
          {error && (
            <Alert
              type="error"
              message={error}
              onDismiss={() => setError(null)}
            />
          )}

          <div>
            <label
              htmlFor="userId"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              User ID
            </label>
            <input
              id="userId"
              type="text"
              autoComplete="username"
              placeholder="Enter your user ID"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              required
            />
          </div>

          <div>
            <label
              htmlFor="token"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Access Token (JWT)
            </label>
            <textarea
              id="token"
              rows={3}
              autoComplete="off"
              placeholder="Paste your JWT access token here"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 font-mono text-xs focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              required
            />
          </div>

          <Button type="submit" className="w-full" isLoading={isLoading}>
            Sign In
          </Button>
        </form>

        <p className="text-center text-xs text-gray-400">
          Credentials are transmitted securely and never stored in plain text.
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
