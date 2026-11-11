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
  name?: string;
  title?: string;
  option_name?: string;
  site_name?: string;
  price?: number;
  currency?: string;
  code?: string;
  url?: string;
  post_url?: string;
  ecommerce_url?: string;
  image_url?: string;
  sub_urls?: string[];
}

export interface RawDealApiResponse {
  id: number;
  title?: string;
  shop_name?: string | null;
  shopName?: string | null;
  ecommerce_url?: string | null;
  post_url?: string | null;
  post_link?: string | null;
  link?: string | null;
  price?: string | number | null;
  site_name?: string;
  site_names?: string[];
  community_name?: string;
  communityName?: string;
  source_community_id?: number;
  image_url?: string | null;
  imageUrl?: string | null;
  scraped_at?: string;
  timeAgo?: string;
  shipping_fee?: string | null;
  shippingFee?: string | null;
  category?: string;
  is_closed?: boolean;
  isClosed?: boolean;
  created_at?: string;
  originalDate?: string;
  currency?: string;
  honey_score?: number;
  honeyScore?: number;
  ai_summary?: string;
  aiSummary?: string;
  content_html?: string;
  contentHtml?: string;
  sources?: DealSource[];
  tags?: string[];
  has_options?: boolean;
  hasOptions?: boolean;
  options_count?: number;
  options_data?: DealOptionItem[];
  optionsData?: DealOptionItem[];
}

export interface Deal {
  id: number;
  community_name: string;
  shop_name?: string;
  title: string;
  price: string;
  image_url: string | null;
  scraped_at: string;
  post_link: string;
  ecommerce_url?: string;
  currency?: string;
  shipping_fee?: string | null;
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
