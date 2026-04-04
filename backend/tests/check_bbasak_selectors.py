from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from bs4 import BeautifulSoup
import time

def check_selectors():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    driver = webdriver.Chrome(options=chrome_options)
    
    try:
        url = "https://bbasak.com/bbs/board.php?bo_table=bbasak1"
        print(f"Checking {url}...")
        driver.get(url)
        time.sleep(3)
        
        soup = BeautifulSoup(driver.page_source, 'html.parser')

        # Save source for inspection
        with open("bbasak_domestic_source.html", "w", encoding="utf-8") as f:
            f.write(soup.prettify())
        print("Saved bbasak_domestic_source.html")
        
        # Check for list container
        board_list = soup.select('div.board-list')
        print(f"div.board-list found: {len(board_list)}")
        
        # Check for items
        items = soup.select('div.board-item')
        print(f"div.board-item found: {len(items)}")
        
        if items:
            first_item = items[0]
            # Check for title
            title = first_item.select_one('a.title-link')
            print(f"Title link found: {title is not None}")
            if title:
                print(f"Sample Title: {title.get_text(strip=True)}")
                
        # If selectors are wrong, print generic structure
        if not items:
            print("Selectors failed. Printing probable list containers:")
            # Basic board structure usually has list or table
            print("Table tags:", len(soup.find_all('table')))
            print("ul tags:", len(soup.find_all('ul')))
            print("li tags:", len(soup.find_all('li')))
            
            # Print first 500 chars of body to see if blocked
            print("Body start:", soup.body.get_text(strip=True)[:200] if soup.body else "No body")
            
    finally:
        driver.quit()

if __name__ == "__main__":
    check_selectors()
