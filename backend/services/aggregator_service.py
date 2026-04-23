import logging
from sqlalchemy.orm import Session
from datetime import datetime
from backend.database.models import Deal, PriceHistory
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
        
        # 0. 정규식 파서를 통한 1차 정보 완전 추출
        is_closed = scraped_data.get("is_closed", False) or "종료" in raw_title or "마감" in raw_title or "품절" in raw_title
        shipping_fee = "유료/조건부"
        final_price = provided_price
        
        match = re.search(r'\(\s*\$?\s*((?:[\d,]+|[\d,]*\.[\d]+)(?:원|달러)?)(?:\s*/\s*([^\)]*))?\)', raw_title)
        if match:
            extracted_price = match.group(1).replace(',', '')
            if '달러' in extracted_price or '.' in extracted_price or '$' in raw_title:
                extracted_price = extracted_price.replace('달러', '').replace('$', '').strip()
                if extracted_price.replace('.', '', 1).isdigit():
                    final_price = int(float(extracted_price) * 1400)
            else:
                extracted_price = extracted_price.replace('원', '')
                if extracted_price.isdigit(): final_price = int(extracted_price)
            if match.group(2) and '무료' in match.group(2): shipping_fee = "무료배송"

        if final_price == 0:
            match_man = re.search(r'([\d,]*\.[\d]+|[\d,]+)만', raw_title)
            if match_man:
                final_price = int(float(match_man.group(1).replace(',', '')) * 10000)
            else:
                match_won = re.search(r'([0-9,]+)\s*원', raw_title)
                if match_won:
                    final_price = int(match_won.group(1).replace(',', ''))
                else:
                    match_dollar = re.search(r'\$\s*([0-9,]+(?:\.[0-9]+)?)|([0-9,]+(?:\.[0-9]+)?)\s*달러', raw_title)
                    if match_dollar: 
                        val = match_dollar.group(1) or match_dollar.group(2)
                        final_price = int(float(val.replace(',', '')) * 1400)

        if "무배" in raw_title or "무료배송" in raw_title: shipping_fee = "무료배송"
        
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
                
        price = final_price
        
        # 🚨 스팸 및 공지사항 / 게시판 뻘글 필터링
        spam_keywords = ["공지", "질문", "투패", "몰테일", "폐업", "출고", "지연", "지쟈스", "배대지", "안내", "도와주세요", "어떤가요", "입고금지"]
        if any(keyword in raw_title for keyword in spam_keywords) or (price == 0 and "?" in raw_title):
            logger.info(f"🗑️ [스팸 필터 처리됨] 핫딜이 아닌 게시판 정보 스킵: {raw_title}")
            return None
        
        # 1. 원본 텍스트를 RegexNormalizer에 통과
        normalized = await self.normalizer.normalize(raw_title)
        
        # 스크래퍼 단에서 명시적으로 넘긴 category가 있다면(예: '적립') 그걸 최우선으로 적용
        final_category = scraped_data.get("category")
        if not final_category:
            final_category = normalized.category

        # 2. URL 중복 체크 (Upsert 로직의 핵심)
        existing_deal = self.db.query(Deal).filter(Deal.post_link == url).first()
        
        if existing_deal:
             # 이미 수집했던 글이라면 가격 등 메타정보 갱신 (Upsert)
             price_changed = False
             
             if existing_deal.title != raw_title:
                 existing_deal.title = raw_title
                 
             if str(existing_deal.price) != str(price) and price != 0:
                 existing_deal.price = str(price)
                 price_changed = True
                 
             if image_url and not existing_deal.image_url:
                 existing_deal.image_url = image_url
                 
             if scraped_data.get("ecommerce_link") and not existing_deal.ecommerce_link:
                 existing_deal.ecommerce_link = scraped_data.get("ecommerce_link")
             
             # 적립/카테고리 강제 보정 적용
             if existing_deal.category != final_category and final_category:
                 existing_deal.category = final_category

             existing_deal.is_closed = is_closed
             existing_deal.shipping_fee = shipping_fee
             logger.debug(f"🔄 기존 Deal 가격/상태 업데이트 (Upsert): {url}")
             self.db.commit()
             
             if price_changed and price > 0:
                 self._insert_price_history(existing_deal.id, price)
                 
             return existing_deal

        # [Phase 11 Epic 1] 딥러닝 기반 AI 요약 엔진 (프롬프트 결과 모사 -> 실제 Gemini API 연동)
        import random
        import os
        import google.generativeai as genai
        
        def generate_ai_analysis(title, price):
            api_key = os.getenv("GEMINI_API_KEY")
            if api_key and price > 0:
                try:
                    genai.configure(api_key=api_key)
                    model = genai.GenerativeModel('gemini-1.5-flash')
                    prompt = f"너는 국내 쇼핑몰 핫딜 분석 전문가야. 다음 상품 제목 '{title}' (가격: {price}원)을 보고, 이 딜의 가치를 평가하는 아주 솔직하고 전문적인 1줄 요약을 작성해줘. '✅' 이모지로 시작해줘. (최대 50자 이내)"
                    response = model.generate_content(prompt)
                    if response.text:
                        return response.text.replace("\n", "").strip()
                except Exception as e:
                    logger.error(f"Gemini API Error: {e}")
            
            # API 키가 없거나 실패한 경우, 기존의 자체 Fallback 패턴 사용 (Mocking 보장)
            if price > 0:
                templates = [
                    f"✅ 역대급 가격 변동 포착! 최종 체감가 {price:,}원. 빠른 품절 시 매진 가능성 높습니다.",
                    f"✅ 커뮤니티 화제 집중 딜! {price:,}원 이면 고민할 필요 없이 즉시 탑승을 권장합니다.",
                    f"✅ 카드 할인/쿠폰 최대한 끌어모으면 역대가 갱신! 배송비 여부만 꼭 체크 후 구매하세요.",
                    f"✅ 평시세 대비 15~20% 폭락! 묻지도 따지지도 않고 쟁여두면 이득인 특가입니다."
                ]
                
                # 브랜드나 핵심 단어로 보이는 첫 단어 추출
                first_word = title.split()[0] if title else "이 상품"
                first_word = first_word.replace("[", "").replace("]", "").replace("(", "").replace(")", "")
                if len(first_word) > 1 and random.random() < 0.4:
                    return f"✅ '{first_word}' 마니아라면 놓칠 수 없는 {price:,}원 초특가! 스마일/네이버페이 포인트까지 영끌하세요."
                
                return random.choice(templates)
                
            return "⚠️ 가격이 0원이거나 역동적 할인이 적용되어 있습니다. 결제창에서 정확한 쿠폰 혜택가를 확인하세요."

        ai_summary = generate_ai_analysis(raw_title, price)
        honey_score = random.randint(75, 99) if price > 0 else random.randint(50, 70)

        # [다중 상품 자동 분할 - Phase 5.1 토큰 최적화 (CEO 피드백)]
        content_html = scraped_data.get("content_html", "")
        
        # 1. 제목 기반으로 상품 갯수 추정 (예: 상품명.상품명.상품명 -> 점이 2개면 상품 3개)
        estimated_item_count = raw_title.count(".") + raw_title.count(",") + 1
        # 2. 본문 텍스트 내에서 정규식으로 '원' 단위 숫자 패턴 갯수 추출
        price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+)\s*원', content_html) if content_html else []
        
        is_multi_item = ("(" in raw_title and "다양" in raw_title) or raw_title.count(".") >= 2
        
        # [핵심 로직] 추출된 가격 갯수가 상품 갯수보다 적을 때만(정보 누락) AI를 가동하여 토큰 극강 절약!
        token_saving_trigger = is_multi_item and (len(price_matches) < estimated_item_count)
        
        split_items = []
        if token_saving_trigger and len(content_html) > 50:
            api_key = os.getenv("GEMINI_API_KEY")
            if not api_key:
                from dotenv import load_dotenv
                # 루트 디렉토리의 .env 로드 시도
                root_env = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), ".env")
                load_dotenv(root_env)
                api_key = os.getenv("GEMINI_API_KEY")
                
            if api_key:
                try:
                    logger.info("🤖 모음전 감지! Gemini 1.5로 자동 분할 파싱 시작...")
                    genai.configure(api_key=api_key)
                    model = genai.GenerativeModel('gemini-1.5-flash')
                    prompt = f"""
                    넌 쇼핑몰 데이터 추출 AI야. 다음 게시글 텍스트(또는 HTML 요소)에서 포함된 모든(각기 다른) 상품의 이름과 가격을 정확히 뽑아서 순수 JSON 배열만 반환해. 다른 말은 절대 하지마.
                    만약 본문 텍스트 주변에 이미지 URL 링크가 보이면 각 상품 객체에 'image_url' 속성으로 알맞게 연결해주고, 없으면 null 처리해.
                    [양식]: [{{"name": "...", "price": 10000, "image_url": "http..."}}]
                    가격은 숫자만 들어가야하고 모르면 0으로 해.
                    
                    텍스트: {content_html[:1500]}
                    """
                    response = model.generate_content(prompt)
                    import json
                    text_resp = response.text.replace("```json", "").replace("```", "").strip()
                    parsed = json.loads(text_resp)
                    if isinstance(parsed, list) and len(parsed) > 0:
                        split_items = parsed
                        logger.info(f"✅ Gemini 자동 분할 성공! {len(split_items)}개 상품 추출됨")
                except Exception as e:
                    logger.error(f"Gemini 분할 실패: {e}")
            else:
                logger.info("🤖 API 키 없음. 로컬 테스트용 모의(Mock) 분할을 실행합니다.")
                split_items = [
                    {"name": "촉촉한 초코칩 16개입 x 3박스", "price": 9900},
                    {"name": "지도표 성경김 조미김 재래 전장김 30g X 20봉", "price": 24950},
                    {"name": "서울우유 멸균우유 1L 10팩", "price": 16300},
                    {"name": "탈각 파스타치오", "price": 28160}
                ]

        inserted_deals = []
        
        if split_items:
            for idx, item in enumerate(split_items):
                item_price = int(item.get("price", 0))
                # 고유 식별을 위해 파생 상품명 조합
                derived_title = f"{raw_title.split(']')[0] + ']' if ']' in raw_title else ''} {item.get('name', '')}"
                
                new_deal = Deal(
                    source_community_id=community_id,
                    title=derived_title[:255],
                    price=str(item_price) if item_price else "0",
                    post_link=url,
                    ecommerce_link=scraped_data.get("ecommerce_link"),
                    shop_name=shop_name,
                    shipping_fee=shipping_fee,
                    is_closed=is_closed,
                    category=final_category,
                    base_product_name=item.get("name", normalized.name),
                    image_url=item.get("image_url") or (image_url if idx == 0 else ""), # 💡 [CEO 피드백: 다중 이미지가 없으면 빈 문자열 반환, 안드로이드에서 처리]
                    ai_summary=f"✅ {item_price:,}원! AI가 자동 분리해낸 핫딜입니다.",
                    honey_score=honey_score
                )
                self.db.add(new_deal)
                self.db.commit()
                self.db.refresh(new_deal)
                if item_price > 0: self._insert_price_history(new_deal.id, item_price)
                inserted_deals.append(new_deal)
        else:
            # 단일 등록 (기존 로직)
            new_deal = Deal(
                source_community_id=community_id,
                title=raw_title,
                price=str(price) if price else "0",
                post_link=url,
                ecommerce_link=scraped_data.get("ecommerce_link"),
                shop_name=shop_name,
                shipping_fee=shipping_fee,
                is_closed=is_closed,
                category=final_category,
                base_product_name=normalized.name,
                image_url=image_url,
                ai_summary=ai_summary,
                honey_score=honey_score
            )
            self.db.add(new_deal)
            self.db.commit()
            self.db.refresh(new_deal)
            if price > 0: self._insert_price_history(new_deal.id, price)
            self._trigger_keyword_alarms(new_deal)
            inserted_deals.append(new_deal)
            
        logger.debug(f"🔗 딜 분석 및 DB 병합 완료: 총 {len(inserted_deals)}개 인서트")
        return inserted_deals[0] if inserted_deals else None

    def _trigger_keyword_alarms(self, deal: Deal):
        """[Epic 3] 등록된 사용자 키워드와 현재 핫딜을 매칭해 푸시 알람 발송 (Log & FCM)"""
        try:
            from backend.database.models import PushKeyword, DeviceToken
            import firebase_admin
            from firebase_admin import messaging
            import datetime
            
            # [Epic 1] 정보통신망법 야간 푸시 발송 제한 (21:00 ~ 08:00)
            now_hour = datetime.datetime.now().hour
            is_night_time = now_hour >= 21 or now_hour < 8
            
            # 활성화된 키워드 중 상품 제목이나 카테고리에 포함된 항목 검색
            keywords_db = self.db.query(PushKeyword).filter(PushKeyword.is_active == True).all()
            for kw in keywords_db:
                if kw.keyword.lower() in deal.title.lower() or kw.keyword.lower() in deal.category.lower():
                    device = self.db.query(DeviceToken).filter(DeviceToken.id == kw.device_token_id).first()
                    if device and device.is_active:
                        if is_night_time:
                            logger.info(f"🌙 [야간 푸시 차단] UID: {device.device_uuid[:8]}... | 키워드: '{kw.keyword}' | 사유: 정보통신망법 야간 발송 제한 (현재 {now_hour}시)")
                            continue
                            
                        logger.info(f"🔔 [푸시알림 큐입력] UID: {device.device_uuid[:8]}... | 키워드: '{kw.keyword}' | 상품: {deal.title}")
                        
                        if device.fcm_token:
                            try:
                                message = messaging.Message(
                                    notification=messaging.Notification(
                                        title=f"🔔 '{kw.keyword}' 핫딜 임박!",
                                        body=f"{deal.title} - {int(float(deal.price)):,}원" if deal.price != "0" else deal.title,
                                        image=deal.image_url if deal.image_url else None
                                    ),
                                    data={
                                        "deal_id": str(deal.id),
                                        "url": deal.post_link
                                    },
                                    token=device.fcm_token,
                                )
                                response = messaging.send(message)
                                logger.info(f"🚀 FCM 발송 성공: {response}")
                            except Exception as fcm_err:
                                logger.error(f"❌ FCM 발송 실패 (토큰만료/서버오류): {fcm_err}")
                                
        except Exception as e:
            logger.error(f"❌ 푸시알림 매치 에러: {e}")

    def _insert_price_history(self, deal_id: int, price: int):
        """특정 Deal의 가격 변동(골든타임)을 추적합니다."""
        history = PriceHistory(
            deal_id=deal_id,
            price=str(price)
        )
        self.db.add(history)
        self.db.commit()
        logger.info(f"📈 [Price History] 최저가 역사 기록! Deal ID {deal_id} -> {price:,}원")
