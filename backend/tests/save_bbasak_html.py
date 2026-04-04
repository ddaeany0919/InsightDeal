from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import time

def create_driver():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
    return webdriver.Chrome(options=chrome_options)

def save_source():
    driver = create_driver()
    try:
        url = "https://bbasak.com"
        print(f"Accessing {url}...")
        driver.get(url)
        time.sleep(5) # Wait for JS
        
        with open("bbasak_source.html", "w", encoding="utf-8") as f:
            f.write(driver.page_source)
        print("Saved bbasak_source.html")
        print(f"Title: {driver.title}")
        
    except Exception as e:
        print(f"Error: {e}")
    finally:
        driver.quit()

if __name__ == "__main__":
    save_source()
