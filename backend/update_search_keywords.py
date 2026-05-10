import sqlite3
import re
import os

CHOSUNG_LIST = ['ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ']

# 한글 -> 영타 매핑 (초성, 중성, 종성)
ENG_KEY_MAP = {
    'ㄱ': 'r', 'ㄲ': 'R', 'ㄴ': 's', 'ㄷ': 'e', 'ㄸ': 'E', 'ㄹ': 'f', 'ㅁ': 'a', 'ㅂ': 'q', 'ㅃ': 'Q', 'ㅅ': 't',
    'ㅆ': 'T', 'ㅇ': 'd', 'ㅈ': 'w', 'ㅉ': 'W', 'ㅊ': 'c', 'ㅋ': 'z', 'ㅌ': 'x', 'ㅍ': 'v', 'ㅎ': 'g',
    'ㅏ': 'k', 'ㅐ': 'o', 'ㅑ': 'i', 'ㅒ': 'O', 'ㅓ': 'j', 'ㅔ': 'p', 'ㅕ': 'u', 'ㅖ': 'P', 'ㅗ': 'h', 'ㅘ': 'hk',
    'ㅙ': 'ho', 'ㅚ': 'hl', 'ㅛ': 'y', 'ㅜ': 'n', 'ㅝ': 'nj', 'ㅞ': 'np', 'ㅟ': 'nl', 'ㅠ': 'b', 'ㅡ': 'm', 'ㅢ': 'ml', 'ㅣ': 'l',
    'ㄳ': 'rt', 'ㄵ': 'sw', 'ㄶ': 'sg', 'ㄺ': 'fr', 'ㄻ': 'fa', 'ㄼ': 'fq', 'ㄽ': 'ft', 'ㄾ': 'fx', 'ㄿ': 'fv', 'ㅀ': 'fg', 'ㅄ': 'qt'
}

def decompose_hangul(char):
    if not ('가' <= char <= '힣'):
        return None
    char_code = ord(char) - ord('가')
    jong = char_code % 28
    jung = ((char_code - jong) // 28) % 21
    cho = ((char_code - jong) // 28) // 21
    
    # 초성
    cho_char = CHOSUNG_LIST[cho]
    
    # 중성
    jung_list = ['ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ']
    jung_char = jung_list[jung]
    
    # 종성
    jong_list = ['', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ']
    jong_char = jong_list[jong] if jong > 0 else ''
    
    return cho_char, jung_char, jong_char

def extract_chosung(text: str) -> str:
    result = ""
    for char in text:
        if '가' <= char <= '힣':
            char_code = ord(char) - ord('가')
            cho = char_code // 588
            result += CHOSUNG_LIST[cho]
        else:
            result += char
    return result

def to_eng_typo(text: str) -> str:
    result = ""
    for char in text:
        if '가' <= char <= '힣':
            cho, jung, jong = decompose_hangul(char)
            result += ENG_KEY_MAP.get(cho, cho)
            result += ENG_KEY_MAP.get(jung, jung)
            if jong:
                result += ENG_KEY_MAP.get(jong, jong)
        elif char in ENG_KEY_MAP:
            result += ENG_KEY_MAP[char]
        else:
            result += char
    return result

def main():
    db_path = '/backend/insight_deal.db'
    if not os.path.exists(db_path):
        # Fallback to local path for testing outside docker
        db_path = 'insight_deal.db'
        
    print(f"Using DB path: {db_path}")
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # Add column if it doesn't exist
    try:
        cursor.execute("ALTER TABLE deals ADD COLUMN search_keywords TEXT")
        print("Added search_keywords column.")
    except sqlite3.OperationalError as e:
        if "duplicate column name" in str(e).lower():
            print("search_keywords column already exists.")
        else:
            print(f"Error adding column: {e}")
            
    # Fetch all deals
    cursor.execute("SELECT id, title FROM deals WHERE search_keywords IS NULL OR search_keywords = ''")
    rows = cursor.fetchall()
    
    print(f"Found {len(rows)} deals to update.")
    
    for row_id, title in rows:
        if not title:
            continue
            
        chosung = extract_chosung(title)
        eng_typo = to_eng_typo(title)
        
        search_keywords = f"{title} | {chosung} | {eng_typo}"
        cursor.execute("UPDATE deals SET search_keywords = ? WHERE id = ?", (search_keywords, row_id))
        
    conn.commit()
    conn.close()
    print("Database update complete.")

if __name__ == '__main__':
    main()
