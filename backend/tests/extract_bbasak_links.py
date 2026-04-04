from bs4 import BeautifulSoup

def extract_links():
    try:
        with open('bbasak_source.html', 'r', encoding='utf-8') as f:
            html_content = f.read()
            
        soup = BeautifulSoup(html_content, 'html.parser')
        
        print("Searching for '국내' or '해외' or '핫딜' links...")
        
        # Strategies to find the links:
        # 1. Exact text match
        # 2. Key phrases in text
        # 3. Href contains keywords
        
        print("Printing first 100 links to analyze structure:")
        count = 0
        for a in soup.find_all('a', href=True):
            text = a.get_text(strip=True)
            href = a['href']
            if len(text) > 0:
                print(f"[{text}] -> {href}")
                count += 1
                if count > 100:
                    break
            
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    extract_links()
