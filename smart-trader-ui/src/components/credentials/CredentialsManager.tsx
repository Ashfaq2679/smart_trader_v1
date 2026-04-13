import { useCallback, useEffect, useState } from 'react';
import { credentialsApi } from '../../api';
import { useAuth } from '../../hooks/useAuth';
import { Card } from '../common/Card';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { LoadingSpinner } from '../common/LoadingSpinner';

/**
 * Component for managing Coinbase API credentials.
 */
export const CredentialsManager = () => {
  const { user } = useAuth();
  const [hasCredentials, setHasCredentials] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [credentials, setCredentials] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const checkCredentials = useCallback(async () => {
    if (!user?.userId) return;
    setIsLoading(true);
    try {
      const response = await credentialsApi.exists(user.userId);
      setHasCredentials(response.data.exists);
    } catch {
      setError('Failed to check credential status');
    } finally {
      setIsLoading(false);
    }
  }, [user?.userId]);

  useEffect(() => {
    checkCredentials();
  }, [checkCredentials]);

  const handleSave = async () => {
    if (!user?.userId || !credentials.trim()) return;
    setIsSubmitting(true);
    setError(null);
    setSuccess(null);
    try {
      await credentialsApi.register(user.userId, credentials);
      setHasCredentials(true);
      setCredentials('');
      setSuccess('Credentials saved successfully');
    } catch {
      setError('Failed to save credentials');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!user?.userId) return;
    setIsSubmitting(true);
    setError(null);
    setSuccess(null);
    try {
      await credentialsApi.delete(user.userId);
      setHasCredentials(false);
      setSuccess('Credentials removed successfully');
    } catch {
      setError('Failed to remove credentials');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <LoadingSpinner message="Checking credentials..." />;
  }

  return (
    <Card title="Coinbase Credentials">
      {error && (
        <Alert type="error" message={error} onDismiss={() => setError(null)} />
      )}
      {success && (
        <Alert
          type="success"
          message={success}
          onDismiss={() => setSuccess(null)}
        />
      )}

      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <span
            className={`inline-block h-2.5 w-2.5 rounded-full ${hasCredentials ? 'bg-green-500' : 'bg-red-500'}`}
          />
          <span className="text-sm text-gray-600">
            {hasCredentials
              ? 'Credentials are configured'
              : 'No credentials configured'}
          </span>
        </div>

        <div>
          <label
            htmlFor="credentials"
            className="mb-1 block text-sm font-medium text-gray-700"
          >
            {hasCredentials ? 'Update Credentials' : 'Enter Credentials'}
          </label>
          <textarea
            id="credentials"
            rows={4}
            placeholder="Paste your Coinbase API credentials here..."
            value={credentials}
            onChange={(e) => setCredentials(e.target.value)}
            className="w-full rounded-md border border-gray-300 px-3 py-2 font-mono text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
          <p className="mt-1 text-xs text-gray-500">
            Credentials are encrypted with AES-256-GCM before storage.
          </p>
        </div>

        <div className="flex gap-2">
          <Button
            onClick={handleSave}
            isLoading={isSubmitting}
            disabled={!credentials.trim()}
          >
            {hasCredentials ? 'Update' : 'Save'} Credentials
          </Button>
          {hasCredentials && (
            <Button
              variant="danger"
              onClick={handleDelete}
              isLoading={isSubmitting}
            >
              Remove Credentials
            </Button>
          )}
        </div>
      </div>
    </Card>
  );
};
