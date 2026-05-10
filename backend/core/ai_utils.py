import os
import random
import logging

logger = logging.getLogger(__name__)

# 영구적으로 한도 초과된 키를 메모리에 기록하여 다시 사용하지 않음
_DEAD_KEYS = set()

def mark_key_dead(api_key):
    """특정 키가 'monthly spending cap' 등에 도달해 더 이상 쓸 수 없을 때 호출"""
    if api_key:
        _DEAD_KEYS.add(api_key)
        logger.warning(f"API Key marked as DEAD. Total dead keys: {len(_DEAD_KEYS)}")

def get_random_gemini_key():
    """
    GEMINI_API_KEYS (콤마 분리) 또는 단일 GEMINI_API_KEY, GOOGLE_API_KEY 중에서
    랜덤하게 하나의 키를 반환합니다. (죽은 키 제외)
    """
    keys = []
    
    # 1. 콤마로 구분된 여러 키 (우선순위)
    keys_str = os.getenv("GEMINI_API_KEYS")
    if keys_str:
        keys.extend([k.strip() for k in keys_str.split(",") if k.strip()])
        
    # 2. 단일 키
    if not keys:
        if os.getenv("GEMINI_API_KEY"):
            keys.append(os.getenv("GEMINI_API_KEY").strip())
        elif os.getenv("GOOGLE_API_KEY"):
            keys.append(os.getenv("GOOGLE_API_KEY").strip())
            
    # 3. 환경변수가 없는 경우 .env 로드 후 재시도
    if not keys:
        from dotenv import load_dotenv
        root_env = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), ".env")
        if os.path.exists(root_env):
            load_dotenv(root_env)
            
            keys_str = os.getenv("GEMINI_API_KEYS")
            if keys_str:
                keys.extend([k.strip() for k in keys_str.split(",") if k.strip()])
            elif os.getenv("GEMINI_API_KEY"):
                keys.append(os.getenv("GEMINI_API_KEY").strip())
            elif os.getenv("GOOGLE_API_KEY"):
                keys.append(os.getenv("GOOGLE_API_KEY").strip())
                
    valid_keys = [k for k in keys if k not in _DEAD_KEYS]
    if not valid_keys:
        logger.warning("No Gemini API key found (or all keys are DEAD).")
        return None
        
    unique_keys = list(set(valid_keys))
    selected = random.choice(unique_keys)
    masked_key = selected[:10] + "..." + selected[-4:] if len(selected) > 15 else "..."
    logger.debug(f"🔑 AI API Key Selected (Rotation): {masked_key}")
    return selected
