import os
from sqlalchemy import create_engine, text
engine = create_engine('postgresql://insightdeal:password@localhost:5432/insightdeal')
with engine.connect() as conn:
    result = conn.execute(text("SELECT title, post_link, ecommerce_link FROM deals WHERE title ILIKE '%JBL BAR%'"))
    for row in result:
        print(row)
