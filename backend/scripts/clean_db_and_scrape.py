import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from backend.database.session import engine, SessionLocal
from backend.database import models
import subprocess

def reset_and_scrape():
    # 1. Delete old deals
    db = SessionLocal()
    try:
        deleted = db.query(models.Deal).delete()
        db.commit()
        print(f"✅ 기존 레거시 핫딜 데이터 {deleted}건 완벽 삭제 (클렌징 완료)")
    except Exception as e:
        print(f"❌ DB 클렌징 에러: {e}")
        db.rollback()
    finally:
        db.close()

    # 2. Run Scraping script securely 
    # Use python executable to run backend/scheduler/main.py --one-shot
    script_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'scheduler', 'main.py')
    print("🚀 최신 정규식(Regex) 탑재 크롤러 즉시 가동...")
    result = subprocess.run([sys.executable, script_path, '--one-shot'], capture_output=True, text=True)
    
    if result.returncode == 0:
        print("✅ 최신 크롤링 및 DB 적재 완료!")
    else:
        print(f"❌ 크롤링 실패:\n{result.stderr}")

if __name__ == '__main__':
    reset_and_scrape()
