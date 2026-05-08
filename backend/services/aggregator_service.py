import os
import logging
from sqlalchemy.orm import Session
from sqlalchemy.sql import func
from datetime import datetime
from backend.database.models import Deal, PriceHistory, Community
from backend.services.normalizer.llm_normalizer import LlmNormalizer
import re

logger = logging.getLogger(__name__)

class AggregatorService:
    """
    🔗 스크래핑 결과(Deal)를 정규화하여 DB의 Deal과 매칭 및 병합하는 연동 레이어
    지능형 업서트(Upsert)와 가격 역사(Price History) 추적 로직이 포함됩니다.
    """
    
    def __init__(self, db_session: Session):
        self.db = db_session
        self.normalizer = LlmNormalizer()


    async def process_scraped_deal(self, community_id: int, scraped_data: dict) -> Deal:
        raw_title = scraped_data.get("title", "")
        provided_price = scraped_data.get("price", 0)
        url = scraped_data.get("url", "")
        shop_name = scraped_data.get("shop_name", "")
        image_url = scraped_data.get("image_url", "")
        
        community = self.db.query(Community).filter(Community.id == community_id).first()
        community_name = community.name if community else ""
        
        # 0. 정규식 파서를 통한 1차 정보 완전 추출
        is_closed = scraped_data.get("is_closed", False) or "종료" in raw_title or "마감" in raw_title or "품절" in raw_title
        
        # 기본 배송비는 스크래퍼가 제공한 값이나 '정보 없음'
        shipping_fee = scraped_data.get("shipping_fee")
        if not shipping_fee:
            shipping_fee = "정보 없음"

        final_price = 0
        currency = "KRW"
        
        match = re.search(r'\(\s*\$?\s*((?:[\d,]+|[\d,]*\.[\d]+)(?:원|달러|유로|€)?)(?:\s*/\s*([^\)]*))?\)', raw_title)
        if match:
            extracted_price = match.group(1).replace(',', '')
            if '달러' in extracted_price or '.' in extracted_price or '$' in raw_title:
                currency = "USD"
                extracted_price = extracted_price.replace('달러', '').replace('$', '').strip()
                if extracted_price.replace('.', '', 1).isdigit():
                    final_price = round(float(extracted_price) * 100)
            elif '유로' in extracted_price or '€' in raw_title:
                currency = "EUR"
                extracted_price = extracted_price.replace('유로', '').replace('€', '').strip()
                if extracted_price.replace('.', '', 1).isdigit():
                    final_price = round(float(extracted_price) * 100)
            else:
                extracted_price = extracted_price.replace('원', '')
                if extracted_price.isdigit(): final_price = int(extracted_price)
            if match.group(2):
                shipping_raw = match.group(2).lower()
                if '무료' in shipping_raw or '무배' in shipping_raw or 'fs' in shipping_raw or 'free' in shipping_raw:
                    shipping_fee = "무료배송"

        if final_price == 0:
            match_man = re.search(r'([\d,]*\.[\d]+|[\d,]+)만(?!\s*(원\s*)?(할인|적립|쿠폰|캐시백|이상|권))', raw_title)
            if match_man:
                final_price = int(float(match_man.group(1).replace(',', '')) * 10000)
            else:
                match_won = re.search(r'(?<![+\-])(?<!\d\s~\s)(?<!~\s)([0-9,]{3,})\s*원(?!\s*(할인|적립|쿠폰|캐시백|이상|권))', raw_title)
                if match_won:
                    final_price = int(match_won.group(1).replace(',', ''))
                else:
                    match_dollar = re.search(r'\$\s*([0-9,]+(?:\.[0-9]+)?)|([0-9,]+(?:\.[0-9]+)?)\s*달러', raw_title)
                    if match_dollar: 
                        currency = "USD"
                        val = match_dollar.group(1) or match_dollar.group(2)
                        final_price = round(float(val.replace(',', '')) * 100)
                    else:
                        match_euro = re.search(r'€\s*([0-9,]+(?:\.[0-9]+)?)|([0-9,]+(?:\.[0-9]+)?)\s*유로', raw_title)
                        if match_euro:
                            currency = "EUR"
                            val = match_euro.group(1) or match_euro.group(2)
                            final_price = round(float(val.replace(',', '')) * 100)

        # 제목에서 추출 실패 시 본문에서 스크래퍼가 넘겨준 가격 사용
        if final_price == 0 and provided_price > 0:
            final_price = provided_price
            if scraped_data.get("currency"):
                currency = scraped_data.get("currency")

        if "무배" in raw_title or "무료배송" in raw_title or "무료" in raw_title: 
            shipping_fee = "무료배송"
        
        if shipping_fee:
            sf_str = str(shipping_fee).strip()
            if sf_str in ["0", "0원"]:
                shipping_fee = "무료배송"
            elif re.match(r'^0(원)?\s*(/|\+)', sf_str):
                shipping_fee = re.sub(r'^0(원)?\s*', '무료배송 ', sf_str)
        
        # [Phase 6] 하이브리드 파이프라인 Step 2: 쇼핑몰 메타태그 직공 (WAF 우회 및 정가 추출)
        import urllib.request
        from bs4 import BeautifulSoup
        
        if final_price == 0 and scraped_data.get("ecommerce_link"):
            try:
                target_url = scraped_data.get("ecommerce_link")
                if 'coupang' not in target_url: # 쿠팡은 WAF 방어가 100%이므로 제외
                    logger.info(f"🕸️ 정규식 실패. 쇼핑몰({target_url})에 직접 침투하여 메타태그 스나이핑 시도...")
                    req = urllib.request.Request(target_url, headers={'User-Agent': 'Mozilla/5.0'})
                    with urllib.request.urlopen(req, timeout=3) as res:
                        soup = BeautifulSoup(res.read(), 'html.parser')
                        # og:price:amount, itemprop=price 등 표준 마크업 탐색
                        meta_price = soup.find('meta', property='product:price:amount') or soup.find('meta', property='og:price:amount')
                        if meta_price and meta_price.get('content'):
                            final_price = int(float(meta_price['content'].replace(',', '')))
                            logger.info(f"🎯 쇼핑몰 메타태그에서 가격 적중! {final_price}원")
            except Exception as e:
                logger.debug(f"쇼핑몰 메타 스나이핑 실패: {e}")
                
        # 산술/논리 검증: 원화(KRW) 기준 터무니없이 낮은 가격(예: 100원 미만)은 추출 오류나 적립금액(53원 등)일 확률이 높으므로 0으로 보정
        if currency == "KRW" and final_price > 0 and final_price < 100:
            logger.info(f"⚠️ 가격 비정상 감지 ({final_price}원). 오류 또는 포인트성 숫자로 간주하여 0원으로 초기화.")
            final_price = 0
        # 상한선 검증: 너무 터무니없이 높은 가격(예: 500만원 초과)은 상품 코드가 잘못 파싱된 경우일 확률이 높으므로 0으로 보정
        elif currency == "KRW" and final_price > 5000000:
            logger.info(f"⚠️ 가격 비정상 감지 ({final_price}원). 오류 또는 상품코드로 간주하여 0원으로 초기화.")
            final_price = 0

        price = final_price
        
        # 🚨 스팸 및 공지사항 / 게시판 뻘글 필터링
        spam_keywords = ["공지", "질문", "투패", "몰테일", "폐업", "출고", "지연", "지쟈스", "배대지", "안내", "도와주세요", "어떤가요", "입고금지", "수익링크", "바이럴", "금지조치", "활동내역", "제재조치"]
        if any(keyword in raw_title for keyword in spam_keywords) or (price == 0 and "?" in raw_title):
            logger.info(f"🗑️ [스팸 필터 처리됨] 핫딜이 아닌 게시판 정보 스킵: {raw_title}")
            return None
        
        # 1. 원본 텍스트를 RegexNormalizer에 통과
        normalized = await self.normalizer.normalize(raw_title)
        
        # 스크래퍼 단에서 명시적으로 넘긴 category가 있다면(예: '적립') 그걸 최우선으로 적용
        final_category = scraped_data.get("category")
        if not final_category:
            final_category = normalized.category

        # 적립/포인트 강제 보정 (사용자 요청: 적립 탭으로 분리)
        event_keywords = ["추첨", "설문", "무료배포", "체험단", "선착순", "라이브", "방송", "라방"]
        point_keywords = ["적립", "포인트", "페이백", "앱테크"]
        # 순수 적립이 아닌 구매/조건부 쇼핑을 걸러내기 위한 금지어
        not_point_keywords = ["결제", "구매", "이상", "슈퍼적립", "혜택", "사은품", "증정", "할인"]
        
        check_title = raw_title.replace("드라이브", "")
        
        is_event = any(kw in check_title for kw in event_keywords)
        is_point = False
        
        if any(kw in check_title for kw in point_keywords) and not any(kw in check_title for kw in not_point_keywords):
            # 사용자가 명시한 대로, "적립" 카테고리는 100원 이하(또는 0원)일 때만 순수 적립(앱테크)으로 취급
            if price <= 100:
                is_point = True
        
        # '적립'이나 '페이백'이 있어도 일반적인 라이브 방송 상품 판매 예고면 포인트(적립)가 아니라 이벤트로 처리
        # 단, 가격이 100원 이하인 소액 적립(라이브 시청 보상 등)은 예외로 '적립' 유지
        if is_point and any(kw in check_title for kw in ["라이브", "예고", "방송", "라방"]):
            if price > 100:
                is_point = False
                is_event = True
            
        if not is_event and "예고" in check_title:
            if price <= 1000:
                is_event = True
                
        if "무료" in check_title and "배송" not in check_title and "무배" not in check_title and "택배" not in check_title:
            if price == 0:
                is_event = True
            
        # 루리웹, 퀘이사존 등에서 걸러진 적립 또는 이벤트로 뺌
        if is_point:
            final_category = "적립"
        elif is_event:
            final_category = "이벤트"

        # [Phase 12] 실제 게시글 작성 시간 반영 (스크래핑 시점이 아닌 실제 업로드 시점)
        posted_at_iso = scraped_data.get("posted_at")
        posted_dt = None
        if posted_at_iso:
            try:
                posted_dt = datetime.fromisoformat(posted_at_iso)
            except Exception:
                pass

        content_html = scraped_data.get("content_html", scraped_data.get("content", ""))

        # 2. URL 중복 체크 (Upsert 로직의 핵심)
        existing_deals = self.db.query(Deal).filter(Deal.post_link == url).all()
        
        if existing_deals:
             # 이미 수집했던 글이라면 가격 등 메타정보 갱신 (Upsert)
             # 만약 AI가 다중 분할한 상품들이라면, 모든 split deal에 대해 종료 상태 등을 일괄 갱신합니다.
             for existing_deal in existing_deals:
                 price_changed = False
                 
                 # 제목은 원래 파생된 제목을 유지해야 하므로, 단일 상품일 때만 원본 제목으로 갱신
                 if len(existing_deals) == 1 and existing_deal.title != raw_title:
                     existing_deal.title = raw_title
                     
                 # 가격 갱신도 단일 상품이거나 가격 변동이 명확할 때만 (분할된 가격은 덮어쓰지 않도록 주의)
                 if len(existing_deals) == 1 and str(existing_deal.price) != str(price) and price != 0:
                     existing_deal.price = str(price)
                     price_changed = True
                     
                 if image_url and not existing_deal.image_url:
                     existing_deal.image_url = image_url
                     
                 if scraped_data.get("ecommerce_link") and not existing_deal.ecommerce_link:
                     existing_deal.ecommerce_link = scraped_data.get("ecommerce_link")
                 
                 if content_html and not existing_deal.content_html:
                     existing_deal.content_html = content_html
                 
                 # 적립/카테고리 강제 보정 적용
                 if existing_deal.category != final_category and final_category:
                     existing_deal.category = final_category

                 if getattr(existing_deal, 'currency', None) != currency:
                     existing_deal.currency = currency

                 existing_deal.is_closed = is_closed
                 existing_deal.shipping_fee = shipping_fee
                 
                 # 지표 업데이트 (Continuous Upsert)
                 view_count = int(scraped_data.get("view_count", 0))
                 like_count = int(scraped_data.get("like_count", 0))
                 comment_count = int(scraped_data.get("comment_count", 0))
                 
                 if view_count > (existing_deal.view_count or 0):
                     existing_deal.view_count = view_count
                 if like_count > (existing_deal.like_count or 0):
                     existing_deal.like_count = like_count
                 if comment_count > (existing_deal.comment_count or 0):
                     existing_deal.comment_count = comment_count
                 
                 # 동적 꿀딜 점수 갱신
                 calc_score = int((existing_deal.view_count / 100) + (existing_deal.like_count * 10) + (existing_deal.comment_count * 5))
                 if scraped_data.get("is_super_hotdeal"):
                     calc_score = 100
                 existing_deal.honey_score = min(100, max(calc_score, existing_deal.honey_score or 0))
                 
                 if posted_dt:
                     existing_deal.indexed_at = posted_dt
                     
                 if price_changed and price > 0:
                     self._insert_price_history(existing_deal.id, price)

             logger.debug(f"🔄 기존 Deal 가격/상태 업데이트 (Upsert) - 총 {len(existing_deals)}개 항목: {url}")
             self.db.commit()
             
             return existing_deals[0]

        ai_summary = None
        
        # 동적 꿀딜 점수 초기 계산
        view_count = int(scraped_data.get("view_count", 0))
        like_count = int(scraped_data.get("like_count", 0))
        comment_count = int(scraped_data.get("comment_count", 0))
        
        honey_score = int((view_count / 100) + (like_count * 10) + (comment_count * 5))
        if honey_score < 50 and price > 0:
            import random
            honey_score = random.randint(50, 70)  # 최소 점수 보장
        
        honey_score = min(100, honey_score)

        # 커뮤니티 추천수/조회수/인기마크 기반 슈퍼 핫딜 판별
        if scraped_data.get("is_super_hotdeal"):
            honey_score = 100
            # ai_summary가 None이므로 여기서 문자열 처리를 피합니다. 나중에 배치에서 처리.

        # [다중 상품 자동 분할 - Phase 5.1 토큰 최적화 (CEO 피드백)]
        # 1. 제목 기반으로 상품 갯수 추정 (예: 상품명.상품명.상품명 -> 점이 2개면 상품 3개)
        estimated_item_count = raw_title.count(".") + raw_title.count(",") + 1
        # 2. 본문 텍스트 내에서 정규식으로 '원' 단위 숫자 패턴 갯수 추출
        price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+)\s*원', content_html) if content_html else []
        
        is_multi_item = ("(" in raw_title and "다양" in raw_title) or raw_title.count(".") >= 2
        is_missing_price = (price == 0)
        
        # [핵심 로직] 추출된 가격 갯수가 상품 갯수보다 적거나, 스크래퍼가 아예 가격(0)을 못 찾은 경우 AI 가동
        token_saving_trigger = (is_multi_item and (len(price_matches) < estimated_item_count)) or is_missing_price
        
        logger.info(f"DEBUG: price={price}, is_missing_price={is_missing_price}, token_saving_trigger={token_saving_trigger}")
        
        split_items = []
        if token_saving_trigger and (len(content_html) > 50 or raw_title):
            api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
            if not api_key:
                from dotenv import load_dotenv
                # 루트 디렉토리의 .env 로드 시도
                root_env = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), ".env")
                load_dotenv(root_env)
                api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
                
            if api_key:
                import asyncio
                import google.generativeai as genai
                logger.info(f"🤖 가격 누락 또는 모음전 감지! Gemini 2.5로 정밀 파싱 시작... (원제: {raw_title})")
                genai.configure(api_key=api_key)
                model = genai.GenerativeModel('gemini-2.5-flash')
                prompt = f"""
                넌 쇼핑몰 핫딜 데이터 추출 AI야. 다음 게시글 내용(HTML)과 제목을 읽고, 판매 중인 상품의 이름과 가격을 정확히 뽑아서 순수 JSON 배열만 반환해.
                만약 여러 개의 상품이 포함된 벌크 핫딜이면 배열에 여러 객체를 넣고, 단일 상품이면 1개만 넣어. 다른 말은 절대 하지마.
                
                [가격 추출 필수 규칙]
                1. '59요금제' 같은 휴대폰 요금제의 경우 월 요금인 59000 처럼 계산해서 적어줘.
                2. 달러($)나 유로(€) 같은 외화면 대략적인 원화(KRW)로 환산해서 정수로 적어줘.
                3. 본문에 정가(원래 가격)와 할인율(예: 75% SALE)만 명시되어 있다면, 반드시 (정가 * (100 - 할인율) / 100)으로 계산해서 최종 할인된 가격을 정수로 적어. (예: 40000원 75% 할인 -> 10000)
                4. 할인율, 쿠폰가, 청구할인가 등이 명시되어 있으면 무조건 최종 할인가를 적어.
                5. 가격은 반드시 정수형 숫자만 들어가야 하고, 본문에 가격 정보가 아예 없고 완전 무료(나눔 등)인 경우에만 0을 적어.
                만약 본문 텍스트 주변에 이미지 URL 링크가 보이면 각 상품 객체에 'image_url' 속성으로 연결해주고, 없으면 null 처리해.
                만약 텍스트 내에 (링크: http...) 형식으로 각 상품별 스토어 주소가 있다면 추출해서 'ecommerce_link' 속성에 넣어줘. 없으면 null 처리해.
                [양식]: [{{"name": "...", "price": 10000, "image_url": "http...", "ecommerce_link": "http..."}}]
                
                게시글 제목: {raw_title}
                게시글 내용: {content_html[:1500]}
                """
                max_retries = 3
                for attempt in range(max_retries):
                    try:
                        response = await model.generate_content_async(prompt)
                        import json
                        text_resp = response.text.replace("```json", "").replace("```", "").strip()
                        parsed = json.loads(text_resp)
                        if isinstance(parsed, list) and len(parsed) > 0:
                            split_items = parsed
                            logger.info(f"✅ Gemini 자동 분할 성공! {len(split_items)}개 상품 추출됨")
                        break
                    except Exception as e:
                        if "429" in str(e) and attempt < max_retries - 1:
                            wait_time = 15
                            match = re.search(r'retry in ([\d\.]+)s', str(e))
                            if match:
                                wait_time = int(float(match.group(1))) + 2
                            logger.warning(f"Gemini Rate Limit Hit (Split). Waiting {wait_time}s... (Attempt {attempt+1}/{max_retries})")
                            await asyncio.sleep(wait_time)
                        else:
                            logger.error(f"Gemini 분할 실패: {e}")
                            break
            else:
                logger.info("⚠️ API 키 없음. 상품 자동 분리를 건너뜁니다.")
                split_items = []
        inserted_deals = []
        
        if split_items:
            for idx, item in enumerate(split_items):
                # 가격 파싱 에러 방어 (LLM이 "1.1만" 같은 문자열을 반환할 경우)
                try:
                    raw_price = str(item.get("price", "0")).replace(",", "").replace("원", "").strip()
                    if "만" in raw_price:
                        num_part = raw_price.replace("만", "").strip()
                        item_price = int(float(num_part) * 10000)
                    else:
                        item_price = int(float(raw_price)) if raw_price.replace(".", "").isdigit() else 0
                except Exception:
                    item_price = 0
                    
                # 고유 식별을 위해 파생 상품명 조합
                derived_title = f"{raw_title.split(']')[0] + ']' if ']' in raw_title else ''} {item.get('name', '')}"
                
                item_ecommerce_link = item.get("ecommerce_link") or scraped_data.get("ecommerce_link")
                
                try:
                    new_deal = Deal(
                        source_community_id=community_id,
                        title=derived_title[:255],
                        price=str(item_price) if item_price else "0",
                        currency=currency,
                        post_link=url,
                        ecommerce_link=item_ecommerce_link or url,
                        shop_name=shop_name,
                        shipping_fee=shipping_fee,
                        is_closed=is_closed,
                        category=final_category,
                        base_product_name=item.get("name", normalized.name),
                        image_url=item.get("image_url") or (image_url if idx == 0 else ""), # 💡 [CEO 피드백: 다중 이미지가 없으면 빈 문자열 반환, 안드로이드에서 처리]
                        ai_summary=f"✅ {item_price:,}원! AI가 자동 분리해낸 핫딜입니다.\n✅ 분할된 옵션 상품으로 정확한 내용은 본문을 참고하세요.\n✅ 세부 스펙은 상품 페이지를 확인해주세요.",
                        content_html=content_html,
                        honey_score=honey_score,
                        view_count=view_count,
                        like_count=like_count,
                        comment_count=comment_count,
                        indexed_at=posted_dt if posted_dt else func.now()
                    )
                    self.db.add(new_deal)
                    self.db.commit()
                    self.db.refresh(new_deal)
                    if item_price > 0: self._insert_price_history(new_deal.id, item_price)
                    inserted_deals.append(new_deal)
                except Exception as e:
                    self.db.rollback()
                    logger.error(f"Error inserting split item '{derived_title}': {e}")
        else:
            # 단일 등록 (기존 로직)
            try:
                new_deal = Deal(
                    source_community_id=community_id,
                    title=raw_title,
                    price=str(price) if price else "0",
                    currency=currency,
                    post_link=url,
                    ecommerce_link=scraped_data.get("ecommerce_link") or url,
                    shop_name=shop_name,
                    shipping_fee=shipping_fee,
                    is_closed=is_closed,
                    category=final_category,
                    base_product_name=normalized.name,
                    image_url=image_url,
                    ai_summary=ai_summary,
                    content_html=content_html,
                    honey_score=honey_score,
                    view_count=view_count,
                    like_count=like_count,
                    comment_count=comment_count,
                    indexed_at=posted_dt if posted_dt else func.now()
                )
                self.db.add(new_deal)
                self.db.commit()
                self.db.refresh(new_deal)
                if price > 0: self._insert_price_history(new_deal.id, price)
            except Exception as e:
                self.db.rollback()
                logger.error(f"Error inserting deal '{raw_title}': {e}")
                return None
            
            # [Epic 3] 비동기 백그라운드 워커 패턴으로 푸시 알림 발송 위임
            import asyncio
            from concurrent.futures import ThreadPoolExecutor
            
            if not hasattr(self.__class__, '_push_executor'):
                self.__class__._push_executor = ThreadPoolExecutor(max_workers=5)
            
            from backend.services.push_worker import background_trigger_keyword_alarms
            loop = asyncio.get_event_loop()
            loop.run_in_executor(self.__class__._push_executor, background_trigger_keyword_alarms, new_deal.id)
            
            inserted_deals.append(new_deal)
            
        logger.debug(f"🔗 딜 분석 및 DB 병합 완료: 총 {len(inserted_deals)}개 인서트")
        return inserted_deals[0] if inserted_deals else None

    def _insert_price_history(self, deal_id: int, price: int):
        """특정 Deal의 가격 변동(골든타임)을 추적합니다."""
        history = PriceHistory(
            deal_id=deal_id,
            price=str(price)
        )
        self.db.add(history)
        self.db.commit()
        logger.info(f"📈 [Price History] 최저가 역사 기록! Deal ID {deal_id} -> {price:,}원")
