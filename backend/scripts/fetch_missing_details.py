import asyncio
import os
import sys
import random
import re
from datetime import datetime, timedelta

# 환경 셋업
root_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.dirname(root_dir))

from dotenv import load_dotenv
load_dotenv(os.path.join(root_dir, ".env"))

from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.fmkorea_scraper import FmkoreaScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.scrapers.ruliweb_scraper import RuliwebScraper
from backend.scrapers.clien_scraper import ClienScraper
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from backend.scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from backend.scrapers.bbasak_parenting_scraper import BbasakParentingScraper

import google.generativeai as genai

SCRAPER_MAP = {
    'ali_ppomppu': AlippomppuScraper,
    'fmkorea': FmkoreaScraper,
    'quasarzone': QuasarzoneScraper,
    'ruliweb': RuliwebScraper,
    'clien': ClienScraper,
    'ppomppu': PpomppuScraper,
    'bbasak_domestic': BbasakDomesticScraper,
    'bbasak_overseas': BbasakOverseasScraper,
    'bbasak_parenting': BbasakParentingScraper,
}

def generate_ai_analysis(title, price, content):
    api_key = os.getenv("GEMINI_API_KEY")
    if api_key and price > 0:
        try:
            genai.configure(api_key=api_key)
            model = genai.GenerativeModel('gemini-2.5-flash')
            prompt = f"너는 국내 쇼핑몰 핫딜 분석 전문가야. 다음 상품 제목 '{title}' (가격: {price}원)과 본문 내용 '{content[:500]}'을 보고, 핵심 스펙, 특징, 그리고 혜택을 위주로 3줄 요약을 작성해줘. 주의: 과거 가격 데이터가 없으므로 '역대 최저가', '가격 방어선' 같은 확정적인 가격 비교 표현은 절대 사용하지 마. 각 줄은 '✅' 이모지로 시작하고 줄바꿈으로 구분해. (최대 150자 이내)"
            response = model.generate_content(prompt)
            if response.text:
                return response.text.strip()
        except Exception as e:
            print(f"Gemini API Error: {e}")
    
    # Fallback
    if price > 0:
        templates = [
            f"✅ 매력적인 혜택이 포함된 딜! 체감가 {price:,}원 수준.\n✅ 본문의 할인 조건 및 스펙을 꼼꼼히 확인하세요.\n✅ 빠른 품절 시 매진 가능성이 있으니 바로 확인해 보세요.",
            f"✅ 커뮤니티 화제 집중 딜! {price:,}원에 등록되었습니다.\n✅ 스펙 대비 훌륭한 혜택을 자랑합니다.\n✅ 배송비와 카드 추가 할인을 구매 전 체크하세요."
        ]
        first_word = title.split()[0] if title else "이 상품"
        first_word = first_word.replace("[", "").replace("]", "").replace("(", "").replace(")", "")
        if len(first_word) > 1 and random.random() < 0.4:
            return f"✅ '{first_word}' 관심 상품이라면 상세 스펙을 확인해보세요!\n✅ 혜택 적용 시 {price:,}원.\n✅ 혜택이 종료되기 전에 본문을 확인하세요."
        return random.choice(templates)
    return "⚠️ 가격이 0원이거나 역동적 할인이 적용되어 있습니다.\n✅ 본문 내 쿠폰/적립 혜택을 확인하세요.\n✅ 결제창에서 정확한 최종가를 확인하세요."

async def fetch_detail_for_deal(db, deal):
    community_name = deal.community.name
    scraper_cls = SCRAPER_MAP.get(community_name)
    if not scraper_cls:
        print(f"[{deal.id}] 매핑된 스크래퍼가 없습니다: {community_name}")
        return

    print(f"🔍 [{community_name}] 상세 페이지 조회 중: {deal.title}")
    try:
        async with scraper_cls(deal.source_community_id) as scraper:
            detail_info = await scraper.get_detail(deal.post_link)
            
            content_html = detail_info.get("content_html", "")
            is_closed = detail_info.get("is_closed", False)
            shipping_fee = detail_info.get("shipping_fee", "")
            ecommerce_link = detail_info.get("ecommerce_link", "")
            extracted_price = detail_info.get("price", 0)

            # Update missing fields
            if content_html:
                deal.content_html = content_html
            if is_closed:
                deal.is_closed = True
            if shipping_fee and not deal.shipping_fee:
                deal.shipping_fee = shipping_fee
            if ecommerce_link and not deal.ecommerce_link:
                deal.ecommerce_link = ecommerce_link
            
            # Recalculate AI Summary if content exists
            if content_html:
                price_val = int(deal.price) if deal.price else extracted_price
                deal.ai_summary = generate_ai_analysis(deal.title, price_val, content_html)
            
            db.commit()
            print(f"✅ [{community_name}] 상세 페이지 업데이트 완료 및 AI 요약 갱신")
    except Exception as e:
        print(f"❌ [{community_name}] 상세 페이지 조회 실패: {e}")

async def run_missing_details_worker():
    print('🕵️‍♂️ [Track 2] 본문이 비어있는 핫딜 스텔스 수집 시작...')
    db = SessionLocal()
    try:
        # 최근 3일 내의 데이터 중 content_html이 비어있고 종료되지 않은 딜 조회
        three_days_ago = datetime.utcnow() - timedelta(days=3)
        deals = db.query(Deal).filter(
            (Deal.content_html == None) | (Deal.content_html == ""),
            Deal.is_closed == False,
            Deal.indexed_at >= three_days_ago
        ).order_by(Deal.indexed_at.desc()).limit(20).all()
        
        if not deals:
            print("✅ 수집할 상세 페이지가 없습니다.")
            return

        print(f"📌 총 {len(deals)}개의 상세 페이지 수집 예정 (5초 간격)")
        
        for deal in deals:
            await fetch_detail_for_deal(db, deal)
            await asyncio.sleep(5)  # Anti-bot delay
            
    finally:
        db.close()
    print('🏁 [Track 2] 스텔스 수집 완료!')

if __name__ == '__main__':
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(run_missing_details_worker())
