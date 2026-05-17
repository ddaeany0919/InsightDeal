import urllib.request
import json

print("Testing Deal ID 5856:")
try:
    req = urllib.request.Request('http://localhost:8000/api/community/deals/5856')
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
        print("Sources:", data.get('sources', []))
        print("Price:", data.get('price'))
except Exception as e:
    print("Failed to get deal 5856:", e)
