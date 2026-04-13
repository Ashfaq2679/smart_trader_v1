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
        <div className="d-flex flex-column gap-3">
          <div className="row g-3">
            <div className="col-6 col-sm-4">
              <p className="small text-secondary mb-1">Signal</p>
              <Badge
                label={decision.signal}
                className={getSignalColor(decision.signal)}
              />
            </div>
            <div className="col-6 col-sm-4">
              <p className="small text-secondary mb-1">Confidence</p>
              <p className="h6 fw-semibold mb-0">
                {(decision.confidence * 100).toFixed(1)}%
              </p>
            </div>
            <div className="col-6 col-sm-4">
              <p className="small text-secondary mb-1">Trend</p>
              <p className="h6 fw-semibold mb-0">{decision.trendDirection}</p>
            </div>
            {decision.nearestSupport != null && (
              <div className="col-6 col-sm-4">
                <p className="small text-secondary mb-1">Nearest Support</p>
                <p className="h6 fw-semibold text-success mb-0">
                  {formatCurrency(decision.nearestSupport)}
                </p>
              </div>
            )}
            {decision.nearestResistance != null && (
              <div className="col-6 col-sm-4">
                <p className="small text-secondary mb-1">Nearest Resistance</p>
                <p className="h6 fw-semibold text-danger mb-0">
                  {formatCurrency(decision.nearestResistance)}
                </p>
              </div>
            )}
          </div>

          <div>
            <p className="small fw-medium mb-2">Reasoning</p>
            <p className="bg-light rounded p-3 small text-secondary mb-0">
              {decision.reasoning}
            </p>
          </div>

          {decision.detectedPatterns.length > 0 && (
            <div>
              <p className="small fw-medium mb-2">
                Detected Patterns
              </p>
              <div className="d-flex flex-wrap gap-2">
                {decision.detectedPatterns.map((pattern) => (
                  <Badge
                    key={pattern}
                    label={pattern}
                    className="bg-info-subtle text-info-emphasis"
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
