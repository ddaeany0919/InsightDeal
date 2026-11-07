export interface DealSource {
  site_name: string;
  post_url: string;
  ecommerce_url?: string;
  price?: number;
  currency?: string;
  code?: string;
  image_url?: string;
  sub_urls?: string[];
}

export interface DealOptionItem {
  name: string;
  price?: number;
  currency?: string;
  code?: string;
  url?: string;
  image_url?: string;
  sub_urls?: string[];
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
  contentHtml?: string;
  sources?: DealSource[];
  tags?: string[];
  has_options?: boolean;
  options_count?: number;
  options_data?: DealOptionItem[];
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

export interface PopularKeywordRanking {
  rank: number;
  keyword: string;
  deal_count: number;
  recent_count: number;
  status: "HOT" | "UP" | "NEW" | "SAME" | string;
}
