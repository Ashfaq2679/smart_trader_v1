import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { userPreferencesApi } from '../../api';
import { useAuth } from '../../hooks/useAuth';
import { Card } from '../common/Card';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { LoadingSpinner } from '../common/LoadingSpinner';
import { STRATEGIES, GRANULARITIES, TIMEZONES } from '../../utils/constants';
import type { UserPreferences } from '../../types';

const defaultPreferences: UserPreferences = {
  userId: '',
  strategy: 'PRICE_ACTION',
  granularity: 'ONE_HOUR',
  baseAsset: 'BTC',
  quoteAsset: 'USDC',
  positionSize: '100',
  maxDailyLoss: '50',
  timezone: 'UTC',
  enabled: true,
};

/**
 * Component for managing user trading preferences.
 */
export const PreferencesForm = () => {
  const { user } = useAuth();
  const [preferences, setPreferences] =
    useState<UserPreferences>(defaultPreferences);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const fetchPreferences = useCallback(async () => {
    if (!user?.userId) return;
    setIsLoading(true);
    try {
      const response = await userPreferencesApi.get(user.userId);
      setPreferences(response.data);
    } catch {
      setPreferences({ ...defaultPreferences, userId: user.userId });
    } finally {
      setIsLoading(false);
    }
  }, [user?.userId]);

  useEffect(() => {
    fetchPreferences();
  }, [fetchPreferences]);

  const handleChange = (
    field: keyof UserPreferences,
    value: string | boolean,
  ) => {
    setPreferences((prev) => ({ ...prev, [field]: value }));
    setError(null);
    setSuccess(null);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!user?.userId) return;
    setIsSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const dataToSave = { ...preferences, userId: user.userId };
      await userPreferencesApi.save(user.userId, dataToSave);
      setSuccess('Preferences saved successfully');
    } catch {
      setError('Failed to save preferences');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <LoadingSpinner message="Loading preferences..." />;
  }

  return (
    <Card title="Trading Preferences">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <Alert
            type="error"
            message={error}
            onDismiss={() => setError(null)}
          />
        )}
        {success && (
          <Alert
            type="success"
            message={success}
            onDismiss={() => setSuccess(null)}
          />
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label
              htmlFor="strategy"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Strategy
            </label>
            <select
              id="strategy"
              value={preferences.strategy}
              onChange={(e) => handleChange('strategy', e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              {STRATEGIES.map((s) => (
                <option key={s} value={s}>
                  {s.replace('_', ' ')}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label
              htmlFor="granularity"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Candle Granularity
            </label>
            <select
              id="granularity"
              value={preferences.granularity}
              onChange={(e) => handleChange('granularity', e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              {GRANULARITIES.map((g) => (
                <option key={g} value={g}>
                  {g.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label
              htmlFor="baseAsset"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Base Asset
            </label>
            <input
              id="baseAsset"
              type="text"
              placeholder="e.g., BTC"
              value={preferences.baseAsset}
              onChange={(e) =>
                handleChange('baseAsset', e.target.value.toUpperCase())
              }
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div>
            <label
              htmlFor="quoteAsset"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Quote Asset
            </label>
            <input
              id="quoteAsset"
              type="text"
              placeholder="e.g., USDC"
              value={preferences.quoteAsset}
              onChange={(e) =>
                handleChange('quoteAsset', e.target.value.toUpperCase())
              }
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div>
            <label
              htmlFor="positionSize"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Position Size (USD)
            </label>
            <input
              id="positionSize"
              type="number"
              min="0"
              step="any"
              value={preferences.positionSize}
              onChange={(e) => handleChange('positionSize', e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div>
            <label
              htmlFor="maxDailyLoss"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Max Daily Loss (USD)
            </label>
            <input
              id="maxDailyLoss"
              type="number"
              min="0"
              step="any"
              value={preferences.maxDailyLoss}
              onChange={(e) => handleChange('maxDailyLoss', e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div>
            <label
              htmlFor="timezone"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Timezone
            </label>
            <select
              id="timezone"
              value={preferences.timezone}
              onChange={(e) => handleChange('timezone', e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              {TIMEZONES.map((tz) => (
                <option key={tz} value={tz}>
                  {tz}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-2 self-end py-2">
            <input
              id="enabled"
              type="checkbox"
              checked={preferences.enabled}
              onChange={(e) => handleChange('enabled', e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            <label htmlFor="enabled" className="text-sm font-medium text-gray-700">
              Enable automated trading
            </label>
          </div>
        </div>

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSaving}>
            Save Preferences
          </Button>
        </div>
      </form>
    </Card>
  );
};
