import asyncio, json, os, google.generativeai as genai

genai.configure(api_key='AIzaSyDBicmjvaaPrwOcs1tuQD65m77bRD4pLg0')
model = genai.GenerativeModel('gemini-pro')

prompt = """넌 쇼핑몰 핫딜 데이터 추출 AI야. 다음 게시글 내용(HTML)과 제목을 읽고, 판매 중인 상품의 이름과 가격을 정확히 뽑아서 순수 JSON 배열만 반환해. 만약 여러 개의 상품이 포함된 벌크 핫딜이면 배열에 여러 객체를 넣고, 단일 상품이면 1개만 넣어.
[양식]: [{"name": "...", "price": 10000, "image_url": "http...", "shipping_fee": "무료배송", "ecommerce_link": "http...", "ai_summary": "옵션별 요약 내용..."}]
"""

html = open('backend/scratch/qz.txt', encoding='utf-8').read()
title = '[알리] 파이어밧 K10 PRO등 파이어밧 미니PC 10.61만 4G 128G'

async def main():
    resp = await model.generate_content_async([prompt, '제목: ' + title, '본문: ' + html[:15000]])
    print(resp.text)

asyncio.run(main())
