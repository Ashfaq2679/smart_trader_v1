import { useCallback, useState } from 'react';
import { scannerApi } from '../../api';
import { useAuth } from '../../hooks/useAuth';
import { Card } from '../common/Card';
import { Badge } from '../common/Badge';
import { Button } from '../common/Button';
import { LoadingSpinner } from '../common/LoadingSpinner';
import { Alert } from '../common/Alert';
import {
  formatCurrency,
  formatPercent,
  formatCompactNumber,
  getSignalColor,
} from '../../utils/formatters';
import type { CoinScanResult } from '../../types';

interface ScanResultsProps {
  onAnalyze?: (productId: string) => void;
}

/**
 * Component for displaying market scan results with ability to trigger new scans.
 */
export const ScanResults = ({ onAnalyze }: ScanResultsProps) => {
  const { user } = useAuth();
  const [results, setResults] = useState<CoinScanResult[]>([]);
  const [isScanning, setIsScanning] = useState(false);
  const [isLoadingCached, setIsLoadingCached] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadCachedResults = useCallback(async () => {
    setIsLoadingCached(true);
    setError(null);
    try {
      const response = await scannerApi.getResults();
      setResults(response.data);
    } catch {
      setError('Failed to load cached results');
    } finally {
      setIsLoadingCached(false);
    }
  }, []);

  const handleScan = async () => {
    if (!user?.userId) return;
    setIsScanning(true);
    setError(null);
    try {
      const response = await scannerApi.scan(user.userId);
      setResults(response.data);
    } catch {
      setError('Failed to run market scan');
    } finally {
      setIsScanning(false);
    }
  };

  return (
    <div className="d-flex flex-column gap-3">
      <Card
        title="Market Scanner"
        actions={
          <div className="d-flex gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={loadCachedResults}
              isLoading={isLoadingCached}
            >
              Load Cached
            </Button>
            <Button size="sm" onClick={handleScan} isLoading={isScanning}>
              Run Scan
            </Button>
          </div>
        }
      >
        {error && (
          <Alert
            type="error"
            message={error}
            onDismiss={() => setError(null)}
          />
        )}

        {isScanning && (
          <LoadingSpinner message="Scanning market... This may take a moment." />
        )}

        {!isScanning && results.length === 0 && (
          <p className="py-4 text-center text-secondary">
            No scan results available. Click &quot;Run Scan&quot; or &quot;Load
            Cached&quot; to see results.
          </p>
        )}

        {!isScanning && results.length > 0 && (
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead className="table-light">
                <tr>
                  <th className="small text-uppercase text-secondary">Product</th>
                  <th className="small text-uppercase text-secondary">Price</th>
                  <th className="small text-uppercase text-secondary">24h Change</th>
                  <th className="small text-uppercase text-secondary">Volume</th>
                  <th className="small text-uppercase text-secondary">Signal</th>
                  <th className="small text-uppercase text-secondary">Confidence</th>
                  <th className="small text-uppercase text-secondary">Score</th>
                  <th className="small text-uppercase text-secondary">Actions</th>
                </tr>
              </thead>
              <tbody>
                {results.map((result) => (
                  <tr key={result.productId}>
                    <td className="fw-medium">
                      {result.productId}
                    </td>
                    <td>
                      {formatCurrency(result.currentPrice)}
                    </td>
                    <td
                      className={
                        result.priceChangePercent24h >= 0
                          ? 'text-success'
                          : 'text-danger'
                      }
                    >
                      {formatPercent(result.priceChangePercent24h)}
                    </td>
                    <td>
                      {formatCompactNumber(result.volume24h)}
                    </td>
                    <td>
                      <Badge
                        label={result.tradeDecision.signal}
                        className={getSignalColor(
                          result.tradeDecision.signal,
                        )}
                      />
                    </td>
                    <td>
                      {(result.tradeDecision.confidence * 100).toFixed(0)}%
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
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onAnalyze?.(result.productId)}
                      >
                        Analyze
                      </Button>
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
