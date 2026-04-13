import { useCallback, useEffect, useState } from 'react';
import { scannerApi } from '../../api';
import { useAuth } from '../../hooks/useAuth';
import { Card } from '../common/Card';
import { Badge } from '../common/Badge';
import { LoadingSpinner } from '../common/LoadingSpinner';
import { Alert } from '../common/Alert';
import {
  formatCurrency,
  formatPercent,
  formatRelativeTime,
  getSignalColor,
} from '../../utils/formatters';
import type { CoinScanResult } from '../../types';

/**
 * Dashboard overview component showing recent scan results and market summary.
 */
export const Dashboard = () => {
  const { user } = useAuth();
  const [results, setResults] = useState<CoinScanResult[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchResults = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await scannerApi.getResults();
      setResults(response.data);
    } catch {
      setError('Failed to load scan results');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchResults();
  }, [fetchResults]);

  if (isLoading) {
    return <LoadingSpinner size="lg" message="Loading dashboard..." />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-sm text-gray-500">
          Welcome back, {user?.displayName ?? 'Trader'}
        </p>
      </div>

      {error && (
        <Alert type="error" message={error} onDismiss={() => setError(null)} />
      )}

      {/* Summary Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <p className="text-sm text-gray-500">Total Scanned</p>
          <p className="mt-1 text-2xl font-bold text-gray-900">
            {results.length}
          </p>
        </Card>
        <Card>
          <p className="text-sm text-gray-500">Buy Signals</p>
          <p className="mt-1 text-2xl font-bold text-green-600">
            {results.filter((r) => r.tradeDecision.signal === 'BUY').length}
          </p>
        </Card>
        <Card>
          <p className="text-sm text-gray-500">Sell Signals</p>
          <p className="mt-1 text-2xl font-bold text-red-600">
            {results.filter((r) => r.tradeDecision.signal === 'SELL').length}
          </p>
        </Card>
        <Card>
          <p className="text-sm text-gray-500">Hold Signals</p>
          <p className="mt-1 text-2xl font-bold text-yellow-600">
            {results.filter((r) => r.tradeDecision.signal === 'HOLD').length}
          </p>
        </Card>
      </div>

      {/* Top Opportunities Table */}
      <Card title="Top Market Opportunities">
        {results.length === 0 ? (
          <p className="py-8 text-center text-gray-500">
            No scan results available. Run a market scan to see opportunities.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-gray-200 text-xs uppercase text-gray-500">
                <tr>
                  <th className="px-4 py-3">Product</th>
                  <th className="px-4 py-3">Price</th>
                  <th className="px-4 py-3">24h Change</th>
                  <th className="px-4 py-3">Signal</th>
                  <th className="px-4 py-3">Score</th>
                  <th className="px-4 py-3">Scanned</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {results.slice(0, 10).map((result) => (
                  <tr key={result.productId} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-900">
                      {result.productId}
                    </td>
                    <td className="px-4 py-3">
                      {formatCurrency(result.currentPrice)}
                    </td>
                    <td
                      className={`px-4 py-3 ${result.priceChangePercent24h >= 0 ? 'text-green-600' : 'text-red-600'}`}
                    >
                      {formatPercent(result.priceChangePercent24h)}
                    </td>
                    <td className="px-4 py-3">
                      <Badge
                        label={result.tradeDecision.signal}
                        className={getSignalColor(result.tradeDecision.signal)}
                      />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-16 overflow-hidden rounded-full bg-gray-200">
                          <div
                            className="h-full rounded-full bg-blue-600"
                            style={{
                              width: `${result.profitPotentialScore}%`,
                            }}
                          />
                        </div>
                        <span className="text-xs text-gray-500">
                          {result.profitPotentialScore.toFixed(0)}
                        </span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {formatRelativeTime(result.scannedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
};
