import urllib.request
import json

def get_grouped_deals():
    try:
        req = urllib.request.Request('http://localhost:8000/api/community/hot-deals?limit=200')
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode('utf-8'))
            deals = data.get('deals', [])
            print(f"Total deals from API: {len(deals)}")
            
            count = 0
            for deal in deals:
                sources = deal.get('sources', [])
                if len(sources) > 1:
                    count += 1
                    print(f"====================================")
                    print(f"Grouped Deal ID: {deal.get('id')}")
                    print(f"Title: {deal.get('title')}")
                    print(f"Community Name: {deal.get('community_name')}")
                    print(f"Sources:")
                    for s in sources:
                        print(f"  - {s.get('site_name')}: {s.get('price')}원")
            print(f"Found {count} grouped deals.")
    except Exception as e:
        print(f"Error calling API: {e}")

if __name__ == "__main__":
    get_grouped_deals()
