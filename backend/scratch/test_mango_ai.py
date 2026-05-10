import asyncio
from backend.services.aggregator_service import AggregatorService
from backend.database.session import SessionLocal

async def run():
    db = SessionLocal()
    agg_service = AggregatorService(db)
    content_html = '''
    항공직송 남독마이 태국망고 대과 9~10입 2.5kg 
 앱사용가 : 20,635원 
 할인코드 2,000원 : MARKET01 
 최종가 : 18,429원 
 >>>>>> 항공직송 남독마이 태국망고 대과 9~10입 2.5kg 바로가기 (링크: https://ko.aliexpress.com/item/1005010216288046.html)
[첨부된 이미지 링크들]: https://cdn3.ppomppu.co.kr/zboard/data3/2026/0509/20260509185355_8RWkbXhoI7.jpg, https://cdn3.ppomppu.co.kr/zboard/data3/2026/0509/20260509190158_2Tv4isAlJ8.jpg
    '''
    mock_deal = {
        'title': '남독마이 태국망고 대과 2.5kg (18,429원) 제스프리 골드키위 슈퍼점보과 3.4kg',
        'url': 'test_url_123',
        'price': '18,429',
        'image_url': 'https://default.jpg',
        'content_html': content_html,
        'like_count': 0,
        'comment_count': 0,
        'view_count': 0,
        'is_closed': False,
        'deal_type': '일반'
    }
    # Test internal splitting logic
    import os, json, requests
    api_key = os.getenv('GEMINI_API_KEYS').split(',')[1] # just grab a key
    print('Testing AI...')
    prompt = f\"\"\"
    다음은 쇼핑몰 핫딜 게시글의 본문 HTML 내용과 원본 제목입니다.
    여러 상품(모음전)이 포함되어 있다면, 개별 상품별로 분리해서 JSON 배열로 반환해주세요.

    원본 제목: 남독마이 태국망고 대과 2.5kg (18,429원) 제스프리 골드키위 슈퍼점보과 3.4kg
    본문 내용:
    {content_html}

    - JSON 응답은 반드시 다음 형식을 따르세요:
      [
        {{"index": 1, "product_name": "제품명", "price": 1000, "link": "http...", "image_url": "http..."}}
      ]
    - image_url 필드에는 상품의 대표 이미지를 반환하세요.
    - JSON 외에 어떤 텍스트도 출력하지 마세요.
    \"\"\"
    payload = { "contents": [{"parts": [{"text": prompt}]}] }
    headers = { "Content-Type": "application/json" }
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={api_key}"
    response = requests.post(url, headers=headers, data=json.dumps(payload), timeout=15)
    content = response.json()
    try:
        text = content['candidates'][0]['content']['parts'][0]['text']
        print(text)
    except:
        print(content)

    db.close()

import sys
if sys.platform == 'win32':
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
asyncio.run(run())