import os
import random
import logging
from typing import Optional

logger = logging.getLogger(__name__)

# 영구적으로 한도 초과된 키를 메모리에 기록하여 다시 사용하지 않음
_DEAD_KEYS = set()
_ROOT_ENV_LOADED = False


def _load_root_env_if_needed() -> None:
    global _ROOT_ENV_LOADED
    if _ROOT_ENV_LOADED:
        return
    from dotenv import load_dotenv
    root_env = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), ".env")
    if os.path.exists(root_env):
        load_dotenv(root_env)
    _ROOT_ENV_LOADED = True


def reset_dead_keys() -> None:
    """테스트 격리를 위해 차단된 API 키 캐시를 초기화합니다."""
    _DEAD_KEYS.clear()


def mark_key_dead(api_key):
    """특정 키가 'monthly spending cap' 또는 '403 leaked' 등에 도달해 더 이상 쓸 수 없을 때 호출"""
    if api_key:
        _DEAD_KEYS.add(api_key)
        logger.warning(f"API Key marked as DEAD. Total dead keys: {len(_DEAD_KEYS)}")


def is_ai_summary_enabled() -> bool:
    """USE_GEMINI_AI=true 또는 AI_SUMMARY_ENABLED=true일 때만 AI 요약 워커를 활성화합니다."""
    for env_name in ("USE_GEMINI_AI", "AI_SUMMARY_ENABLED"):
        if os.getenv(env_name, "").strip().lower() in ("true", "1", "yes"):
            return True
    return False


def get_gemini_summary_model() -> str:
    return (os.getenv("GEMINI_SUMMARY_MODEL") or "gemini-2.5-flash-lite").strip() or "gemini-2.5-flash-lite"


def get_random_gemini_key():
    """
    USE_GEMINI_AI=true 일 때만 GEMINI_API_KEYS (콤마 분리) 또는 단일 GEMINI_API_KEY, GOOGLE_API_KEY 중에서
    랜덤하게 하나의 키를 반환합니다. (죽은 키 제외, 기본값은 로컬 엔진 100% 가동)
    """
    if not is_ai_summary_enabled():
        return None

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
        _load_root_env_if_needed()
        keys_str = os.getenv("GEMINI_API_KEYS")
        if keys_str:
            keys.extend([k.strip() for k in keys_str.split(",") if k.strip()])
        elif os.getenv("GEMINI_API_KEY"):
            keys.append(os.getenv("GEMINI_API_KEY").strip())
        elif os.getenv("GOOGLE_API_KEY"):
            keys.append(os.getenv("GOOGLE_API_KEY").strip())

    valid_keys = [k for k in keys if k not in _DEAD_KEYS]
    if not valid_keys:
        return None

    unique_keys = list(set(valid_keys))
    selected = random.choice(unique_keys)
    masked_key = selected[:10] + "..." + selected[-4:] if len(selected) > 15 else "..."
    logger.debug(f"🔑 AI API Key Selected (Rotation): {masked_key}")
    return selected


def get_openai_compatible_config() -> Optional[dict]:
    """
    Groq 무료 티어 또는 OpenAI 호환 엔드포인트(LM Studio http://localhost:1234/v1 등) 설정을 반환합니다.
    """
    if not is_ai_summary_enabled():
        return None

    base_url = (os.getenv("LLM_BASE_URL") or "").strip().rstrip("/")
    groq_key = (os.getenv("GROQ_API_KEY") or "").strip()
    llm_key = (os.getenv("LLM_API_KEY") or "").strip()

    if base_url:
        api_key = llm_key or groq_key or "lm-studio"
        if api_key in _DEAD_KEYS:
            return None
        default_model = "llama-3.1-8b-instant" if "groq.com" in base_url.lower() else "local-model"
        model = (os.getenv("LLM_MODEL") or os.getenv("GROQ_MODEL") or default_model).strip() or default_model
        provider = "groq" if "groq.com" in base_url.lower() else "openai_compatible"
        return {
            "provider": provider,
            "base_url": base_url,
            "api_key": api_key,
            "model": model,
        }

    if groq_key and groq_key not in _DEAD_KEYS:
        model = (os.getenv("GROQ_MODEL") or os.getenv("LLM_MODEL") or "llama-3.1-8b-instant").strip()
        return {
            "provider": "groq",
            "base_url": "https://api.groq.com/openai/v1",
            "api_key": groq_key,
            "model": model or "llama-3.1-8b-instant",
        }

    return None


def get_cloudflare_ai_config() -> Optional[dict]:
    """Cloudflare Workers AI 무료 티어 설정을 반환합니다."""
    if not is_ai_summary_enabled():
        return None

    account_id = (os.getenv("CLOUDFLARE_ACCOUNT_ID") or "").strip()
    api_token = (os.getenv("CLOUDFLARE_AI_API_TOKEN") or "").strip()
    if not account_id or not api_token or api_token in _DEAD_KEYS:
        return None

    model = (os.getenv("CLOUDFLARE_AI_MODEL") or "@cf/meta/llama-3.1-8b-instruct-fast").strip()
    return {
        "provider": "cloudflare_ai",
        "account_id": account_id,
        "api_token": api_token,
        "model": model or "@cf/meta/llama-3.1-8b-instruct-fast",
    }
