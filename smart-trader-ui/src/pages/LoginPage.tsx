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
    <div className="d-flex min-vh-100 align-items-center justify-content-center bg-light px-3">
      <div className="w-100 login-container">
        <div className="text-center mb-4">
          <h1 className="h3 fw-bold text-primary">Smart Trader</h1>
          <p className="text-secondary">
            Sign in with your JWT token to continue
          </p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="card shadow-sm"
        >
          <div className="card-body d-flex flex-column gap-3 p-4">
            {error && (
              <Alert
                type="error"
                message={error}
                onDismiss={() => setError(null)}
              />
            )}

            <div>
              <label htmlFor="userId" className="form-label small fw-medium">
                User ID
              </label>
              <input
                id="userId"
                type="text"
                autoComplete="username"
                placeholder="Enter your user ID"
                value={userId}
                onChange={(e) => setUserId(e.target.value)}
                className="form-control"
                required
              />
            </div>

            <div>
              <label htmlFor="token" className="form-label small fw-medium">
                Access Token (JWT)
              </label>
              <textarea
                id="token"
                rows={3}
                autoComplete="off"
                placeholder="Paste your JWT access token here"
                value={token}
                onChange={(e) => setToken(e.target.value)}
                className="form-control font-monospace small"
                required
              />
            </div>

            <Button type="submit" className="w-100" isLoading={isLoading}>
              Sign In
            </Button>
          </div>
        </form>

        <p className="text-center small text-secondary mt-3">
          Credentials are transmitted securely and never stored in plain text.
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
