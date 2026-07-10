import urllib.request
import json
import sys

def get_grouped_deals():
    try:
        req = urllib.request.Request('http://localhost:8080/api/community/top-hot-deals')
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode('utf-8'))
            deals = data.get('deals', [])
            
            count = 0
            for deal in deals:
                sources = deal.get('sources', [])
                if len(sources) > 1:
                    count += 1
                    print(f"[{deal.get('id')}] {deal.get('title')}")
                    for s in sources:
                        print(f"  - {s.get('site_name')}: {s.get('price')}원")
    except Exception as e:
        print(f"Error calling API: {e}")

if __name__ == "__main__":
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    get_grouped_deals()
