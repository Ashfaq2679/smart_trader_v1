export interface UserPreferences {
  id?: string;
  userId: string;
  strategy: string;
  granularity: string;
  baseAsset: string;
  quoteAsset: string;
  positionSize: string;
  maxDailyLoss: string;
  timezone: string;
  enabled: boolean;
  updatedAt?: string;
}
