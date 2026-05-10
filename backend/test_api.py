import httpx

try:
    resp = httpx.get("http://localhost:8000/api/community/popular-keywords")
    print(resp.status_code)
    print(resp.json())
except Exception as e:
    print(e)
