from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from bs4 import BeautifulSoup
import time

def create_driver():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
    
    driver = webdriver.Chrome(options=chrome_options)
    return driver

def test_url(driver, url, name):
    try:
        print(f"Testing {name}: {url}")
        driver.get(url)
        time.sleep(3)
        print(f"  -> Title: {driver.title}")
        print(f"  -> Current URL: {driver.current_url}")
        return driver.page_source
    except Exception as e:
        print(f"  -> Error: {e}")
        return None

def main():
    driver = create_driver()
    try:
        # 1. Test Main Page to find links
        page_source = test_url(driver, "https://bbasak.com", "Main Page")
        
        if page_source:
            soup = BeautifulSoup(page_source, 'html.parser')
            print("\nSearching for links on Main Page:")
            for a in soup.find_all('a', href=True):
                text = a.get_text(strip=True)
                if "국내" in text or "해외" in text or "핫딜" in text:
                    print(f"  Found potential link: [{text}] -> {a['href']}")

        # 2. Test Suspected URLs
        print("\nVerifying suspected URLs:")
        test_url(driver, "https://bbs.bbasak.com/domestic", "Suspected Domestic")
        test_url(driver, "https://bbs.bbasak.com/overseas", "Suspected Overseas")

    finally:
        driver.quit()

if __name__ == "__main__":
    main()
