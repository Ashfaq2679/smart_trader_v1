export type Signal = 'BUY' | 'SELL' | 'HOLD';

export interface TradeDecision {
  signal: Signal;
  confidence: number;
  reasoning: string;
  detectedPatterns: string[];
  trendDirection: string;
  nearestSupport: number | null;
  nearestResistance: number | null;
  productId: string;
  timestamp: string;
}

export interface CoinScanResult {
  productId: string;
  tradeDecision: TradeDecision;
  volume24h: number;
  volumeChangePercent24h: number;
  priceChangePercent24h: number;
  currentPrice: number;
  profitPotentialScore: number;
  summary: string;
  scannedAt: string;
}
