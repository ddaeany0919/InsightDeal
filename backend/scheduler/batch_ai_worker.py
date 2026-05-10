import os
import json
import logging
import time
import re
import sys
import google.generativeai as genai

# 환경 셋업
root_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if root_dir not in sys.path:
    sys.path.append(root_dir)

from dotenv import load_dotenv
load_dotenv(os.path.join(root_dir, ".env"))

from backend.database.session import SessionLocal
from backend.database.models import Deal

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def process_ai_batch():
    db = SessionLocal()
    try:
        # ai_summary가 비어있는 항목을 15개씩 묶어서 처리
        deals_to_process = db.query(Deal).filter(
            Deal.ai_summary == None, 
            Deal.price != '0', 
            Deal.price != None
        ).limit(15).all()
        
        if not deals_to_process:
            logger.info("🎉 처리가 필요한 AI 요약 항목이 없습니다.")
            return 0

        logger.info(f"🚀 {len(deals_to_process)}개의 항목에 대해 일괄 AI 분석을 시작합니다...")
        
        # JSON 입력 배열 구성
        input_data = []
        for d in deals_to_process:
            try:
                price_val = float(d.price)
                formatted_price = f"${price_val / 100:.2f}" if d.currency == "USD" else f"€{price_val / 100:.2f}" if d.currency == "EUR" else f"{int(price_val):,}원"
            except:
                formatted_price = "가격미상"

            content_preview = d.content_html[:400] if d.content_html else ""
            input_data.append({
                "id": d.id,
                "title": d.title,
                "price": formatted_price,
                "content": content_preview,
                "is_super_hotdeal": d.honey_score == 100
            })
            
        prompt = f"""
        너는 국내 쇼핑몰 핫딜 분석 전문가야. 아래 JSON 배열로 전달된 {len(input_data)}개의 핫딜 데이터를 분석해서, 각 핫딜에 대해 핵심 스펙과 체감가를 정리하는 아주 솔직하고 전문적인 3줄 요약을 작성해줘.
        
        [규칙]
        1. 반드시 순수 JSON 배열만 반환할 것 (마크다운 백틱 제외).
        2. 각 객체는 입력받은 'id'를 반드시 그대로 유지하고, 'ai_summary' 필드에 3줄 요약을 넣을 것.
        3. 'ai_summary'의 각 줄은 '✅' 이모지로 시작하고 줄바꿈(\\n)으로 구분할 것. (최대 150자 이내)
        4. 만약 is_super_hotdeal 값이 true라면, 요약의 첫 줄 앞에 '🔥 [커뮤니티 인증 핫딜] '을 붙일 것.
        
        [입력 데이터]:
        {json.dumps(input_data, ensure_ascii=False, indent=2)}
        
        [출력 예시]:
        [
          {{"id": 101, "ai_summary": "✅ 역대급 가격...\\n✅ 스펙 대비 훌륭...\\n✅ 구매 전 혜택 확인..."}},
          {{"id": 102, "ai_summary": "🔥 [커뮤니티 인증 핫딜] ✅ 품절 임박...\\n✅ 카드 할인 시...\\n✅ 리뷰 긍정적..."}}
        ]
        """
        
        max_retries = 3
        for attempt in range(max_retries):
            try:
                from backend.core.ai_utils import get_random_gemini_key
                import google.generativeai as genai
                
                api_key = get_random_gemini_key()
                if not api_key:
                    logger.error("GEMINI_API_KEY가 설정되지 않았습니다.")
                    return 0
                    
                genai.configure(api_key=api_key)
                model = genai.GenerativeModel('gemini-flash-latest')
                
                response = model.generate_content(prompt)
                text_resp = response.text.replace("```json", "").replace("```", "").strip()
                parsed = json.loads(text_resp)
                
                # 결과 매핑
                success_count = 0
                for result in parsed:
                    deal_id = result.get("id")
                    summary = result.get("ai_summary")
                    if deal_id and summary:
                        # 매칭되는 Deal 찾아서 업데이트
                        for d in deals_to_process:
                            if d.id == deal_id:
                                d.ai_summary = summary
                                success_count += 1
                                break
                
                db.commit()
                logger.info(f"✅ AI Batch 업데이트 성공: {success_count}/{len(deals_to_process)} 항목 완료")
                return success_count
                
            except Exception as e:
                if "429" in str(e):
                    if "spending cap" in str(e).lower():
                        from backend.core.ai_utils import mark_key_dead
                        mark_key_dead(api_key)
                    if attempt < max_retries - 1:
                        import re
                        wait_time = 1
                        match = re.search(r'retry in ([\d\.]+)s', str(e))
                        if match:
                            wait_time = int(float(match.group(1))) + 2
                        logger.warning(f"Gemini Rate Limit Hit (Batch). Waiting {wait_time}s... (Attempt {attempt+1}/{max_retries})")
                        time.sleep(wait_time)
                        continue
                logger.error(f"AI Batch 처리 중 에러 발생 (Fallback 적용): {e}")
                # API 한도 초과 시 Fallback 요약 적용
                for d in deals_to_process:
                    d.ai_summary = f"✅ (AI 요약 대체) {d.title}\n✅ 가격: {d.price}\n✅ 상세 내용은 본문 참조"
                db.commit()
                return len(deals_to_process)
        
        # 만약 루프를 다 돌았는데도 실패했다면 (이론상 else 블록에서 처리되지만 혹시 모를 대비)
        for d in deals_to_process:
            if not d.ai_summary:
                d.ai_summary = f"✅ (AI 요약 대체) {d.title}\n✅ 가격: {d.price}\n✅ 상세 내용은 본문 참조"
        db.commit()
        return len(deals_to_process)
    finally:
        db.close()

def run_all_batches(max_batches=20):
    """최대 설정된 횟수만큼 배치 처리 실행"""
    total_processed = 0
    for i in range(max_batches):
        db = SessionLocal()
        count = db.query(Deal).filter(Deal.ai_summary == None, Deal.price != '0', Deal.price != None).count()
        db.close()
        
        if count == 0:
            logger.info("모든 요약 처리가 완료되었습니다.")
            break
            
        logger.info(f"배치 라운드 {i+1}/{max_batches} 시작 (남은 항목: {count}개)")
        processed = process_ai_batch()
        if processed == 0:
            logger.warning("진척이 없어 배치를 중단합니다.")
            break
            
        total_processed += processed
        # 과도한 API 호출 방지
        time.sleep(4)
        
    return total_processed

if __name__ == "__main__":
    run_all_batches()
