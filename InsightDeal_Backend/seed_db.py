import database
import models
from sqlalchemy.orm import Session

def seed():
    db: Session = database.SessionLocal()
    communities = [
        models.Community(name="뽐뿌", base_url="https://www.ppomppu.co.kr"),
        models.Community(name="뽐뿌해외", base_url="https://www.ppomppu.co.kr"),
        models.Community(name="알리뽐뿌", base_url="https://www.ppomppu.co.kr"),
        models.Community(name="루리웹", base_url="https://bbs.ruliweb.com"),
        models.Community(name="퀘이사존", base_url="https://quasarzone.com"),
        models.Community(name="펨코", base_url="https://www.fmkorea.com"),
        models.Community(name="클리앙", base_url="https://www.clien.net"),
        models.Community(name="빠삭국내", base_url="https://bbasak.com"),
        models.Community(name="빠삭해외", base_url="https://bbasak.com"),
    ]

    print("🌱 DB에 초기 커뮤니티 데이터를 심는 중입니다...")
    try:
        for comm in communities:
            exists = db.query(models.Community).filter(models.Community.name == comm.name).first()
            if not exists:
                new_comm = models.Community(name=comm.name, base_url=comm.base_url)
                db.add(new_comm)
                db.flush()
                print(f"[+] {comm.name} 추가 (ID: {new_comm.id})")
            else:
                print(f"[=] {comm.name} 이미 존재 (ID: {exists.id})")
        db.commit()
        print("✅ 시딩 완료")
    except Exception as e:
        print("❌ 시딩 중 오류:", e)
        db.rollback()
    finally:
        db.close()

if __name__ == "__main__":
    print("데이터베이스 테이블을 생성합니다...")
    models.Base.metadata.create_all(bind=database.engine)
    seed()