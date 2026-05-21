import os
import sys

# Add backend directory to path
sys.path.append(os.path.join(os.path.dirname(__file__), "backend"))

from backend.database.session import SessionLocal
from backend.database import models

def main():
    db = SessionLocal()
    try:
        # Check deals count
        total_deals = db.query(models.Deal).count()
        print(f"Total Deals in DB: {total_deals}")
        
        # Check deals with honey_score >= 100
        high_score_deals = db.query(models.Deal).filter(models.Deal.honey_score >= 100).all()
        print(f"Deals with honey_score >= 100: {len(high_score_deals)}")
        for i, deal in enumerate(high_score_deals[:5]):
            print(f"[{i+1}] ID: {deal.id}, Title: {deal.title}, HoneyScore: {deal.honey_score}, AI Summary: {deal.ai_summary}")
            
        # Check deals with honey_score >= 50
        medium_score_deals = db.query(models.Deal).filter(models.Deal.honey_score >= 50).all()
        print(f"Deals with honey_score >= 50: {len(medium_score_deals)}")
        
        # Check max honey_score in DB
        max_score = db.query(models.Deal).order_by(models.Deal.honey_score.desc()).first()
        if max_score:
            print(f"Max HoneyScore in DB: {max_score.honey_score} (ID: {max_score.id}, Title: {max_score.title})")
        else:
            print("No deals in DB to check max score.")
            
    except Exception as e:
        print(f"Error querying DB: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    main()
