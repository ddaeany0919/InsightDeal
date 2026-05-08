import sys
import os
from sqlalchemy.orm import Session
from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.services.aggregator_service import AggregatorService
import datetime

# Ensure firebase is initialized
from backend.core.notifications import notification_service
if not notification_service.is_available():
    print("Firebase is not initialized or available.")

def trigger_test_deal(keyword: str):
    db = SessionLocal()
    try:
        # Get the latest deal from the DB to ensure a valid deal_id exists for deep linking
        latest_deal = db.query(Deal).order_by(Deal.id.desc()).first()
        if not latest_deal:
            print("No deals found in the database. Please run a scraper first.")
            return

        print(f"Triggering alarm for keyword: {keyword} with real Deal ID: {latest_deal.id}")
        
        # Override the title to trigger the alarm based on the test keyword
        original_title = latest_deal.title
        latest_deal.title = f"[테스트] {keyword} 핫딜 알림!"
        
        from backend.services.push_worker import background_trigger_keyword_alarms
        
        # Trigger alarm
        db.commit() # Ensure it's reachable by worker if needed
        background_trigger_keyword_alarms(latest_deal.id)
        latest_deal.title = original_title
        
        print("Alarm trigger process finished. Check device!")
        
    finally:
        db.close()

if __name__ == "__main__":
    kw = sys.argv[1] if len(sys.argv) > 1 else "아이패드"
    trigger_test_deal(kw)
