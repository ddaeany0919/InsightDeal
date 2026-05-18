import sqlite3
import os

corrupted_db = "insight_deal_corrupted.db"
new_db = "insight_deal.db"

if not os.path.exists(corrupted_db):
    print("Corrupted DB not found.")
    exit()

print("Attempting to recover data...")

try:
    conn_old = sqlite3.connect(corrupted_db)
    conn_new = sqlite3.connect(new_db)
    
    cursor_old = conn_old.cursor()
    cursor_new = conn_new.cursor()
    
    # Get all tables
    cursor_old.execute("SELECT name FROM sqlite_master WHERE type='table';")
    tables = cursor_old.fetchall()
    
    for table_name_tuple in tables:
        table_name = table_name_tuple[0]
        if table_name == "sqlite_sequence":
            continue
            
        print(f"Recovering table: {table_name}")
        
        try:
            cursor_old.execute(f"SELECT * FROM {table_name}")
            rows = cursor_old.fetchall()
            
            if not rows:
                continue
                
            # Get column names
            col_names = [description[0] for description in cursor_old.description]
            placeholders = ",".join(["?"] * len(col_names))
            
            # Insert into new DB
            # Use INSERT OR IGNORE to prevent unique constraint errors
            query = f"INSERT OR IGNORE INTO {table_name} ({','.join(col_names)}) VALUES ({placeholders})"
            cursor_new.executemany(query, rows)
            conn_new.commit()
            print(f"Successfully recovered {len(rows)} rows for {table_name}.")
            
        except sqlite3.DatabaseError as e:
            print(f"Failed to read from table {table_name} due to corruption: {e}")
            # Try row by row if fetchall fails
            try:
                cursor_old.execute(f"SELECT * FROM {table_name}")
                recovered_count = 0
                while True:
                    try:
                        row = cursor_old.fetchone()
                        if row is None:
                            break
                        query = f"INSERT OR IGNORE INTO {table_name} ({','.join(col_names)}) VALUES ({placeholders})"
                        cursor_new.execute(query, row)
                        conn_new.commit()
                        recovered_count += 1
                    except sqlite3.DatabaseError:
                        # Skip corrupted row
                        pass
                print(f"Recovered {recovered_count} rows row-by-row for {table_name}.")
            except Exception as e2:
                print(f"Row-by-row recovery also failed for {table_name}: {e2}")

    conn_old.close()
    conn_new.close()
    print("Recovery finished.")
except Exception as e:
    print(f"Error during recovery: {e}")
