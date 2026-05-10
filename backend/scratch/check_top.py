import urllib.request
import json
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

try:
    with urllib.request.urlopen('http://localhost:8000/api/community/top-hot-deals') as response:
        data = json.loads(response.read().decode('utf-8'))
        deals = data.get('deals', [])
        print(f'Total deals: {len(deals)}')
        for d in deals:
            summary = d.get('ai_summary') or ''
            summary = summary.replace('\U0001f525', '[FIRE]')
            if '[FIRE]' in summary:
                print(f"ID: {d['id']}, created_at: {d.get('created_at')}, ai_summary: {summary[:30]}")
            elif d['id'] == 3560:
                print(f"ID 3560 found but no fire! summary: {summary}")
except Exception as e:
    print(e)
