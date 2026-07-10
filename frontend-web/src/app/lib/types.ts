export interface DealSource {
  site_name: string;
  post_url: string;
  ecommerce_url?: string;
  price?: number;
  currency?: string;
}

export interface Deal {
  id: number;
  community_name: string;
  title: string;
  price: string;
  image_url: string | null;
  scraped_at: string;
  post_link: string;
  ecommerce_url?: string;
  currency?: string;
  shipping_fee?: string;
  category: string;
  isClosed: boolean;
  originalDate?: string;
  honeyScore?: number;
  aiSummary?: string;
  sources?: DealSource[];
}

export interface AIDemoResult {
  score: string;
  probability: string;
  priceTrend: string;
  verdict: string;
  tip: string;
  isRealData: boolean;
}

export interface PriceHistoryItem {
  price: number;
  originalPrice: number | null;
  discountRate: number | null;
  recordedAt: string;
  source: string;
}
