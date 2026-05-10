import asyncio
from dotenv import load_dotenv
load_dotenv('backend/.env')

from backend.database.session import SessionLocal
from backend.scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
import google.generativeai as genai
from backend.core.ai_utils import get_random_gemini_key
import json

async def main():
    db = SessionLocal()
    scraper = PpomppuOverseasScraper(db)
    url = 'https://m.ppomppu.co.kr/new/bbs_view.php?id=ppomppu8&page=1&divpage=15&no=87695'
    target_item = {'title': '남독마이 태국망고 대과 2.5kg (18,429원) 제스프리 골드키위 슈퍼점보과 3.4kg', 'url': url}
    
    async with scraper:
        detail = await scraper.get_detail(url)
        merged = target_item.copy()
        merged.update(detail)
        
        raw_title = merged.get('title', '')
        content_html = str(merged.get('content_html', ''))
        
        genai.configure(api_key=get_random_gemini_key())
        model = genai.GenerativeModel('gemini-2.5-flash')
        
        prompt = f"""
        다음은 핫딜 커뮤니티 게시글의 제목과 본문 내용이야.
        이 글이 여러 상품을 모아서 파는 '모음전'이거나 본문에 여러 가지 옵션(가격이 다른)이 존재한다면, 
        각 상품들을 개별 핫딜로 분리해줘.
        
        1. 무조건 JSON 배열 형식으로만 대답해. (예: [{{...}}, {{...}}])
        2. 반환하는 JSON 필드: 'name' (분리된 상품명), 'price' (숫자형식 가격), 'image_url' (본문 내 이미지 링크 중 해당 상품과 가장 어울리는 것, 없으면 null), 'shipping_fee' (문자열, 모르면 null), 'ai_summary' (해당 옵션에 대한 1~2줄 요약)
        3. 만약 텍스트 내에 (링크: http...) 형식으로 각 상품별 스토어 주소가 있다면 추출해서 'ecommerce_link' 속성에 넣어줘. 없으면 null 처리해.
        
        [양식]: [{{"name": "...", "price": 10000, "image_url": "http...", "shipping_fee": "무료배송", "ecommerce_link": "http...", "ai_summary": "옵션별 요약 내용..."}}]
        
        게시글 제목: {raw_title}
        게시글 내용: {content_html[:2000]}
        """
        
        print("Prompt length:", len(prompt))
        response = await model.generate_content_async(prompt)
        print("AI Output:")
        print(response.text)
        
    db.close()

if __name__ == '__main__':
    import sys
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(main())
