from database.session import SessionLocal
from database.models import Community

def register_bbasak():
    db = SessionLocal()
    try:
        communities = [
            {"name": "빠삭국내", "url": "https://bbasak.com/bbs/board.php?bo_table=bbasak1"},
            {"name": "빠삭해외", "url": "https://bbasak.com/bbs/board.php?bo_table=bbasak2"}
        ]
        
        for comm_data in communities:
            existing = db.query(Community).filter(Community.name == comm_data["name"]).first()
            if not existing:
                print(f"Registering {comm_data['name']}...")
                new_comm = Community(name=comm_data["name"], base_url=comm_data["url"])
                db.add(new_comm)
            else:
                print(f"{comm_data['name']} already exists. Updating URL...")
                existing.base_url = comm_data["url"]
        
        db.commit()
        print("Bbasak communities registered/updated.")
        
    except Exception as e:
        print(f"Error: {e}")
        db.rollback()
    finally:
        db.close()

if __name__ == "__main__":
    register_bbasak()
