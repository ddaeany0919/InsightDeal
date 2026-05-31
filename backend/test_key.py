import google.generativeai as genai
import os
from dotenv import load_dotenv

# session.py 경로 기준으로 두 단계 위로 가서 .env 로드
load_dotenv(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env"))

key = "AIzaSyDBicmjvaaPrwOcs1tuQD65m77bRD4pLg0"
print(f"Testing key: {key}")
try:
    genai.configure(api_key=key)
    model = genai.GenerativeModel("gemini-1.5-flash")
    response = model.generate_content("hello")
    print("SUCCESS:", response.text)
except Exception as e:
    print("FAILED:", e)
