import asyncio
import sqlite3
import os
import sys

# 프로젝트 루트를 python path에 추가하여 backend 패키지를 임포트할 수 있도록 함
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.services.normalizer.regex_normalizer import RegexNormalizer

async def reprocess_deals():
    db_path = r"C:\Users\kth00\StudioProjects\InsightDeal\backend\insight_deal.db"
    if not os.path.exists(db_path):
        print(f"Error: DB file not found at {db_path}")
        return

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()

    # 모든 딜 데이터 조회
    cursor.execute("SELECT id, title, category FROM deals")
    deals = cursor.fetchall()
    print(f"Total deals in DB: {len(deals)}")

    normalizer = RegexNormalizer()
    updated_count = 0
    misclassified_fixes = []

    for deal in deals:
        deal_id = deal['id']
        title = deal['title']
        old_category = deal['category']

        # RegexNormalizer를 사용해 카테고리 재분류
        normalized = await normalizer.normalize(title)
        new_category = normalized.category

        if old_category != new_category:
            misclassified_fixes.append((new_category, deal_id, title, old_category))

    print(f"Detected {len(misclassified_fixes)} deals with category changes.")

    # 실제로 업데이트 수행
    if misclassified_fixes:
        print("Starting batch DB update...")
        for new_cat, d_id, title, old_cat in misclassified_fixes[:20]:
            print(f"  [Fix Hint] ID: {d_id} | Old: {old_cat} -> New: {new_cat} | Title: {title}")
        
        if len(misclassified_fixes) > 20:
            print(f"  ... and {len(misclassified_fixes) - 20} more.")

        # 트랜잭션 단위로 업데이트 실행
        try:
            cursor.executemany(
                "UPDATE deals SET category = ? WHERE id = ?",
                [(new_cat, d_id) for new_cat, d_id, _, _ in misclassified_fixes]
            )
            conn.commit()
            print(f" Successfully updated {len(misclassified_fixes)} deals in the database!")
        except Exception as e:
            conn.rollback()
            print(f"❌ Error during DB update: {e}")
    else:
        print(" No mismatch found. DB categories are already up-to-date with the latest normalizer rules!")

    conn.close()

if __name__ == "__main__":
    asyncio.run(reprocess_deals())
