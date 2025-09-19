import os
import re
import json
import google.generativeai as genai
from google.generativeai.types import GenerationConfig

# 환경변수에서 API 키 안전하게 로드
if "GOOGLE_API_KEY" not in os.environ:
    raise KeyError("환경 변수 'GOOGLE_API_KEY'가 설정되지 않았습니다. API 키를 설정해주세요.")
genai.configure(api_key=os.environ["GOOGLE_API_KEY"])

# AI 모델 설정을 미리 정의합니다.
generation_config = GenerationConfig(
    temperature=0.0,
    max_output_tokens=2048,
)
model = genai.GenerativeModel('gemini-2.5-flash-lite-preview-09-2025', generation_config=generation_config)

def detect_free_shipping(raw_title: str) -> bool:
    free_keywords = ['무배', '무료', '택배비 포함']
    return any(keyword in raw_title for keyword in free_keywords)

def parse_title_with_ai(raw_title: str):
    # 이 함수는 카테고리 분류에만 사용되므로 기존과 동일하게 둡니다.
    print(f"  [AI Title Parser Activated] Analyzing title for category: {raw_title[:40]}...")
    prompt = f"""
    You are an expert at extracting and categorizing information from Korean hot deal titles.
    From the text below, extract 'shop_name', 'product_title', and 'category'.

    - 'shop_name' is the name of the online store. Standardize common names (e.g., "11마존" -> "11번가", "G9" -> "G마켓", "스스" -> "스마트스토어").
    - 'product_title' MUST be the pure product name, excluding all other info.
    - 'category' MUST be one of the following options only:
      ["디지털/가전", "PC/하드웨어", "음식/식품", "의류/패션", "생활/잡화", "모바일/상품권", "패키지/이용권", "적립/이벤트", "기타", "해외핫딜", "알리익스프레스"]

    - If the product is from AliExpress, the category is "알리익스프레스".
    - If it is clearly an overseas deal but not AliExpress, use "해외핫딜".
    - Choose the most appropriate category from the list. If none seem to fit, use "기타".

    Return the result ONLY in a single valid JSON object format.

    Text: "{raw_title}"
    """
    try:
        response = model.generate_content(prompt)
        json_str_match = re.search(r'\{.*\}', response.text, re.DOTALL)
        if not json_str_match: return None
        json_str = json_str_match.group(0)
        parsed_json = json.loads(json_str)

        shop_name = parsed_json.get('shop_name') or parsed_json.get('쇼핑몰') or '정보 없음'
        product_title = parsed_json.get('product_title') or parsed_json.get('상품명') or raw_title
        category = parsed_json.get('category') or parsed_json.get('카테고리') or '기타'

        if not product_title or not product_title.strip(): product_title = raw_title
        shipping_fee = "정보 없음"
        if detect_free_shipping(raw_title):
            shipping_fee = "무료"
        return {'shop_name': shop_name, 'product_title': product_title,'category': category, 'shipping_fee': shipping_fee}
    except Exception as e:
        print(f"  [AI Title Parser Error] Exception: {e}")
        return None

def parse_content_with_ai(content_text: str, post_link: str, original_title: str):
    """
    게시물 본문 텍스트, 링크, 원본 제목을 입력받아, LLM을 사용하여 구조화된 딜 정보를 추출합니다.
    이 함수는 여러 개의 딜이 포함된 복잡한 게시물 분석에 특화되어 있습니다.
    """
    print(f"  [AI Multi-Deal Parser Activated] Analyzing content for: {original_title[:30]}...")

    prompt = f"""
    ## ROLE & OBJECTIVE
    You are a hyper-precise data extraction bot specializing in Korean e-commerce deals. Your sole objective is to analyze the provided text and extract all distinct product deals into a structured JSON format. You must be meticulous and ignore any irrelevant conversational text.
    ## CRITICAL INSTRUCTION: SOURCE OF TRUTH
    1. The `PRODUCT_PAGE_HTML`, if provided, is the **ultimate source of truth**. Information found here (price, shipping, stock) OVERRIDES any other information.
    2. **✨ (New) The `[Image Text]` block**, if present in the `TEXT`, is the second most reliable source, as it comes directly from an image. You should give this high priority, especially for `shipping_fee`.
    ## ANALYSIS WORKFLOW
    You must follow these steps in order:
    1.  **SCAN & IGNORE**: First, read the entire `TEXT`. Ignore irrelevant content like personal opinions, greetings, or questions. Focus only on parts describing products and prices.
    2.  **IDENTIFY & SEGMENT**: Identify and mentally segment the text into self-contained 'deal blocks'. A 'deal block' is a section of text that describes one specific product and its associated price(s). These blocks often start with a bolded product title.
    3.  **ANALYZE DEAL TYPE**: Before extracting, determine the deal type for each block ('Standard', 'Options', 'Coupon/Discount') and apply the special rules below.
    4.  **EXTRACT PER BLOCK**: For each 'deal block', apply the `EXTRACTION_RULES` to extract the required information.

    ## EXTRACTION RULES (Apply per block)
    * **`product_title`**:
        * Rule 1: Extract the main, specific product name.
        * Rule 2: Clean the title. Remove generic or promotional phrases like "초특가", "역대급", "강력 추천".
    * **`price`**:
        * Priority 1: Find '최종가' and extract its corresponding value.
        * Priority 2: If not present, find the next most likely price (e.g., '판매가', '할인가').
        * Priority 3: If no price can be found, the value MUST be "정보 없음".
    * **`shipping_fee`**:
        * Rule 1: Look for explicit shipping fee information.
        * Rule 2: If not found, the value MUST be "정보 없음".
    * **`ecommerce_link`**: Find the direct e-commerce purchase link within the text block. It often follows keywords like '바로가기', '구매링크', '링크>>'. If not found, it must be `null`.
    * **`is_closed`**: `true` if the title/text contains keywords like '종료', '품절', '마감', otherwise `false`.
    * **`deal_type`**: Must be "이벤트" for point rewards/events, otherwise "일반".

    * **SPECIAL RULES BY DEAL TYPE**:
        * **Type A: Options Deal**: If a block lists sizes (e.g., 20인치, 24인치) or colors for the SAME base product, you MUST treat this as a SINGLE deal.
            * `product_title`: Synthesize to include the options (e.g., 'Product Name (20/24/26인치)').
            * `price`: Use the starting price with a tilde '~' (e.g., '192,510원~').
        * **Type B: Coupon/Discount Deal**: If the title describes a percentage discount (e.g., '99% 할인'), a coupon, or points.
            * `product_title`: Synthesize to reflect the benefit (e.g., 'KakaoPay 99% Discount Coupon').
            * `price`: Describe the benefit, not a purchase price (e.g., '최대 1,000원 할인').
            * `shipping_fee`: Use 'N/A' if it's not a physical product.
        * **Type C: Standard Deal**: If none of the above, extract information as normal.
        * **Type D: Pre-order Deal**: If the text contains keywords like '사전예약', '예약구매', '프리오더'.
            * `product_title`: Synthesize to include the pre-order status (e.g., 'Product Name (사전예약)').
            * `price`: Extract the specific pre-order price.
        * **Type E: Sold-Out Deal**: If `is_closed` is `true`, ensure the deal is still extracted but flagged correctly.
        * **Type F: Event/Reward Deal**: If `deal_type` is "이벤트".
            * `product_title`: Summarize the event (e.g., '네이버페이 일일 클릭적립').
            * `price`: State the reward (e.g., '총 38원 적립').
            * `shipping_fee`: Use 'N/A'.
        * **Type G: Informational/Event Deal**: If the text is an announcement for a general sale event with multiple different products/links (e.g., '추석 할인 프로모션'), treat it as a SINGLE deal.
            * `product_title`: Summarize the event title (e.g., 'H2mall 추석 할인 프로모션').
            * `price`: Use '가격 상이' or '본문 참조'.
            * `deal_type`: Must be "이벤트".
    # --- 최종 규칙 ---
    ## FINAL CRITICAL RULE: Main Price vs. Secondary Benefit
    * The primary goal is to extract the MAIN PURCHASE DEAL.
    * Secondary, conditional benefits like "포토리뷰 작성 시 OOO원 적립" (point reward for photo review) or "구매 확정 시 캐시백" (cashback upon purchase confirmation) are NOT separate deals.
    * You MUST IGNORE these secondary benefits when extracting the primary deal's price. Focus only on the actual transaction price.    
    ## EXAMPLES (Learn from these patterns)
    * **Example 1: Conditional Deal**
        * Input: "퀘스트3 : 729,000원 구매 시 배터리스트랩: 37,800원으로 할인"
        * Output: A SINGLE deal for the main product, with a synthesized title.
        ```json
        {{ "deals": [ {{ "product_title": "메타 퀘스트3 (구매 시 배터리스트랩 37,800원)", "price": "729,000원" }} ] }}
        ```
    * **Example 2: Multiple Independent Deals**
        * Input: "제임스딘 반팔티 최종가 8,682원. 제임스딘 민소매 나시 최종가 7,621원."
        * Output: TWO SEPARATE deals.
        ```json
        {{ "deals": [ {{ "product_title": "제임스딘 반팔티", "price": "8,682원" }}, {{ "product_title": "제임스딘 민소매 나시", "price": "7,621원" }} ] }}
        ```
    * **Example 3: Options Deal**
        * Input: "코르딕스 캐리어. 20인치 192,510원. 24인치 209,250원."
        * Output: A SINGLE deal with the starting price.
        ```json
        {{ "deals": [ {{ "product_title": "코르딕스 캐리어 (20/24/26인치)", "price": "192,510원~" }} ] }}
        ```
    * **Example 4: Coupon Deal**
        * Input: "[카카오페이] 편의점 99% 할인(1,000원 한도)"
        * Output: A SINGLE deal describing the benefit.
        ```json
        {{ "deals": [ {{ "product_title": "카카오페이 편의점 99% 할인 쿠폰", "price": "최대 1,000원 할인", "shipping_fee": "N/A" }} ] }}
        ```
    * **Example 5: Pre-order Deal**
        * Input: "[네이버] 에버미디어 GC311G2 (사전예약110,000원)" and Body: "사전예약 구매가 110,000원이에요"
        * Output: A SINGLE deal with pre-order status in the title.
        ```json
        {{ "deals": [ {{ "product_title": "에버미디어 GC311G2 (사전예약)", "price": "110,000원" }} ] }}
         ```
     * **Example 6: Sold-Out Deal**
        * Input: "[G마켓][종료] LG 모니터 (150,000원)"
        * Output: A SINGLE deal with `is_closed: true`.
        ```json
        {{ "deals": [ {{ "product_title": "LG 모니터", "price": "150,000원", "is_closed": true }} ] }}
        ```
    * **Example 7: Event/Reward Deal**
        * Input: "[네이버페이] 일일적립, 클릭 38원"
        * Output: A SINGLE event deal.
        ```json
        {{ "deals": [ {{ "deal_type": "이벤트", "product_title": "네이버페이 일일 클릭적립", "price": "총 38원 적립", "shipping_fee": "N/A" }} ] }}
        ```
    * **Example 8: Deal with Secondary Benefit**
        * Input: "에버미디어 GC311G2 사전예약 110,000원. 포토리뷰 작성 시 네이버페이 3,000원 추가 적립."
        * Output: A SINGLE deal for the main purchase. IGNORE the 3,000 won review benefit in the output.
        ```json
        {{ "deals": [ {{ "product_title": "에버미디어 GC311G2 (사전예약)", "price": "110,000원" }} ] }}
        ```
     * **Example 9: Informational Event Deal**
        * Input: "[H2mall]추석 할인 프로모션 및 기타 이벤트 안내" with multiple links in the body.
        * Output: A SINGLE event deal.
        ```json
        {{ "deals": [ {{ "deal_type": "이벤트", "product_title": "H2mall 추석 할인 프로모션", "price": "가격 상이" }} ] }}
        ```
    ## OUTPUT FORMAT
    * You MUST return ONLY a single, valid JSON object.
    * The JSON must have a top-level `shop_name` key and a `deals` key with an array of deal objects.

    [Input Data]
    ORIGINAL_TITLE: "{original_title}"
    TEXT: "{content_text}"
    URL: "{post_link}"
    """

    try:
        response = model.generate_content(prompt)
        # AI가 ```json ... ``` 코드 블록으로 응답하는 경우를 안정적으로 처리
        json_str_match = re.search(r'```json\s*(\{.*?\})\s*```', response.text, re.DOTALL)
        if not json_str_match:
            json_str_match = re.search(r'\{.*\}', response.text, re.DOTALL)
        
        if not json_str_match:
            print(f"  [AI Multi-Deal Parser Warning] No JSON object found in response.")
            return None

        json_str = json_str_match.group(1) if '```json' in json_str_match.group(0) else json_str_match.group(0)
        parsed_json = json.loads(json_str)

        print(f"  [AI Multi-Deal Analysis] Found {len(parsed_json.get('deals', []))} deals.")
        return parsed_json

    except Exception as e:
        print(f"  [AI Multi-Deal Parser Error] Exception: {e}")
        return None