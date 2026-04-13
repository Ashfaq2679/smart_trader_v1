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
    <div className="d-flex flex-column gap-4">
      <div>
        <h1 className="h4 fw-bold">Dashboard</h1>
        <p className="small text-secondary">
          Welcome back, {user?.displayName ?? 'Trader'}
        </p>
      </div>

      {error && (
        <Alert type="error" message={error} onDismiss={() => setError(null)} />
      )}

      {/* Summary Cards */}
      <div className="row g-3">
        <div className="col-12 col-sm-6 col-lg-3">
          <Card>
            <p className="small text-secondary mb-1">Total Scanned</p>
            <p className="h4 fw-bold mb-0">
              {results.length}
            </p>
          </Card>
        </div>
        <div className="col-12 col-sm-6 col-lg-3">
          <Card>
            <p className="small text-secondary mb-1">Buy Signals</p>
            <p className="h4 fw-bold text-success mb-0">
              {results.filter((r) => r.tradeDecision.signal === 'BUY').length}
            </p>
          </Card>
        </div>
        <div className="col-12 col-sm-6 col-lg-3">
          <Card>
            <p className="small text-secondary mb-1">Sell Signals</p>
            <p className="h4 fw-bold text-danger mb-0">
              {results.filter((r) => r.tradeDecision.signal === 'SELL').length}
            </p>
          </Card>
        </div>
        <div className="col-12 col-sm-6 col-lg-3">
          <Card>
            <p className="small text-secondary mb-1">Hold Signals</p>
            <p className="h4 fw-bold text-warning mb-0">
              {results.filter((r) => r.tradeDecision.signal === 'HOLD').length}
            </p>
          </Card>
        </div>
      </div>

      {/* Top Opportunities Table */}
      <Card title="Top Market Opportunities">
        {results.length === 0 ? (
          <p className="py-4 text-center text-secondary">
            No scan results available. Run a market scan to see opportunities.
          </p>
        ) : (
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead className="table-light">
                <tr>
                  <th className="small text-uppercase text-secondary">Product</th>
                  <th className="small text-uppercase text-secondary">Price</th>
                  <th className="small text-uppercase text-secondary">24h Change</th>
                  <th className="small text-uppercase text-secondary">Signal</th>
                  <th className="small text-uppercase text-secondary">Score</th>
                  <th className="small text-uppercase text-secondary">Scanned</th>
                </tr>
              </thead>
              <tbody>
                {results.slice(0, 10).map((result) => (
                  <tr key={result.productId}>
                    <td className="fw-medium">
                      {result.productId}
                    </td>
                    <td>
                      {formatCurrency(result.currentPrice)}
                    </td>
                    <td
                      className={result.priceChangePercent24h >= 0 ? 'text-success' : 'text-danger'}
                    >
                      {formatPercent(result.priceChangePercent24h)}
                    </td>
                    <td>
                      <Badge
                        label={result.tradeDecision.signal}
                        className={getSignalColor(result.tradeDecision.signal)}
                      />
                    </td>
                    <td>
                      <div className="d-flex align-items-center gap-2">
                        <div className="progress score-bar">
                          <div
                            className="progress-bar"
                            role="progressbar"
                            aria-valuenow={result.profitPotentialScore}
                            aria-valuemin={0}
                            aria-valuemax={100}
                            style={{
                              width: `${result.profitPotentialScore}%`,
                            }}
                          />
                        </div>
                        <small className="text-secondary">
                          {result.profitPotentialScore.toFixed(0)}
                        </small>
                      </div>
                    </td>
                    <td>
                      <small className="text-secondary">
                        {formatRelativeTime(result.scannedAt)}
                      </small>
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
