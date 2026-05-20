import sqlite3
import os

def migrate():
    # 백엔드 디렉토리 경로 계산
    backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    db_path = os.path.join(backend_dir, "insight_deal.db")
    
    print(f"[INFO] SQLite Migration Start: {db_path}")
    
    if not os.path.exists(db_path):
        print("[WARNING] Database file does not exist. session.py will generate a new one automatically.")
        return
        
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # 1. deals 테이블 컬럼 추가
    cursor.execute("PRAGMA table_info(deals)")
    deal_columns = [col[1] for col in cursor.fetchall()]
    
    if "brand" not in deal_columns:
        print("[ADD] Adding 'brand' column to deals table...")
        cursor.execute("ALTER TABLE deals ADD COLUMN brand VARCHAR(100)")
        cursor.execute("CREATE INDEX IF NOT EXISTS ix_deals_brand ON deals (brand)")
    else:
        print("[INFO] 'brand' column already exists in deals table.")
        
    if "model_code" not in deal_columns:
        print("[ADD] Adding 'model_code' column to deals table...")
        cursor.execute("ALTER TABLE deals ADD COLUMN model_code VARCHAR(100)")
        cursor.execute("CREATE INDEX IF NOT EXISTS ix_deals_model_code ON deals (model_code)")
    else:
        print("[INFO] 'model_code' column already exists in deals table.")
        
    # 2. device_tokens 테이블 컬럼 추가
    cursor.execute("PRAGMA table_info(device_tokens)")
    token_columns = [col[1] for col in cursor.fetchall()]
    
    if "dnd_enabled" not in token_columns:
        print("[ADD] Adding 'dnd_enabled' column to device_tokens table...")
        cursor.execute("ALTER TABLE device_tokens ADD COLUMN dnd_enabled BOOLEAN NOT NULL DEFAULT 0")
    else:
        print("[INFO] 'dnd_enabled' column already exists in device_tokens table.")
        
    if "dnd_start_time" not in token_columns:
        print("[ADD] Adding 'dnd_start_time' column to device_tokens table...")
        cursor.execute("ALTER TABLE device_tokens ADD COLUMN dnd_start_time VARCHAR(5) NOT NULL DEFAULT '21:00'")
    else:
        print("[INFO] 'dnd_start_time' column already exists in device_tokens table.")
        
    if "dnd_end_time" not in token_columns:
        print("[ADD] Adding 'dnd_end_time' column to device_tokens table...")
        cursor.execute("ALTER TABLE device_tokens ADD COLUMN dnd_end_time VARCHAR(5) NOT NULL DEFAULT '08:00'")
    else:
        print("[INFO] 'dnd_end_time' column already exists in device_tokens table.")
        
    conn.commit()
    conn.close()
    print("[SUCCESS] SQLite Migration completed successfully!")

if __name__ == "__main__":
    migrate()
