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
      <form onSubmit={handleSubmit}>
        <div className="d-flex flex-column gap-3">
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

          <div className="row g-3">
            <div className="col-12 col-sm-6">
              <label htmlFor="strategy" className="form-label small fw-medium">
                Strategy
              </label>
              <select
                id="strategy"
                value={preferences.strategy}
                onChange={(e) => handleChange('strategy', e.target.value)}
                className="form-select form-select-sm"
              >
                {STRATEGIES.map((s) => (
                  <option key={s} value={s}>
                    {s.replace('_', ' ')}
                  </option>
                ))}
              </select>
            </div>

            <div className="col-12 col-sm-6">
              <label htmlFor="granularity" className="form-label small fw-medium">
                Candle Granularity
              </label>
              <select
                id="granularity"
                value={preferences.granularity}
                onChange={(e) => handleChange('granularity', e.target.value)}
                className="form-select form-select-sm"
              >
                {GRANULARITIES.map((g) => (
                  <option key={g} value={g}>
                    {g.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </div>

            <div className="col-12 col-sm-6">
              <label htmlFor="baseAsset" className="form-label small fw-medium">
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
                className="form-control form-control-sm"
              />
            </div>

            <div className="col-12 col-sm-6">
              <label htmlFor="quoteAsset" className="form-label small fw-medium">
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
                className="form-control form-control-sm"
              />
            </div>

            <div className="col-12 col-sm-6">
              <label htmlFor="positionSize" className="form-label small fw-medium">
                Position Size (USD)
              </label>
              <input
                id="positionSize"
                type="number"
                min="0"
                step="any"
                value={preferences.positionSize}
                onChange={(e) => handleChange('positionSize', e.target.value)}
                className="form-control form-control-sm"
              />
            </div>

            <div className="col-12 col-sm-6">
              <label htmlFor="maxDailyLoss" className="form-label small fw-medium">
                Max Daily Loss (USD)
              </label>
              <input
                id="maxDailyLoss"
                type="number"
                min="0"
                step="any"
                value={preferences.maxDailyLoss}
                onChange={(e) => handleChange('maxDailyLoss', e.target.value)}
                className="form-control form-control-sm"
              />
            </div>

            <div className="col-12 col-sm-6">
              <label htmlFor="timezone" className="form-label small fw-medium">
                Timezone
              </label>
              <select
                id="timezone"
                value={preferences.timezone}
                onChange={(e) => handleChange('timezone', e.target.value)}
                className="form-select form-select-sm"
              >
                {TIMEZONES.map((tz) => (
                  <option key={tz} value={tz}>
                    {tz}
                  </option>
                ))}
              </select>
            </div>

            <div className="col-12 col-sm-6 d-flex align-items-end py-2">
              <div className="form-check">
                <input
                  id="enabled"
                  type="checkbox"
                  checked={preferences.enabled}
                  onChange={(e) => handleChange('enabled', e.target.checked)}
                  className="form-check-input"
                />
                <label htmlFor="enabled" className="form-check-label small fw-medium">
                  Enable automated trading
                </label>
              </div>
            </div>
          </div>

          <div className="d-flex justify-content-end">
            <Button type="submit" isLoading={isSaving}>
              Save Preferences
            </Button>
          </div>
        </div>
      </form>
    </Card>
  );
};
