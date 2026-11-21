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
  total_price?: number | null;
  site_name?: string;
  site_names?: string[];
  community_name?: string;
  communityName?: string;
  source_community_id?: number;
  image_url?: string | null;
  imageUrl?: string | null;
  scraped_at?: string;
  indexed_at?: string | null;
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
  score?: number;
  is_super_hotdeal?: boolean;
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
  // 1-RTT 상세 요약 및 이중 키 필드
  buy_count?: number;
  buyCount?: number;
  pass_count?: number;
  passCount?: number;
  save_count?: number;
  saveCount?: number;
  like_count?: number;
  dislike_count?: number;
  user_vote?: string | null;
  userVote?: string | null;
  comment_count?: number;
  commentCount?: number;
  comments?: number;
  views?: number;
  view_count?: number;
  recommendations?: number;
  history_count?: number;
  historyCount?: number;
  is_search_fallback?: boolean;
  isSearchFallback?: boolean;
  is_free?: boolean;
  isFree?: boolean;
  body_images?: string[];
  bodyImages?: string[];
  report_count?: number;
  reportCount?: number;
  initial_history?: PriceHistoryItem[];
  initialHistory?: PriceHistoryItem[];
  initial_history_period?: string;
  initialHistoryPeriod?: string;
  similar_deals?: RawDealApiResponse[];
  similarDeals?: RawDealApiResponse[];
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
  isSuperHotdeal?: boolean;
  likeCount?: number;
  aiSummary?: string;
  contentHtml?: string;
  sources?: DealSource[];
  tags?: string[];
  has_options?: boolean;
  options_count?: number;
  options_data?: DealOptionItem[];
  // 1-RTT 요약 필
  buyCount?: number;
  passCount?: number;
  userVote?: string | null;
  commentCount?: number;
  comments?: number;
  recommendations?: number;
  historyCount?: number;
  isSearchFallback?: boolean;
  is_search_fallback?: boolean;
  isFree?: boolean;
  bodyImages?: string[];
  reportCount?: number;
}

export interface NextCursor {
  indexed_at: string;
  id: number;
}

export interface HotDealsApiResponse {
  deals?: RawDealApiResponse[];
  items?: RawDealApiResponse[];
  has_more?: boolean;
  hasMore?: boolean;
  total?: number;
  next_cursor?: NextCursor | null;
  nextCursor?: NextCursor | null;
}

export type HotDealsResponse = HotDealsApiResponse;

export interface DealVoteApiResponse {
  buy_count?: number;
  buyCount?: number;
  pass_count?: number;
  passCount?: number;
  save_count?: number;
  saveCount?: number;
  user_vote?: string | null;
  userVote?: string | null;
}

export interface DealCommentItem {
  id: number;
  userId?: string;
  user_id?: string;
  nickname: string;
  content: string;
  createdAt?: string | null;
  created_at?: string | null;
}

export interface WishlistApiItem {
  id: number;
  keyword: string;
  target_price: number;
  user_id: string;
  is_active?: boolean;
  alert_enabled?: boolean;
  created_at?: string;
  updated_at?: string;
}

export interface VapidPublicKeyResponse {
  public_key?: string;
  publicKey?: string;
}

export interface CategoryMetaItem {
  key: string;
  label: string;
}

export interface WebDeviceAuth {
  deviceUuid: string;
  deviceToken: string;
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

export interface PairingCodeResponse {
  code: string;
  expires_at?: string;
  expiresAt?: string;
  expires_in_sec?: number;
}

export interface ClaimPairingCodeResponse {
  status?: string;
  success?: boolean;
  merged_keywords?: number;
  mergedKeywords?: number;
  merged_wishlists?: number;
  mergedWishlists?: number;
  merged_alerts?: number;
  mergedAlerts?: number;
  dnd_synced?: boolean;
  dndSynced?: boolean;
  detail?: string;
}

export interface AccountAuthResponse {
  username: string;
  nickname: string;
  honey_points: number;
  access_token?: string;
  token_type?: string;
  detail?: string;
}

export interface ReportClosedResponse {
  status?: string;
  deal_id?: number;
  dealId?: number;
  report_count?: number;
  reportCount?: number;
  is_closed?: boolean;
  isClosed?: boolean;
  threshold?: number;
  detail?: string;
}

export interface NotificationLogItem {
  id: string | number;
  title: string;
  keyword: string;
  receivedAt?: number;
  received_at?: string;
  dealUrl?: string;
  deal_url?: string;
  is_read?: boolean;
  dnd_suppressed?: boolean;
  digest_sent_at?: string | null;
}

export type ThemeMode = "system" | "light" | "dark" | "amoled";
