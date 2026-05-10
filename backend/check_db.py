import asyncio
from database import SessionLocal
from models import Deal

def main():
    db = SessionLocal()
    # Get top 5 deals to see what the data looks like
    deals = db.query(Deal).order_by(Deal.id.desc()).limit(10).all()
    for d in deals:
        print(f"ID: {d.id}, Title: {d.title}, Price: {d.price}, Post Link: {d.post_link}, Ecom Link: {d.ecommerce_link}")

if __name__ == "__main__":
    main()
