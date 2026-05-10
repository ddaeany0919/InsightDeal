import sqlite3

try:
    conn = sqlite3.connect('backend/insight_deal.db')
    c = conn.cursor()
    c.execute("UPDATE deals SET honey_score = 99 WHERE honey_score >= 100 AND (ai_summary IS NULL OR ai_summary NOT LIKE '%인증 핫딜%')")
    conn.commit()
    print(f"{c.rowcount} rows updated.")
except Exception as e:
    print(f"Error: {e}")
finally:
    if conn:
        conn.close()
