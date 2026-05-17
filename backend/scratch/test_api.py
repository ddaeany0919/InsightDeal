import urllib.request, json
import sys, codecs
sys.stdout = codecs.getwriter('utf-8')(sys.stdout.detach())

try:
    with urllib.request.urlopen('http://localhost:8000/api/community/top-hot-deals') as response:
        data = json.loads(response.read().decode())
        deals = data.get("deals", [])
        print(f"Got {len(deals)} deals")
        for d in deals[:10]:
            print(f"- {d.get('title')} ({d.get('category')}) score: {d.get('honey_score')} is_closed: {d.get('is_closed')}")
except Exception as e:
    print('Server might be down:', e)
