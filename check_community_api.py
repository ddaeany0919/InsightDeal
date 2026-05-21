import urllib.request
import json

url = "http://localhost:8000/api/community/hot-deals?limit=200&offset=0"
try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode('utf-8'))
        deals = data.get('deals', [])
        print(f"Total deals scanned: {len(deals)}")
        
        null_site_name = []
        null_currency = []
        null_sources = []
        null_post_url = []
        source_issues = []
        
        for deal in deals:
            deal_id = deal.get('id')
            title = deal.get('title')
            
            if deal.get('site_name') is None:
                null_site_name.append((deal_id, title))
            if deal.get('currency') is None:
                null_currency.append((deal_id, title))
            if deal.get('post_url') is None:
                null_post_url.append((deal_id, title))
                
            sources = deal.get('sources')
            if sources is None:
                null_sources.append((deal_id, title))
            elif isinstance(sources, list):
                for s_idx, source in enumerate(sources):
                    if source.get('site_name') is None or source.get('post_url') is None:
                        source_issues.append((deal_id, title, s_idx, source))
                        
        print("\n=== Validation Results ===")
        print(f"Null site_name count: {len(null_site_name)}")
        for item in null_site_name[:5]:
            print(f"  - ID: {item[0]}, Title: {item[1]}")
            
        print(f"Null currency count: {len(null_currency)}")
        for item in null_currency[:5]:
            print(f"  - ID: {item[0]}, Title: {item[1]}")
            
        print(f"Null post_url count: {len(null_post_url)}")
        for item in null_post_url[:5]:
            print(f"  - ID: {item[0]}, Title: {item[1]}")
            
        print(f"Null sources count: {len(null_sources)}")
        for item in null_sources[:5]:
            print(f"  - ID: {item[0]}, Title: {item[1]}")
            
        print(f"Invalid sources details count: {len(source_issues)}")
        for item in source_issues[:5]:
            print(f"  - ID: {item[0]}, Title: {item[1]}, Source Index: {item[2]}, Val: {item[3]}")
            
except Exception as e:
    print(f"Failed: {e}")


