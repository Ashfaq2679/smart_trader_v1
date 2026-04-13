import { useCallback, useEffect, useState } from 'react';
import { scannerApi } from '../../api';
import { useAuth } from '../../hooks/useAuth';
import { Card } from '../common/Card';
import { Badge } from '../common/Badge';
import { Button } from '../common/Button';
import { LoadingSpinner } from '../common/LoadingSpinner';
import { Alert } from '../common/Alert';
import { formatCurrency, getSignalColor } from '../../utils/formatters';
import type { TradeDecision } from '../../types';

interface ProductAnalysisProps {
  productId: string;
  onClose: () => void;
}

/**
 * Component for displaying detailed analysis of a specific product.
 */
export const ProductAnalysis = ({
  productId,
  onClose,
}: ProductAnalysisProps) => {
  const { user } = useAuth();
  const [decision, setDecision] = useState<TradeDecision | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAnalysis = useCallback(async () => {
    if (!user?.userId) return;
    setIsLoading(true);
    setError(null);
    try {
      const response = await scannerApi.analyze(productId, user.userId);
      setDecision(response.data);
    } catch {
      setError('Failed to analyze product');
    } finally {
      setIsLoading(false);
    }
  }, [productId, user?.userId]);

  useEffect(() => {
    fetchAnalysis();
  }, [fetchAnalysis]);

  if (isLoading) {
    return <LoadingSpinner message={`Analyzing ${productId}...`} />;
  }

  return (
    <Card
      title={`Analysis: ${productId}`}
      actions={
        <Button variant="ghost" size="sm" onClick={onClose}>
          Close
        </Button>
      }
    >
      {error && (
        <Alert type="error" message={error} onDismiss={() => setError(null)} />
      )}

      {decision && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            <div>
              <p className="text-sm text-gray-500">Signal</p>
              <Badge
                label={decision.signal}
                className={getSignalColor(decision.signal)}
              />
            </div>
            <div>
              <p className="text-sm text-gray-500">Confidence</p>
              <p className="text-lg font-semibold">
                {(decision.confidence * 100).toFixed(1)}%
              </p>
            </div>
            <div>
              <p className="text-sm text-gray-500">Trend</p>
              <p className="text-lg font-semibold">{decision.trendDirection}</p>
            </div>
            {decision.nearestSupport != null && (
              <div>
                <p className="text-sm text-gray-500">Nearest Support</p>
                <p className="text-lg font-semibold text-green-600">
                  {formatCurrency(decision.nearestSupport)}
                </p>
              </div>
            )}
            {decision.nearestResistance != null && (
              <div>
                <p className="text-sm text-gray-500">Nearest Resistance</p>
                <p className="text-lg font-semibold text-red-600">
                  {formatCurrency(decision.nearestResistance)}
                </p>
              </div>
            )}
          </div>

          <div>
            <p className="mb-2 text-sm font-medium text-gray-700">Reasoning</p>
            <p className="rounded-md bg-gray-50 p-3 text-sm text-gray-600">
              {decision.reasoning}
            </p>
          </div>

          {decision.detectedPatterns.length > 0 && (
            <div>
              <p className="mb-2 text-sm font-medium text-gray-700">
                Detected Patterns
              </p>
              <div className="flex flex-wrap gap-2">
                {decision.detectedPatterns.map((pattern) => (
                  <Badge
                    key={pattern}
                    label={pattern}
                    className="bg-purple-100 text-purple-800"
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </Card>
  );
};
