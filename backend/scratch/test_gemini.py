import os, sys, json
import google.generativeai as genai

sys.path.append(os.path.abspath('.'))

api_key = None
try:
    with open('.env', 'r', encoding='utf-8') as f:
        for line in f:
            if line.startswith('GEMINI_API_KEY='):
                api_key = line.split('=', 1)[1].strip().strip('\"\'')
            elif line.startswith('GOOGLE_API_KEY=') and not api_key:
                api_key = line.split('=', 1)[1].strip().strip('\"\'')
except Exception as e:
    print('Failed to read .env:', e)

if not api_key:
    print('No API Key found')
    sys.exit(1)

genai.configure(api_key=api_key)
model = genai.GenerativeModel('gemini-2.5-flash')

raw_title = "남독마이 태국망고 대과 2.5kg (18,429원) 제스프리 골드키위 슈퍼점보과 3.4kg (27,206원) / 무료"
content_html = """
[알리뽐뿌] MARKET01 MARKET02 프로모션 코드 확인하세요. 
할인 적용가
1. 남독마이 태국망고 대과 2.5kg: 18,429원
2. 제스프리 골드키위 슈퍼점보과 3.4kg: 27,206원
코인 할인 10% 추가 적용 가능.
택배비는 모두 무료배송입니다.

[첨부된 이미지 링크들]: https://image.ppomppu.co.kr/mango_box.jpg, https://image.ppomppu.co.kr/kiwi_box.jpg
"""

prompt = f"""
넌 쇼핑몰 핫딜 데이터 추출 AI야. 다음 게시글 내용(HTML)과 제목을 읽고, 판매 중인 상품의 이름과 가격을 정확히 뽑아서 순수 JSON 배열만 반환해.
만약 여러 개의 상품이 포함된 벌크 핫딜이면 배열에 여러 객체를 넣고, 단일 상품이면 1개만 넣어. 다른 말은 절대 하지마.

🚨 [아주 중요한 단일화 예외 규칙] 🚨
만약 게시글 본문에 각 상품별 구매 링크가 개별적으로 존재하지 않고, 오직 1개의 대표 구매 링크만 존재하는 '모음전(옵션 선택형)'이라면, 절대 상품을 여러 개로 분리하지 마! 
무조건 전체를 대표하는 이름으로 단일 객체(1개)만 반환해. (예: "무파마 삼겹살 외 14종 모음전")
대신 이 경우, 포함된 세부 상품들의 목록(마니커, 하림 등)이나 특징을 'ai_summary' 속성에 3줄 요약으로 보기 좋게 작성해줘.

[가격 추출 필수 규칙]
1. '59요금제' 같은 휴대폰 요금제의 경우 월 요금인 59000 처럼 계산해서 적어줘.
2. 달러($)나 유로(€) 같은 외화면 대략적인 원화(KRW)로 환산해서 정수로 적어줘.
3. 본문에 정가(원래 가격)와 할인율(예: 75% SALE)만 명시되어 있다면, 반드시 (정가 * (100 - 할인율) / 100)으로 계산해서 최종 할인된 가격을 정수로 적어. (예: 40000원 75% 할인 -> 10000)
4. 할인율, 쿠폰가, 청구할인가 등이 명시되어 있으면 무조건 최종 할인가를 적어.
5. 가격은 반드시 정수형 숫자만 들어가야 하고, 본문에 가격 정보가 아예 없고 완전 무료(나눔 등)이거나, 옵션별로 가격이 다양해서 알 수 없으면 0을 적어.
6. 💡 [중요] 여러 상품이 병렬로 나열되고 괄호 안에 개별 가격이 있다면(예: 키위 (27000원)), 각 분할된 객체의 'price'에 해당 개별 금액을 정확히 추출해 매핑해.

[다중 옵션 및 메타정보 매핑 규칙]
1. [이미지 매핑]: 본문 끝 `[첨부된 이미지 링크들]`을 꼼꼼히 분석해, 분리된 각 옵션(예: 망고, 키위)과 문맥상 가장 매칭되는 이미지 URL을 찾아 각 객체의 'image_url'에 연결해. 매칭 안되면 null 처리. 🚨절대 부모(대표) 이미지를 모든 분할 객체에 똑같이 복사하지 마!
2. [배송비]: 원문 제목 끝의 '/ 무료' 혹은 본문의 배송비 정보를 파악해, 모든 생성된 객체의 'shipping_fee' 속성에 '무료배송' 등 값을 일괄 복사해 넣어줘.
3. 만약 텍스트 내에 (링크: http...) 형식으로 각 상품별 스토어 주소가 있다면 추출해서 'ecommerce_link' 속성에 넣어줘. 없으면 null 처리해.

[양식]: [{{"name": "...", "price": 10000, "image_url": "http...", "shipping_fee": "무료배송", "ecommerce_link": "http...", "ai_summary": "옵션별 요약 내용..."}}]

게시글 제목: {raw_title}
게시글 내용: {content_html[:2000]}
"""

response = model.generate_content(prompt)
print("--- GEMINI OUTPUT ---")
print(response.text)
