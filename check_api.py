import urllib.request
import json

url = "http://localhost:8000/api/community/top-hot-deals"
req = urllib.request.Request(url)
with urllib.request.urlopen(req) as response:
    data = json.loads(response.read().decode('utf-8'))
    with open('api_deals.txt', 'w', encoding='utf-8') as f:
        f.write(f"Total deals: {len(data.get('deals', []))}\n")
        for deal in data.get('deals', []):
            f.write(f"[{deal['site_name']}] {deal['title']} | Score: {deal['honey_score']}\n")
