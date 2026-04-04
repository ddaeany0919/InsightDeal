from database.session import SessionLocal
from database.models import Community

def check_communities():
    db = SessionLocal()
    try:
        communities = db.query(Community).all()
        print("--- Communities in DB ---")
        for c in communities:
            print(f"- ID: {c.id}, Name: {c.name}, URL: {c.base_url}")
    finally:
        db.close()

if __name__ == "__main__":
    check_communities()
