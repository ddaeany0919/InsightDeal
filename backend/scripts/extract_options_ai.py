import sys
import os
import asyncio
import json
import logging
from sqlalchemy import or_

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..')))

from backend.database.session import get_db_session
from backend.database.models import Deal
import google.generativeai as genai
from backend.core.ai_utils import get_random_gemini_key

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

async def extract_options_for_deal(deal: Deal, model):
    if not deal.content_html:
        logger.warning(f"Deal {deal.id} has no content_html.")
        return None

    prompt = f"""
    당신은 핫딜 게시글 본문에서 상품명과 가격 옵션들을 추출하는 파서입니다.
    이 게시글은 "모음전"이거나 여러 상품 옵션을 포함하고 있을 수 있습니다.
    본문에서 판매 중인 각 상품의 '옵션명(또는 상품명)'과 '최종 가격(숫자)'를 한화(원) 또는 달러/유로 등으로 찾아 추출하세요.
    
    가격이 달러나 유로 등 외화라면, 숫자로 변환할 때 센트 단위로 환산하지 말고 원래 외화 값 그대로나 100을 곱해서(만약 센트 변환 규칙을 안다면) 반환하세요.
    기본적으로 우리 시스템은 '원화'는 그대로, 'USD/EUR' 등은 * 100 한 정수를 사용합니다. 잘 모르면 숫자만 정확히 적으세요.
    
    게시글 제목: {deal.title}
    게시글 본문 HTML/텍스트:
    {deal.content_html[:1500]}  # 텍스트가 너무 길면 잘릴 수 있으니 상단 1500자 이내에서 옵션을 찾으세요.

    결과는 반드시 다음 JSON 배열 형식으로만 반환하세요:
    [
      {{"name": "옵션명1", "price": 10000}},
      {{"name": "옵션명2", "price": 20000}}
    ]
    만약 여러 옵션이 발견되지 않고 단일 상품이라면 빈 배열 [] 을 반환하세요.
    ```json 이나 다른 텍스트는 절대 포함하지 마세요. 오직 JSON 배열만 출력하세요.
    """
    
    try:
        response = model.generate_content(prompt)
        text = response.text.replace('```json', '').replace('```', '').strip()
        data = json.loads(text)
        if isinstance(data, list) and len(data) > 0:
            return data
        return None
    except Exception as e:
        logger.error(f"Failed to parse options for deal {deal.id}: {e}")
        return None

async def main():
    api_key = get_random_gemini_key()
    if not api_key:
        logger.error("No Gemini API Key found.")
        return

    genai.configure(api_key=api_key)
    model = genai.GenerativeModel('gemini-flash-latest')

    db = next(get_db_session())
    
    # 5080, 미니PC, 모음전 등 확인 요청한 키워드 필터링
    deals = db.query(Deal).filter(
        or_(
            Deal.title.like('%5080%'),
            Deal.title.like('%미니PC%'),
            Deal.title.like('%모음전%')
        ),
        or_(Deal.options_data == None, Deal.options_data == 'null')
    ).order_by(Deal.id.desc()).limit(20).all()

    logger.info(f"Found {len(deals)} deals to re-evaluate for options.")

    for deal in deals:
        logger.info(f"Processing Deal {deal.id}: {deal.title}")
        options = await extract_options_for_deal(deal, model)
        if options:
            logger.info(f" -> Found options: {options}")
            deal.options_data = json.dumps(options, ensure_ascii=False)
            deal.has_options = True
            db.commit()
        else:
            logger.info(f" -> No options found.")
            
if __name__ == "__main__":
    asyncio.run(main())
