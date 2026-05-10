import sqlite3; conn=sqlite3.connect('insight_deal.db'); cursor=conn.cursor(); cursor.execute("DELETE FROM deals WHERE id=4964"); conn.commit()
