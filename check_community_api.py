import urllib.request
import json

url = "http://localhost:8000/api/community/hot-deals?limit=20&offset=0"
try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode('utf-8'))
        print("Success!")
        print(f"Total deals in response: {len(data.get('deals', []))}")
        print(f"Total: {data.get('total')}")
        if data.get('deals'):
            deal = data['deals'][0]
            print(f"Sample Deal -> ID: {deal.get('id')}, Title: {deal.get('title')}, Category: {deal.get('category')}")
except Exception as e:
    print(f"Failed: {e}")
