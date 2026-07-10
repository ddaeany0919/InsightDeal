export interface PriceHistoryItem {
  price: number;
  originalPrice: number | null;
  discountRate: number | null;
  recordedAt: string;
  source: string;
}
