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
    <div className="space-y-4">
      <Card
        title="Market Scanner"
        actions={
          <div className="flex gap-2">
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
          <p className="py-8 text-center text-gray-500">
            No scan results available. Click &quot;Run Scan&quot; or &quot;Load
            Cached&quot; to see results.
          </p>
        )}

        {!isScanning && results.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-gray-200 text-xs uppercase text-gray-500">
                <tr>
                  <th className="px-4 py-3">Product</th>
                  <th className="px-4 py-3">Price</th>
                  <th className="px-4 py-3">24h Change</th>
                  <th className="px-4 py-3">Volume</th>
                  <th className="px-4 py-3">Signal</th>
                  <th className="px-4 py-3">Confidence</th>
                  <th className="px-4 py-3">Score</th>
                  <th className="px-4 py-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {results.map((result) => (
                  <tr key={result.productId} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-900">
                      {result.productId}
                    </td>
                    <td className="px-4 py-3">
                      {formatCurrency(result.currentPrice)}
                    </td>
                    <td
                      className={`px-4 py-3 ${
                        result.priceChangePercent24h >= 0
                          ? 'text-green-600'
                          : 'text-red-600'
                      }`}
                    >
                      {formatPercent(result.priceChangePercent24h)}
                    </td>
                    <td className="px-4 py-3">
                      {formatCompactNumber(result.volume24h)}
                    </td>
                    <td className="px-4 py-3">
                      <Badge
                        label={result.tradeDecision.signal}
                        className={getSignalColor(
                          result.tradeDecision.signal,
                        )}
                      />
                    </td>
                    <td className="px-4 py-3">
                      {(result.tradeDecision.confidence * 100).toFixed(0)}%
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
                    <td className="px-4 py-3">
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
