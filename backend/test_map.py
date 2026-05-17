import sqlite3
import re
from datetime import datetime
conn = sqlite3.connect('backend/insight_deal.db')

recent_deals = conn.execute("SELECT id, price, indexed_at FROM deals WHERE id IN (4984, 5856, 5866)").fetchall()
history = conn.execute("SELECT deal_id, price, checked_at FROM price_history WHERE deal_id IN (4984, 5856, 5866)").fetchall()

result_map = {}
for h in history:
    try:
        p_val = int(re.sub(r'[^\d]', '', str(h[1])))
        if p_val > 0:
            time_key = h[2][:16].replace('-', '.')[5:] # "05.10 07:22"
            if time_key not in result_map or p_val < result_map[time_key]:
                result_map[time_key] = p_val
    except:
        pass

for d in recent_deals:
    try:
        p_val = int(re.sub(r'[^\d]', '', str(d[1])))
        if p_val > 0:
            time_key = d[2][:16].replace('-', '.')[5:] # "05.10 07:22"
            if time_key not in result_map or p_val < result_map[time_key]:
                result_map[time_key] = p_val
    except:
        pass

sorted_times = sorted(result_map.keys())
print("RESULT MAP:", [(t, result_map[t]) for t in sorted_times])
